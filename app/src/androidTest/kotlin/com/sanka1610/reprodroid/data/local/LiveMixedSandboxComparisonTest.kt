package com.sanka1610.reprodroid.data.local

import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.artifact.ApkContentComparator
import com.sanka1610.reprodroid.data.artifact.ExpectedApkFile
import com.sanka1610.reprodroid.data.repository.sandboxSelectionText
import com.sanka1610.reprodroid.data.repository.storedSandboxManifestValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only reuse of separately verified real APKs; does not start/rewrite a product comparison run. */
@RunWith(AndroidJUnit4::class)
class LiveMixedSandboxComparisonTest {
    @Test fun comparesRealHostDockerAndOfficialArtifactsWithoutChangingProductTrust() = runBlocking(Dispatchers.IO) {
        assumeTrue("No mixed-mode artifact acceptance requested",
            InstrumentationRegistry.getArguments().getString("sandboxMixedArtifacts") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReproDroidApplication
        val appId = "77ba9fd3-1adb-4efc-a00a-b8a76a8f088c"
        val before = requireNotNull(application.managedAppRepository.observeApp(appId).first())
        val official = requireNotNull(before.latestRelease?.selectedAsset)
        val dockerId = "555fcc99-3a49-4d30-9149-1983c37b7362"
        val hostId = "e6429113-4c52-4223-b355-fd58e4621634"
        val hostDatabase = "phase3e-host-$hostId.sqlite3"
        assertTrue(application.getDatabasePath(hostDatabase).isFile)
        val database = Room.databaseBuilder(application, ReproDroidDatabase::class.java, hostDatabase).build()
        try {
            val hostJob = requireNotNull(database.jobDao().getJob(hostId))
            val dockerJob = requireNotNull(application.jobRepository.getJob(dockerId))
            val hostManifest = requireNotNull(database.jobDao().getBuildEnvironmentManifest(hostId))
            val dockerManifest = requireNotNull(application.jobRepository.getBuildEnvironmentManifest(dockerId))
            assertTrue(storedSandboxManifestValid(hostJob, hostManifest.manifest))
            assertTrue(storedSandboxManifestValid(dockerJob, dockerManifest.manifest))
            assertEquals("HOST — no build container", sandboxSelectionText(hostJob))
            assertEquals("DOCKER / docker-microg-v1 — cleanup COMPLETE", sandboxSelectionText(dockerJob))
            assertEquals(hostJob.resolvedCommitSha, dockerJob.resolvedCommitSha)
            val host = database.jobDao().getArtifacts(hostId).single()
            val docker = application.jobRepository.getArtifacts(dockerId).single()
            for (artifact in listOf(host, docker)) {
                assertEquals("VERIFIED", artifact.downloadStatus)
                assertEquals(official.packageName, artifact.packageName)
                assertEquals(official.versionName, artifact.versionName)
                assertEquals(official.versionCode, artifact.versionCode)
                assertEquals(artifact.sizeBytes, artifact.downloadedSizeBytes)
                assertEquals(artifact.sha256, artifact.downloadedSha256)
            }
            assertEquals("VERIFIED", official.downloadStatus)
            val comparator = ApkContentComparator()
            val officialFile = File(requireNotNull(official.localContentPath))
            val hostFile = File(application.filesDir, requireNotNull(host.localContentPath))
            val dockerFile = File(application.filesDir, requireNotNull(docker.localContentPath))
            val officialExpected = ExpectedApkFile(requireNotNull(official.downloadedSizeBytes), requireNotNull(official.computedRawSha256))
            val hostExpected = ExpectedApkFile(host.sizeBytes, host.sha256)
            val dockerExpected = ExpectedApkFile(docker.sizeBytes, docker.sha256)
            val referenceRoot = File(application.filesDir, "reference-apks")
            val rebuiltRoot = File(application.filesDir, "apks")
            val results = listOf(
                comparator.compare(officialFile, referenceRoot, officialExpected, hostFile, rebuiltRoot, hostExpected),
                comparator.compare(officialFile, referenceRoot, officialExpected, dockerFile, rebuiltRoot, dockerExpected),
                comparator.compare(hostFile, rebuiltRoot, hostExpected, dockerFile, rebuiltRoot, dockerExpected),
            )
            results.forEach { assertTrue(it.isMatch); assertEquals(6, it.entries.size) }
            val after = requireNotNull(application.managedAppRepository.observeApp(appId).first())
            assertEquals(before.currentComparison, after.currentComparison)
            assertEquals(before.trustLevel, after.trustLevel)
            assertEquals(before.releaseInstallAttempts, after.releaseInstallAttempts)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("mixedSandboxEvidence", "HOST=$hostId DOCKER=$dockerId raw=MATCH/MATCH/MATCH " +
                    "entriesPerAxis=6 identity=matched productRunUnchanged=true installUnchanged=true")
            })
        } finally { database.close() }
        Unit
    }
}
