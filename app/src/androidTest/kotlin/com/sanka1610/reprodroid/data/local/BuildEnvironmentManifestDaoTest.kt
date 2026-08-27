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
class BuildEnvironmentManifestDaoTest {
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
        dao.replaceBuildEnvironmentManifest(
            manifest(retrievedAt = "2026-08-27T00:02:00Z"),
            listOf(dependency(jobId = JOB_ID, fileName = "old.jar", sha256 = "b".repeat(64))),
        )

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                dao.replaceBuildEnvironmentManifest(
                    manifest(retrievedAt = "2026-08-27T00:03:00Z"),
                    listOf(
                        dependency(
                            jobId = "missing-job",
                            fileName = "new.jar",
                            sha256 = "c".repeat(64),
                        ),
                    ),
                )
            }
        }

        val stored = requireNotNull(dao.getBuildEnvironmentManifest(JOB_ID))
        assertEquals("2026-08-27T00:02:00Z", stored.manifest.retrievedAt)
        assertEquals(listOf("old.jar"), stored.dependencies.map { it.fileName })
    }

    private fun job() = JobEntity(
        jobId = JOB_ID,
        executionMode = "REAL_TRUSTED",
        repositoryUrl = "https://github.com/example/app",
        revisionType = "TAG",
        revisionValue = "1.0",
        simulationOutcome = null,
        resolvedCommitSha = COMMIT,
        state = "SUCCEEDED",
        progressPercent = 100,
        latestLogSequence = 1,
        errorCode = null,
        errorMessage = null,
        createdAt = "2026-08-27T00:00:00Z",
        updatedAt = "2026-08-27T00:01:00Z",
    )

    private fun manifest(retrievedAt: String) = BuildEnvironmentManifestEntity(
        jobId = JOB_ID,
        schemaVersion = 1,
        commitSha = COMMIT,
        javaVersion = "18.0.2.1+1",
        javaVendor = "Eclipse Adoptium",
        gradleVersion = "8.14.3",
        androidSdkApiLevel = 36,
        buildToolsVersion = "36.0.0",
        apkSha256 = "a".repeat(64),
        retrievedAt = retrievedAt,
    )

    private fun dependency(jobId: String, fileName: String, sha256: String) =
        BuildEnvironmentDependencyEntity(
            jobId = jobId,
            ordinal = 0,
            fileName = fileName,
            sha256 = sha256,
        )

    private companion object {
        const val JOB_ID = "job-a"
        const val COMMIT = "0123456789012345678901234567890123456789"
    }
}
