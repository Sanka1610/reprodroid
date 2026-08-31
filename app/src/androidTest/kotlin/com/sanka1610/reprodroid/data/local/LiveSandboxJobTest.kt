package com.sanka1610.reprodroid.data.local

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.network.decodeSandboxEvidence
import com.sanka1610.reprodroid.data.network.BuildSandboxMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit instrumentation argument opts in; ordinary regression runs do not access a live Runner. */
@RunWith(AndroidJUnit4::class)
class LiveSandboxJobTest {
    @Test fun importsLiveJobAndManifestThroughProductionRepository() = runBlocking(Dispatchers.IO) {
        val arguments = InstrumentationRegistry.getArguments()
        val jobId = arguments.getString("sandboxJobId")
        assumeTrue("No live sandbox Job requested", jobId != null)
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReproDroidApplication
        val repository = application.jobRepository
        repository.syncJob(requireNotNull(jobId))
        val expectedMode = BuildSandboxMode.valueOf(arguments.getString("sandboxExpectedMode") ?: "DOCKER")
        val expectedOrigin = arguments.getString("sandboxExpectedOrigin") ?: "NEW_JOB"
        var job = requireNotNull(repository.getJob(jobId))
        assertEquals(expectedMode.name, job.sandboxMode)
        assertEquals(expectedOrigin, job.sandboxOrigin)
        assertEquals(if (expectedMode == BuildSandboxMode.DOCKER) "docker-microg-v1" else null, job.sandboxProfileId)
        assertTrue(job.sandboxResponseSeen)
        assertFalse(repository.sandboxWarnings.value.containsKey(jobId))
        if (arguments.getString("sandboxAction") == "cancel") {
            assertEquals(BuildSandboxMode.DOCKER, expectedMode)
            assertEquals("BUILDING", job.state)
            assertEquals("PENDING", job.sandboxCleanupStatus)
            repository.cancelJob(jobId)
            job = requireNotNull(repository.getJob(jobId))
            assertEquals("CANCELLED", job.state)
            val initialCleanup = job.sandboxCleanupStatus
            assertTrue(initialCleanup in setOf("PENDING", "COMPLETE"))
            withTimeout(75_000) {
                while (job.sandboxCleanupStatus != "COMPLETE") {
                    delay(200)
                    repository.syncJob(jobId)
                    job = requireNotNull(repository.getJob(jobId))
                    assertEquals("CANCELLED", job.state)
                }
            }
            assertTrue(repository.getArtifacts(jobId).isEmpty())
            assertNull(repository.getBuildEnvironmentManifest(jobId))
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("sandboxCancelEvidence", "job=$jobId state=${job.state} afterRequest=$initialCleanup finalCleanup=${job.sandboxCleanupStatus} artifacts=0")
            })
        }
        if (job.state == "SUCCEEDED") {
            assertEquals(if (expectedMode == BuildSandboxMode.DOCKER) "COMPLETE" else null, job.sandboxCleanupStatus)
            val manifest = requireNotNull(repository.getBuildEnvironmentManifest(jobId)).manifest
            assertEquals(3, manifest.schemaVersion)
            assertEquals(expectedMode, decodeSandboxEvidence(requireNotNull(manifest.sandboxJson)).mode)
            assertFalse(repository.buildManifestWarnings.value.containsKey(jobId))
            if (arguments.getString("sandboxDownload") == "true") {
                val artifact = repository.getArtifacts(jobId).single()
                repository.downloadArtifactForComparison(jobId, artifact.artifactId)
                assertEquals("VERIFIED", repository.getArtifacts(jobId).single().downloadStatus)
            }
        }
    }
}
