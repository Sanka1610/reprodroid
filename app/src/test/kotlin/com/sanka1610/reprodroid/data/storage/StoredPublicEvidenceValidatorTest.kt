package com.sanka1610.reprodroid.data.storage

import com.sanka1610.reprodroid.data.local.SourceScanDetectorCountEntity
import com.sanka1610.reprodroid.data.local.SourceScanEntity
import com.sanka1610.reprodroid.data.local.SourceScanFindingEntity
import com.sanka1610.reprodroid.data.local.SourceScanWithDetails
import org.junit.Assert.assertThrows
import org.junit.Test

class StoredPublicEvidenceValidatorTest {
    @Test
    fun `valid stored scan is accepted while unknown schema and inconsistent counts fail closed`() {
        val valid = fixture()
        StoredPublicEvidenceValidator.requireValid(valid)

        assertThrows(IllegalStateException::class.java) {
            StoredPublicEvidenceValidator.requireValid(valid.copy(scan = valid.scan.copy(schemaVersion = 2)))
        }
        assertThrows(IllegalStateException::class.java) {
            StoredPublicEvidenceValidator.requireValid(
                valid.copy(detectorCounts = valid.detectorCounts.map { it.copy(count = 2) }),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            StoredPublicEvidenceValidator.requireValid(
                valid.copy(findings = valid.findings.map { it.copy(displayPath = "../private/path") }),
            )
        }
    }

    private fun fixture(): SourceScanWithDetails {
        val jobId = "job"
        return SourceScanWithDetails(
            scan = SourceScanEntity(
                jobId = jobId,
                schemaVersion = 1,
                resolvedCommitSha = "1".repeat(40),
                scannerVersion = "reprodroid-static-v1",
                resultSha256 = "a".repeat(64),
                scannedFiles = 1,
                scannedBytes = 10,
                skippedBinaryFiles = 0,
                skippedSymlinks = 0,
                findingCount = 1,
                requiresReview = true,
                reviewed = false,
                retrievedAt = "2026-09-04T00:00:00Z",
            ),
            detectorCounts = listOf(SourceScanDetectorCountEntity(jobId, "PROCESS_EXEC_API", 1)),
            findings = listOf(SourceScanFindingEntity(jobId, 0, "PROCESS_EXEC_API", "build.gradle.kts", 1, 1)),
        )
    }
}
