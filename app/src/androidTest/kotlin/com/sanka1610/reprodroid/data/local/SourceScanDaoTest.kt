package com.sanka1610.reprodroid.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceScanDaoTest {
    private lateinit var database: ReproDroidDatabase
    private lateinit var dao: JobDao

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ReproDroidDatabase::class.java,
        ).build()
        dao = database.jobDao()
        dao.upsertJob(job())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacementIsAtomicAndFailedReplacementPreservesPreviousEvidence() = runBlocking {
        dao.replaceSourceScan(
            scan(retrievedAt = "2026-08-29T00:02:00Z"),
            listOf(SourceScanDetectorCountEntity(JOB_ID, "PROCESS_EXEC_API", 1)),
            listOf(SourceScanFindingEntity(JOB_ID, 0, "PROCESS_EXEC_API", "build.gradle.kts", 1, 1)),
        )

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                dao.replaceSourceScan(
                    scan(retrievedAt = "2026-08-29T00:03:00Z"),
                    listOf(SourceScanDetectorCountEntity("missing-job", "PROCESS_EXEC_API", 1)),
                    emptyList(),
                )
            }
        }

        val stored = requireNotNull(dao.getSourceScan(JOB_ID))
        assertEquals("2026-08-29T00:02:00Z", stored.scan.retrievedAt)
        assertEquals(listOf("build.gradle.kts"), stored.findings.map { it.displayPath })
    }

    private fun job() = JobEntity(
        jobId = JOB_ID,
        executionMode = "REAL_TRUSTED",
        repositoryUrl = "https://github.com/example/app",
        revisionType = "TAG",
        revisionValue = "1.0",
        simulationOutcome = null,
        resolvedCommitSha = COMMIT,
        state = "AWAITING_SCAN_REVIEW",
        progressPercent = 25,
        latestLogSequence = 1,
        errorCode = null,
        errorMessage = null,
        createdAt = "2026-08-29T00:00:00Z",
        updatedAt = "2026-08-29T00:01:00Z",
    )

    private fun scan(retrievedAt: String) = SourceScanEntity(
        jobId = JOB_ID,
        schemaVersion = 1,
        resolvedCommitSha = COMMIT,
        scannerVersion = "reprodroid-static-v1",
        resultSha256 = "a".repeat(64),
        scannedFiles = 1,
        scannedBytes = 10,
        skippedBinaryFiles = 0,
        skippedSymlinks = 0,
        findingCount = 1,
        requiresReview = true,
        reviewed = false,
        retrievedAt = retrievedAt,
    )

    private companion object {
        const val JOB_ID = "job-a"
        const val COMMIT = "0123456789012345678901234567890123456789"
    }
}
