package com.sanka1610.reprodroid.data.local

import android.util.Log
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.network.BuildSandboxMode
import com.sanka1610.reprodroid.data.network.decodeSandboxEvidence
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One explicitly selected product-workflow step; never automatically acknowledges A or B. */
@RunWith(AndroidJUnit4::class)
class LiveSandboxComparisonTest {
    @Test fun runsExplicitComparisonStepThroughProductionRepositories() = runBlocking(Dispatchers.IO) {
        val arguments = InstrumentationRegistry.getArguments()
        val action = arguments.getString("sandboxComparisonAction")
        assumeTrue("No live comparison step requested", action != null)
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReproDroidApplication
        val repository = application.managedAppRepository
        val jobs = application.jobRepository
        val expectedCommit = "d8df10ab687a1c1ca05221634cfa46bad262023a"
        val appId = if (action == "start") {
            assertTrue(repository.observeApps().first().none {
                it.app.canonicalRepositoryUrl == "https://github.com/morpheapp/microg-re"
            })
            val preview = repository.previewLatest("https://github.com/MorpheApp/MicroG-RE")
            val githubPreview = checkNotNull(preview as? ResolvedGitHubRelease) {
                "The GitHub live fixture must resolve through the GitHub provider adapter."
            }
            assertEquals("6.1.4", githubPreview.release.tagName)
            assertEquals(expectedCommit, githubPreview.resolvedCommitSha)
            repository.registerAndDownload(githubPreview, ManagementMode.VERIFICATION, InstallationSource.OFFICIAL_RELEASE, false)
                .also { repository.startComparison(it) }
        } else requireNotNull(arguments.getString("sandboxAppId"))
        var record = requireNotNull(repository.observeApp(appId).first())
        var run = requireNotNull(record.currentComparison)
        assertEquals(expectedCommit, run.expectedCommitSha)
        assertEquals(2, run.protocolVersion)
        if (action != "start") {
            repository.refreshComparison(run.comparisonRunId)
            record = requireNotNull(repository.observeApp(appId).first())
            run = requireNotNull(record.currentComparison)
        }
        if (action == "confirm" || action == "review") {
            val currentJobId = run.repeatRunnerJobId ?: run.runnerJobId
            assertEquals(arguments.getString("sandboxExpectedJobId"), currentJobId)
            val job = requireNotNull(jobs.getJob(currentJobId))
            assertEquals(expectedCommit, job.resolvedCommitSha)
            assertEquals("DOCKER", job.sandboxMode)
            assertFalse(jobs.sandboxWarnings.value.containsKey(currentJobId))
            if (action == "confirm") {
                assertEquals("AWAITING_CONFIRMATION", job.state)
                repository.confirmComparison(run.comparisonRunId)
            } else {
                assertEquals("AWAITING_SCAN_REVIEW", job.state)
                val scan = requireNotNull(jobs.getSourceScan(currentJobId))
                assertEquals(requireNotNull(arguments.getString("sandboxReviewedDigest")), scan.scan.resultSha256)
                assertEquals(121, scan.findings.size)
                repository.continueComparisonSourceScan(run.comparisonRunId)
            }
        } else require(action in setOf("start", "refresh", "verify"))
        record = requireNotNull(repository.observeApp(appId).first())
        run = requireNotNull(record.currentComparison)
        assertEquals("VERIFIED", record.latestRelease?.selectedAsset?.downloadStatus)
        if (action == "verify") {
            assertEquals("COMPLETED", run.status)
            assertEquals("MATCH", run.outcome)
            assertEquals("MATCH", run.repeatOfficialOutcome)
            assertEquals("MATCH", run.repeatabilityOutcome)
            assertNotEquals(run.runnerJobId, run.repeatRunnerJobId)
            for (jobId in listOf(run.runnerJobId, requireNotNull(run.repeatRunnerJobId))) {
                val job = requireNotNull(jobs.getJob(jobId))
                assertEquals("SUCCEEDED", job.state)
                assertEquals("DOCKER", job.sandboxMode)
                assertEquals("COMPLETE", job.sandboxCleanupStatus)
                val manifest = requireNotNull(jobs.getBuildEnvironmentManifest(jobId)).manifest
                assertEquals(3, manifest.schemaVersion)
                assertEquals(BuildSandboxMode.DOCKER, decodeSandboxEvidence(requireNotNull(manifest.sandboxJson)).mode)
                assertEquals("VERIFIED", jobs.getArtifacts(jobId).single().downloadStatus)
            }
            assertTrue(record.releaseInstallAttempts.isEmpty())
            assertEquals(TrustLevel.REPRODUCIBLE, record.trustLevel)
            SQLiteDatabase.openDatabase(application.getDatabasePath("reprodroid.sqlite3").path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("PRAGMA user_version", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(14, cursor.getInt(0))
                }
                database.rawQuery("SELECT count(*) FROM comparison_entries WHERE comparisonRunId=? AND result='MATCH'", arrayOf(run.comparisonRunId)).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(6, cursor.getInt(0))
                }
            }
        }
        val evidence = "app=$appId run=${run.comparisonRunId} A=${run.runnerJobId} B=${run.repeatRunnerJobId} status=${run.status} raw=${run.outcome}/${run.repeatOfficialOutcome}/${run.repeatabilityOutcome} officialSha256=${record.latestRelease?.selectedAsset?.computedRawSha256} trust=${record.trustLevel}"
        Log.i("SandboxComparison", evidence)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("sandboxEvidence", evidence) })
        Unit
    }
}
