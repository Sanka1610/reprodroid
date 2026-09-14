package com.sanka1610.reprodroid.data.artifact

import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.MainActivity
import com.sanka1610.reprodroid.data.local.ExistingInstallStatus
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Opt-in device acceptance for the production-signed ReproDroid update gate.
 *
 * The candidate is copied from a fixed shell-owned staging path into the same
 * app-private reference directory used by normal verified release downloads.
 * The production installer then revalidates bytes, package, version, and signer
 * before committing a user-confirmed PackageInstaller session.
 */
@RunWith(AndroidJUnit4::class)
class SelfUpdateProductAcceptanceTest {
    @Test
    fun productionSignedAlpha05PassesNormalGateAndUpdatesInstalledAlpha04() =
        runBlocking(Dispatchers.IO) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val arguments = InstrumentationRegistry.getArguments()
            assumeTrue(
                "No production self-update acceptance requested",
                arguments.getString(RUN_ARGUMENT) == "true",
            )
            val context = instrumentation.targetContext
            assertTrue(
                "Unknown-app-source permission must be granted explicitly before this run",
                context.packageManager.canRequestPackageInstalls(),
            )

            val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
            val registeredAppId = UUID.randomUUID().toString()
            val snapshotId = UUID.randomUUID().toString()
            val assetId = UUID.randomUUID().toString()
            val candidate = File(context.filesDir, "reference-apks/$assetId.apk")
            try {
                candidate.parentFile?.mkdirs()
                instrumentation.uiAutomation.executeShellCommand("cat $SHELL_CANDIDATE_PATH").use { descriptor ->
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        candidate.outputStream().use(input::copyTo)
                    }
                }
                assertTrue("The staged candidate is empty", candidate.length() > 0L)

                val inspection = ApkInspector(context.packageManager).inspect(candidate)
                assertEquals(PRODUCTION_PACKAGE, inspection.packageName)
                assertEquals(EXPECTED_CANDIDATE_VERSION_CODE, inspection.versionCode)
                assertEquals(EXPECTED_INSTALLED_VERSION_CODE, inspection.installedVersionCode)
                assertEquals(ExistingInstallStatus.SIGNER_MATCH, inspection.existingInstallStatus)
                assertEquals(listOf(PRODUCTION_SIGNER_SHA256), inspection.currentSignerSha256)

                val now = Instant.now().toString()
                val dao = database.managedAppDao()
                dao.upsertRegisteredApp(
                    RegisteredAppEntity(
                        registeredAppId = registeredAppId,
                        displayName = "ReproDroid self-update acceptance",
                        repositoryUrl = "https://github.com/Sanka1610/reprodroid",
                        canonicalRepositoryUrl = "https://github.com/Sanka1610/reprodroid",
                        provider = "GITHUB",
                        managementMode = ManagementMode.ACQUISITION.name,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                dao.upsertReleaseSnapshot(
                    ReleaseSnapshotEntity(
                        releaseSnapshotId = snapshotId,
                        registeredAppId = registeredAppId,
                        providerReleaseId = "phase5-alpha05-local-acceptance",
                        tagName = "v0.1.0-alpha05",
                        resolvedCommitSha = arguments.getString("expectedSourceCommit").orEmpty(),
                        releaseName = "ReproDroid 0.1.0-alpha05",
                        releaseUrl = "https://github.com/Sanka1610/reprodroid/releases",
                        targetCommitishRaw = "develop",
                        isDraft = false,
                        isPrerelease = true,
                        isImmutable = true,
                        releaseCreatedAt = now,
                        publishedAt = null,
                        fetchedAt = now,
                        observationSha256 = sha256(candidate),
                        lastObservedAt = now,
                        selectedProviderAssetId = "phase5-alpha05-apk",
                    ),
                )
                val verifiedAsset = ReleaseAssetEntity(
                    releaseAssetId = assetId,
                    releaseSnapshotId = snapshotId,
                    providerAssetId = "phase5-alpha05-apk",
                    assetName = "reprodroid-0.1.0-alpha05.apk",
                    stableAssetUrl = "https://github.com/Sanka1610/reprodroid/releases",
                    selectionReason = "MANUAL_RELEASE_ASSET",
                    contentType = "application/vnd.android.package-archive",
                    providerSizeBytes = candidate.length(),
                    providerDigestSha256 = sha256(candidate),
                    downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                    localContentPath = candidate.absolutePath,
                    downloadedSizeBytes = candidate.length(),
                    computedRawSha256 = sha256(candidate),
                    downloadContentType = "application/vnd.android.package-archive",
                    finalDownloadHost = "github.com",
                    packageName = inspection.packageName,
                    versionName = inspection.versionName,
                    versionCode = inspection.versionCode,
                    signingCertificateSha256 = inspection.signingCertificateSha256.joinToString(","),
                    currentSignerSha256 = inspection.currentSignerSha256.joinToString(","),
                    existingInstallStatus = inspection.existingInstallStatus?.name,
                    installedVersionName = inspection.installedVersionName,
                    installedVersionCode = inspection.installedVersionCode,
                    updateStatus = UpdateStatus.UPDATE_AVAILABLE.name,
                    updateEvaluatedAt = now,
                    downloadedAt = now,
                )
                dao.upsertReleaseAsset(verifiedAsset)

                val wrongSigner = verifiedAsset.copy(currentSignerSha256 = "00".repeat(32))
                val wrongSignerFailure = runCatching {
                    ReleaseApkInstaller(context, dao).install(registeredAppId, wrongSigner)
                }.exceptionOrNull()
                assertTrue(
                    "A mismatched persisted signer must fail before PackageInstaller",
                    wrongSignerFailure is IllegalStateException &&
                        wrongSignerFailure.message.orEmpty().contains("signer"),
                )

                instrumentation.startActivitySync(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                instrumentation.waitForIdleSync()
                val attemptId = ReleaseApkInstaller(context, dao).install(registeredAppId, verifiedAsset)
                assertEquals(
                    InstallAttemptStatus.COMMITTED.name,
                    dao.getReleaseInstallAttempt(attemptId)?.status,
                )
                instrumentation.sendStatus(0, Bundle().apply {
                    putString(
                        "selfUpdateReady",
                        "attempt=$attemptId package=${inspection.packageName} " +
                            "from=${inspection.installedVersionCode} to=${inspection.versionCode} " +
                            "signer=${inspection.currentSignerSha256.single()} sha256=${sha256(candidate)}",
                    )
                })

                val deadline = System.currentTimeMillis() + INSTALL_TIMEOUT_MILLIS
                var installed = ApkInspector(context.packageManager).inspect(candidate)
                while (installed.installedVersionCode != EXPECTED_CANDIDATE_VERSION_CODE &&
                    System.currentTimeMillis() < deadline
                ) {
                    delay(500)
                    installed = ApkInspector(context.packageManager).inspect(candidate)
                }
                assertEquals(EXPECTED_CANDIDATE_VERSION_CODE, installed.installedVersionCode)
                assertEquals(ExistingInstallStatus.SIGNER_MATCH, installed.existingInstallStatus)
                assertEquals(listOf(PRODUCTION_SIGNER_SHA256), installed.currentSignerSha256)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString(
                        "selfUpdateEvidence",
                        "package=${installed.packageName} installed=${installed.installedVersionCode} " +
                            "candidate=${installed.versionCode} signer=SIGNER_MATCH userAction=true",
                    )
                })
            } finally {
                database.close()
                candidate.delete()
            }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val RUN_ARGUMENT = "runSelfUpdateProductAcceptance"
        const val SHELL_CANDIDATE_PATH = "/data/local/tmp/reprodroid-alpha05-self-update.apk"
        const val PRODUCTION_PACKAGE = "com.sanka1610.reprodroid"
        const val EXPECTED_INSTALLED_VERSION_CODE = 4L
        const val EXPECTED_CANDIDATE_VERSION_CODE = 5L
        const val INSTALL_TIMEOUT_MILLIS = 120_000L
        const val PRODUCTION_SIGNER_SHA256 =
            "42e0382888f6ebbd22a25532ad6495cd385d54cd86b00e214ad1eacca4d19abd"
    }
}
