package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.SourceScanDetectorCountEntity
import com.sanka1610.reprodroid.data.local.SourceScanEntity
import com.sanka1610.reprodroid.data.local.SourceScanFindingEntity
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.SourceScanDetailResponse
import com.sanka1610.reprodroid.data.network.SourceScanDetectorId
import com.sanka1610.reprodroid.data.network.SourceScanFinding
import com.sanka1610.reprodroid.data.network.SourceScanStatus
import com.sanka1610.reprodroid.data.network.SourceScanSummaryResponse
import java.nio.charset.StandardCharsets

data class SourceScanWarning(
    val code: String,
    val message: String,
)

internal data class ValidatedSourceScan(
    val scan: SourceScanEntity,
    val detectorCounts: List<SourceScanDetectorCountEntity>,
    val findings: List<SourceScanFindingEntity>,
)

internal fun validateSourceScanSummary(remoteJob: JobResponse): SourceScanSummaryResponse? {
    val sourceScan = remoteJob.sourceScan
    if (sourceScan == null) {
        check(remoteJob.state !in setOf(JobState.SCANNING_SOURCE, JobState.AWAITING_SCAN_REVIEW))
        return null
    }
    check(sourceScan.scannerVersion == SOURCE_SCANNER_VERSION)
    when (sourceScan.status) {
        SourceScanStatus.SCANNING -> {
            check(remoteJob.state == JobState.SCANNING_SOURCE)
            check(sourceScan.completedFieldsAreNull())
        }
        SourceScanStatus.FAILED -> {
            check(remoteJob.state == JobState.FAILED)
            check(remoteJob.error?.code?.startsWith("SOURCE_SCAN_") == true)
            check(sourceScan.completedFieldsAreNull())
        }
        SourceScanStatus.COMPLETED -> {
            val resultSha256 = requireNotNull(sourceScan.resultSha256)
            val scannedFiles = requireNotNull(sourceScan.scannedFiles)
            val scannedBytes = requireNotNull(sourceScan.scannedBytes)
            val findingCount = requireNotNull(sourceScan.findingCount)
            val requiresReview = requireNotNull(sourceScan.requiresReview)
            val reviewed = requireNotNull(sourceScan.reviewed)
            check(LOWERCASE_SHA256.matches(resultSha256))
            check(scannedFiles in 0..MAX_SCANNED_FILES)
            check(scannedBytes in 0..MAX_SCANNED_BYTES)
            check(findingCount in 0..MAX_FINDINGS)
            check(requiresReview == (findingCount > 0))
            check(!reviewed || requiresReview)
            if (requiresReview && !reviewed) {
                check(
                    remoteJob.state in setOf(
                        JobState.AWAITING_SCAN_REVIEW,
                        JobState.CANCELLED,
                        JobState.FAILED,
                    ),
                )
            } else if (remoteJob.state == JobState.AWAITING_SCAN_REVIEW) {
                error("A job awaiting source scan review must expose unreviewed findings.")
            }
        }
    }
    return sourceScan
}

internal fun validateSourceScanDetail(
    jobId: String,
    remoteJob: JobResponse,
    response: SourceScanDetailResponse,
    retrievedAt: String,
): ValidatedSourceScan {
    val sourceScan = requireNotNull(validateSourceScanSummary(remoteJob))
    check(sourceScan.status == SourceScanStatus.COMPLETED)
    check(response.schemaVersion == SOURCE_SCAN_SCHEMA_VERSION)
    check(remoteJob.jobId == jobId && response.jobId == jobId)
    check(LOWERCASE_COMMIT_SHA.matches(response.resolvedCommitSha))
    check(response.resolvedCommitSha == remoteJob.resolvedCommitSha)
    check(response.scannerVersion == SOURCE_SCANNER_VERSION && response.scannerVersion == sourceScan.scannerVersion)
    check(LOWERCASE_SHA256.matches(response.resultSha256) && response.resultSha256 == sourceScan.resultSha256)
    check(response.summary.scannedFiles == sourceScan.scannedFiles)
    check(response.summary.scannedBytes == sourceScan.scannedBytes)
    check(response.summary.findingCount == sourceScan.findingCount)
    check(response.summary.scannedFiles in 0..MAX_SCANNED_FILES)
    check(response.summary.scannedBytes in 0..MAX_SCANNED_BYTES)
    check(response.summary.skippedBinaryFiles in 0..MAX_SCANNED_FILES)
    check(response.summary.skippedSymlinks in 0..MAX_SCANNED_FILES)
    check(response.summary.findingCount in 0..MAX_FINDINGS)
    check(response.findings.size == response.summary.findingCount)

    val detectorCountOrder = response.detectorCounts.map { it.detectorId.name }
    check(detectorCountOrder == detectorCountOrder.sorted() && detectorCountOrder.distinct().size == detectorCountOrder.size)
    val actualCounts = response.findings.groupingBy(SourceScanFinding::detectorId).eachCount()
    check(response.detectorCounts.all { it.count > 0 && actualCounts[it.detectorId] == it.count })
    check(response.detectorCounts.sumOf { it.count } == response.findings.size)
    check(response.detectorCounts.map { it.detectorId }.toSet() == actualCounts.keys)

    val expectedFindings = response.findings.sortedWith(SOURCE_SCAN_FINDING_ORDER)
    check(response.findings == expectedFindings)
    val findings = response.findings.mapIndexed { ordinal, finding ->
        validateFinding(finding)
        SourceScanFindingEntity(
            jobId = jobId,
            ordinal = ordinal,
            detectorId = finding.detectorId.name,
            displayPath = finding.displayPath,
            line = finding.line,
            column = finding.column,
        )
    }
    return ValidatedSourceScan(
        scan = SourceScanEntity(
            jobId = jobId,
            schemaVersion = response.schemaVersion,
            resolvedCommitSha = response.resolvedCommitSha,
            scannerVersion = response.scannerVersion,
            resultSha256 = response.resultSha256,
            scannedFiles = response.summary.scannedFiles,
            scannedBytes = response.summary.scannedBytes,
            skippedBinaryFiles = response.summary.skippedBinaryFiles,
            skippedSymlinks = response.summary.skippedSymlinks,
            findingCount = response.summary.findingCount,
            requiresReview = requireNotNull(sourceScan.requiresReview),
            reviewed = requireNotNull(sourceScan.reviewed),
            retrievedAt = retrievedAt,
        ),
        detectorCounts = response.detectorCounts.map { count ->
            SourceScanDetectorCountEntity(jobId, count.detectorId.name, count.count)
        },
        findings = findings,
    )
}

private fun SourceScanSummaryResponse.completedFieldsAreNull(): Boolean =
    resultSha256 == null && scannedFiles == null && scannedBytes == null && findingCount == null &&
        requiresReview == null && reviewed == null

private fun validateFinding(finding: SourceScanFinding) {
    check(isSafeDisplayPath(finding.displayPath))
    check((finding.line == null) == (finding.column == null))
    finding.line?.let { check(it > 0) }
    finding.column?.let { check(it > 0) }
    when (finding.detectorId) {
        SourceScanDetectorId.CANONICAL_PATH_COLLISION,
        SourceScanDetectorId.TEXT_ENCODING_UNSUPPORTED,
        -> check(finding.line == null)
        SourceScanDetectorId.UNICODE_NON_NFC -> Unit
        SourceScanDetectorId.UNICODE_BIDI_CONTROL,
        SourceScanDetectorId.UNICODE_INVISIBLE_FORMAT,
        SourceScanDetectorId.PROCESS_EXEC_API,
        SourceScanDetectorId.DYNAMIC_NATIVE_LOAD_API,
        SourceScanDetectorId.NETWORK_DOWNLOAD_COMMAND,
        -> check(finding.line != null)
    }
}

private fun isSafeDisplayPath(path: String): Boolean {
    if (path.isBlank() || path.toByteArray(StandardCharsets.UTF_8).size > MAX_DISPLAY_PATH_BYTES) return false
    if (path.startsWith('/') || path.contains('\\') || hasUnsafeDisplayCodePoint(path)) return false
    if (path == REDACTED_PATH) return true
    val segments = path.split('/')
    return segments.none { it.isEmpty() || it == "." || it == ".." }
}

private fun hasUnsafeDisplayCodePoint(path: String): Boolean {
    var offset = 0
    while (offset < path.length) {
        val first = path[offset]
        if (Character.isLowSurrogate(first)) return true
        if (Character.isHighSurrogate(first) &&
            (offset + 1 >= path.length || !Character.isLowSurrogate(path[offset + 1]))
        ) {
            return true
        }
        val codePoint = path.codePointAt(offset)
        if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt()) {
            return true
        }
        offset += Character.charCount(codePoint)
    }
    return false
}

private const val SOURCE_SCANNER_VERSION = "reprodroid-static-v1"
private const val SOURCE_SCAN_SCHEMA_VERSION = 1
private const val MAX_SCANNED_FILES = 50_000
private const val MAX_SCANNED_BYTES = 256L * 1024L * 1024L
private const val MAX_FINDINGS = 5_000
private const val MAX_DISPLAY_PATH_BYTES = 1_024
private const val REDACTED_PATH = "<redacted-path>"
private val LOWERCASE_COMMIT_SHA = Regex("[0-9a-f]{40}")
private val LOWERCASE_SHA256 = Regex("[0-9a-f]{64}")
private val SOURCE_SCAN_FINDING_ORDER = compareBy<SourceScanFinding>(
    { it.detectorId.name },
    SourceScanFinding::displayPath,
    { it.line != null },
    { it.line ?: 0 },
    { it.column ?: 0 },
)
