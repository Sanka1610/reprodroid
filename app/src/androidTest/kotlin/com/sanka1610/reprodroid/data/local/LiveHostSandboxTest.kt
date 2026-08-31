package com.sanka1610.reprodroid.data.local

import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.network.BuildSandboxMode
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.SandboxEvidence
import com.sanka1610.reprodroid.data.network.decodeSandboxEvidence
import com.sanka1610.reprodroid.data.repository.JobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real API -> production Repository -> durable Room; separate DBs preserve the product DOCKER A/B run. */
@RunWith(AndroidJUnit4::class)
class LiveHostSandboxTest {
    @Test fun importsExplicitCompletedHostJobAndRetainsEvidenceAfterDatabaseReopen() = runBlocking(Dispatchers.IO) {
        val arguments = InstrumentationRegistry.getArguments()
        val jobId = arguments.getString("hostJobId")
        assumeTrue("No live HOST acceptance requested", jobId != null)
        requireNotNull(jobId)
        require(UUID.fromString(jobId).toString() == jobId)
        val port = requireNotNull(arguments.getString("hostRunnerPort")).toInt()
        require(port in setOf(18084, 18085))
        val origin = requireNotNull(arguments.getString("hostExpectedOrigin"))
        require(origin in setOf("NEW_JOB", "LEGACY_HOST"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase3e-host-$jobId.sqlite3"
        var database = Room.databaseBuilder(context, ReproDroidDatabase::class.java, name).build()
        val client = RunnerApiClient("http://127.0.0.1:$port")
        try {
            val repository = JobRepository(context, database, client)
            repository.syncJob(jobId)
            val job = requireNotNull(repository.getJob(jobId))
            assertEquals("SUCCEEDED", job.state)
            assertEquals("d8df10ab687a1c1ca05221634cfa46bad262023a", job.resolvedCommitSha)
            assertEquals("HOST", job.sandboxMode)
            assertEquals(origin, job.sandboxOrigin)
            assertNull(job.sandboxProfileId)
            assertNull(job.sandboxCleanupStatus)
            assertTrue(job.sandboxResponseSeen)
            assertFalse(repository.sandboxWarnings.value.containsKey(jobId))
            assertFalse(repository.buildManifestWarnings.value.containsKey(jobId))
            val manifest = requireNotNull(repository.getBuildEnvironmentManifest(jobId))
            assertEquals(3, manifest.manifest.schemaVersion)
            assertEquals(SandboxEvidence(BuildSandboxMode.HOST), decodeSandboxEvidence(requireNotNull(manifest.manifest.sandboxJson)))
            assertEquals(1031, manifest.dependencies.size)
            val artifact = repository.getArtifacts(jobId).single()
            repository.downloadArtifactForComparison(jobId, artifact.artifactId)
            val verifiedArtifact = repository.getArtifacts(jobId).single()
            assertEquals("VERIFIED", verifiedArtifact.downloadStatus)
            assertTrue(database.jobDao().getPendingInstallAttempts().isEmpty())
            database.close()
            database = Room.databaseBuilder(context, ReproDroidDatabase::class.java, name).build()
            // No API refresh after reopen: validate what actually survived on disk.
            val reopened = JobRepository(context, database, client)
            assertEquals(job, reopened.getJob(jobId))
            assertEquals(manifest, reopened.getBuildEnvironmentManifest(jobId))
            assertEquals(verifiedArtifact, reopened.getArtifacts(jobId).single())
            assertEquals(SandboxEvidence(BuildSandboxMode.HOST), decodeSandboxEvidence(
                requireNotNull(reopened.getBuildEnvironmentManifest(jobId)?.manifest?.sandboxJson)))
            database.openHelper.readableDatabase.query("PRAGMA user_version").use {
                assertTrue(it.moveToFirst())
                assertEquals(14, it.getInt(0))
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("hostEvidence", "job=$jobId origin=$origin mode=HOST public=3 dependencies=1031 " +
                    "artifact=${verifiedArtifact.sha256} download=VERIFIED room=14 reopened=true db=$name")
            })
        } finally { database.close() }
    }
}
