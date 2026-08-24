package com.sanka1610.reprodroid.data.artifact

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.net.toUri
import com.sanka1610.reprodroid.InstallResultActivity
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.ManagedAppDao
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseInstallAttemptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class ReleaseApkInstaller(
    context: Context,
    private val dao: ManagedAppDao,
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val inspector = ApkInspector(packageManager)
    private val referenceRoot = File(applicationContext.filesDir, "reference-apks").toPath()
        .toAbsolutePath()
        .normalize()

    suspend fun install(registeredAppId: String, asset: ReleaseAssetEntity): String =
        withContext(Dispatchers.IO) {
            if (!packageManager.canRequestPackageInstalls()) throw UnknownSourcesPermissionRequired()
            check(asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name) {
                "The official APK must pass download verification before installation."
            }
            check(!asset.currentSignerSha256.isNullOrBlank()) {
                "Only an official APK with verified signing certificate information can be installed."
            }
            val storedPath = asset.localContentPath
                ?: throw IllegalStateException("The verified official APK path is missing.")
            val apkPath = File(storedPath).toPath().toAbsolutePath().normalize()
            if (
                !apkPath.startsWith(referenceRoot) ||
                Files.isSymbolicLink(apkPath) ||
                !Files.isRegularFile(apkPath, LinkOption.NOFOLLOW_LINKS)
            ) {
                throw IllegalStateException("The verified official APK is outside its app-private storage boundary.")
            }
            val expectedSize = asset.downloadedSizeBytes
                ?: throw IllegalStateException("The verified official APK size is missing.")
            val expectedSha = asset.computedRawSha256
                ?: throw IllegalStateException("The verified official APK SHA-256 is missing.")
            if (Files.size(apkPath) != expectedSize || sha256(apkPath.toFile()) != expectedSha) {
                throw IllegalStateException("The app-private official APK no longer matches its verified metadata.")
            }
            val packageName = asset.packageName?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("The verified official APK package name is missing.")
            val inspection = inspector.inspect(apkPath.toFile())
            check(inspection.packageName == packageName && inspection.versionCode == asset.versionCode) {
                "The official APK identity no longer matches its verified package metadata."
            }
            check(
                inspection.signingCertificateSha256.joinToString(",") == asset.signingCertificateSha256 &&
                    inspection.currentSignerSha256.joinToString(",") == asset.currentSignerSha256,
            ) {
                "The official APK signer no longer matches its verified signing metadata."
            }

            val attemptId = UUID.randomUUID().toString()
            val now = Instant.now().toString()
            var attempt = ReleaseInstallAttemptEntity(
                attemptId = attemptId,
                registeredAppId = registeredAppId,
                releaseAssetId = asset.releaseAssetId,
                packageInstallerSessionId = null,
                status = InstallAttemptStatus.PREPARING.name,
                packageInstallerStatus = null,
                statusMessage = null,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsertReleaseInstallAttempt(attempt)

            var sessionId: Int? = null
            try {
                val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(packageName)
                    setSize(expectedSize)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                }
                sessionId = packageManager.packageInstaller.createSession(parameters)
                attempt = attempt.copy(
                    packageInstallerSessionId = sessionId,
                    updatedAt = Instant.now().toString(),
                )
                dao.upsertReleaseInstallAttempt(attempt)
                packageManager.packageInstaller.openSession(sessionId).use { session ->
                    apkPath.toFile().inputStream().use { input ->
                        session.openWrite("base.apk", 0, expectedSize).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    attempt = attempt.copy(
                        status = InstallAttemptStatus.COMMITTED.name,
                        updatedAt = Instant.now().toString(),
                    )
                    dao.upsertReleaseInstallAttempt(attempt)
                    session.commit(statusIntent(attemptId, sessionId).intentSender)
                }
                attemptId
            } catch (failure: Throwable) {
                sessionId?.let { runCatching { packageManager.packageInstaller.abandonSession(it) } }
                dao.upsertReleaseInstallAttempt(
                    attempt.copy(
                        status = InstallAttemptStatus.FAILED.name,
                        statusMessage = failure.message ?: "The official APK install session could not be started.",
                        updatedAt = Instant.now().toString(),
                    ),
                )
                throw failure
            }
        }

    @Suppress("DEPRECATION")
    private fun statusIntent(attemptId: String, sessionId: Int): PendingIntent {
        val intent = Intent(applicationContext, InstallResultActivity::class.java).apply {
            data = "reprodroid://release-install/$attemptId".toUri()
            putExtra(InstallResultActivity.EXTRA_ATTEMPT_ID, attemptId)
            putExtra(InstallResultActivity.EXTRA_ATTEMPT_SOURCE, InstallResultActivity.SOURCE_RELEASE)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    } else {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    },
                )
                .toBundle()
        } else {
            null
        }
        return PendingIntent.getActivity(applicationContext, sessionId, intent, flags, options)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1_024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
