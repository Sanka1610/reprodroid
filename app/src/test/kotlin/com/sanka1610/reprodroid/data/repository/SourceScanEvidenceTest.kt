package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.RequestedRevision
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.SourceScanDetailResponse
import com.sanka1610.reprodroid.data.network.SourceScanDetectorCount
import com.sanka1610.reprodroid.data.network.SourceScanDetectorId
import com.sanka1610.reprodroid.data.network.SourceScanFinding
import com.sanka1610.reprodroid.data.network.SourceScanStatistics
import com.sanka1610.reprodroid.data.network.SourceScanStatus
import com.sanka1610.reprodroid.data.network.SourceScanSummaryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceScanEvidenceTest {
    @Test
    fun `completed scan becomes deterministic Room rows`() {
        val validated = validateSourceScanDetail(
            JOB_ID,
            remoteJob(),
            detail(),
            "2026-08-29T00:00:00Z",
        )

        assertEquals(RESULT_DIGEST, validated.scan.resultSha256)
        assertEquals(listOf(0), validated.findings.map { it.ordinal })
        assertEquals(SourceScanDetectorId.PROCESS_EXEC_API.name, validated.detectorCounts.single().detectorId)
    }

    @Test
    fun `awaiting review requires completed finding evidence bound to digest`() {
        val accepted = validateSourceScanSummary(remoteJob())
        assertEquals(RESULT_DIGEST, accepted?.resultSha256)

        assertThrows(IllegalStateException::class.java) {
            validateSourceScanSummary(remoteJob().copy(sourceScan = summary().copy(reviewed = true)))
        }
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanSummary(remoteJob().copy(sourceScan = summary().copy(resultSha256 = "A".repeat(64))))
        }
    }

    @Test
    fun `cancelled or failed review job may retain unreviewed evidence`() {
        validateSourceScanSummary(remoteJob().copy(state = JobState.CANCELLED))
        validateSourceScanSummary(remoteJob().copy(state = JobState.FAILED))

        assertThrows(IllegalStateException::class.java) {
            validateSourceScanSummary(remoteJob().copy(state = JobState.SUCCEEDED))
        }
    }

    @Test
    fun `minimal scanning and failed summaries reject completed fields`() {
        validateSourceScanSummary(
            remoteJob().copy(
                state = JobState.SCANNING_SOURCE,
                sourceScan = SourceScanSummaryResponse(SourceScanStatus.SCANNING, SCANNER_VERSION),
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanSummary(
                remoteJob().copy(
                    state = JobState.SCANNING_SOURCE,
                    sourceScan = SourceScanSummaryResponse(
                        SourceScanStatus.SCANNING,
                        SCANNER_VERSION,
                        resultSha256 = RESULT_DIGEST,
                    ),
                ),
            )
        }
    }

    @Test
    fun `detail rejects commit count ordering and unsafe paths`() {
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanDetail(JOB_ID, remoteJob(), detail().copy(resolvedCommitSha = "2".repeat(40)), NOW)
        }
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanDetail(
                JOB_ID,
                remoteJob(),
                detail().copy(summary = detail().summary.copy(findingCount = 2)),
                NOW,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanDetail(
                JOB_ID,
                remoteJob(),
                detail().copy(findings = listOf(detail().findings.single().copy(displayPath = "../secret.gradle"))),
                NOW,
            )
        }
    }

    @Test
    fun `detector position rules are fail closed`() {
        assertThrows(IllegalStateException::class.java) {
            validateSourceScanDetail(
                JOB_ID,
                remoteJob(),
                detail().copy(findings = listOf(detail().findings.single().copy(line = null, column = null))),
                NOW,
            )
        }
    }

    @Test
    fun `safe supplementary path is accepted but unpaired surrogate is rejected`() {
        val supplementary = detail().findings.single().copy(displayPath = "module/\ud83d\ude80.gradle.kts")
        validateSourceScanDetail(JOB_ID, remoteJob(), detail().copy(findings = listOf(supplementary)), NOW)

        assertThrows(IllegalStateException::class.java) {
            validateSourceScanDetail(
                JOB_ID,
                remoteJob(),
                detail().copy(findings = listOf(supplementary.copy(displayPath = "module/\ud83d.gradle.kts"))),
                NOW,
            )
        }
    }

    @Test
    fun `identical redacted findings retain separate ordinals`() {
        val finding = SourceScanFinding(SourceScanDetectorId.TEXT_ENCODING_UNSUPPORTED, "<redacted-path>")
        val response = detail().copy(
            summary = detail().summary.copy(findingCount = 2),
            detectorCounts = listOf(SourceScanDetectorCount(SourceScanDetectorId.TEXT_ENCODING_UNSUPPORTED, 2)),
            findings = listOf(finding, finding),
        )
        val remote = remoteJob().copy(
            sourceScan = summary().copy(findingCount = 2),
        )

        val validated = validateSourceScanDetail(JOB_ID, remote, response, NOW)

        assertEquals(listOf(0, 1), validated.findings.map { it.ordinal })
    }

    private fun remoteJob() = JobResponse(
        jobId = JOB_ID,
        executionMode = ExecutionMode.REAL_TRUSTED,
        repositoryUrl = "https://github.com/example/app",
        requestedRevision = RequestedRevision(RevisionType.TAG, "1.0"),
        resolvedCommitSha = COMMIT,
        state = JobState.AWAITING_SCAN_REVIEW,
        progressPercent = 25,
        requiresConfirmation = false,
        sourceScan = summary(),
        latestLogSequence = 1,
        artifacts = emptyList(),
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun summary() = SourceScanSummaryResponse(
        status = SourceScanStatus.COMPLETED,
        scannerVersion = SCANNER_VERSION,
        resultSha256 = RESULT_DIGEST,
        scannedFiles = 10,
        scannedBytes = 100,
        findingCount = 1,
        requiresReview = true,
        reviewed = false,
    )

    private fun detail() = SourceScanDetailResponse(
        schemaVersion = 1,
        jobId = JOB_ID,
        resolvedCommitSha = COMMIT,
        scannerVersion = SCANNER_VERSION,
        resultSha256 = RESULT_DIGEST,
        summary = SourceScanStatistics(10, 100, 2, 1, 1),
        detectorCounts = listOf(SourceScanDetectorCount(SourceScanDetectorId.PROCESS_EXEC_API, 1)),
        findings = listOf(SourceScanFinding(SourceScanDetectorId.PROCESS_EXEC_API, "build.gradle.kts", 2, 5)),
    )

    private companion object {
        const val JOB_ID = "job-a"
        const val COMMIT = "0123456789012345678901234567890123456789"
        const val RESULT_DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SCANNER_VERSION = "reprodroid-static-v1"
        const val NOW = "2026-08-29T00:00:00Z"
    }
}
