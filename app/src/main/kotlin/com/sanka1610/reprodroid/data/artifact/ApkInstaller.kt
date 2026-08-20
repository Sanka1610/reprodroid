package com.sanka1610.reprodroid.data.artifact

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.net.toUri
import com.sanka1610.reprodroid.InstallResultActivity
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.InstallAttemptEntity
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.JobDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class UnknownSourcesPermissionRequired : RuntimeException(
    "Permission to install unknown apps is required before opening the system installer.",
)

class ApkInstaller(
    context: Context,
    private val jobDao: JobDao,
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val filesDirectory = applicationContext.filesDir.toPath().toAbsolutePath().normalize()

    suspend fun install(jobId: String, artifact: ArtifactEntity): String = withContext(Dispatchers.IO) {
        if (!packageManager.canRequestPackageInstalls()) throw UnknownSourcesPermissionRequired()
        if (artifact.downloadStatus != ArtifactDownloadStatus.VERIFIED.name) {
            throw IllegalStateException("The APK must pass transfer verification before installation.")
        }
        val relativePath = artifact.localContentPath
            ?: throw IllegalStateException("The verified APK path is missing.")
        val apkPath = filesDirectory.resolve(relativePath).normalize()
        if (!apkPath.startsWith(filesDirectory) || !apkPath.toFile().isFile) {
            throw IllegalStateException("The verified APK is no longer available in app storage.")
        }
        if (
            apkPath.toFile().length() != artifact.sizeBytes ||
            artifact.downloadedSha256 != artifact.sha256 ||
            sha256(apkPath.toFile()) != artifact.sha256
        ) {
            throw IllegalStateException("The app-private APK no longer matches its verified SHA-256 metadata.")
        }
        check(artifact.packageName.isNotBlank()) { "The verified APK package name is missing." }

        val attemptId = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        var attempt = InstallAttemptEntity(
            attemptId = attemptId,
            jobId = jobId,
            artifactId = artifact.artifactId,
            packageInstallerSessionId = null,
            status = InstallAttemptStatus.PREPARING.name,
            packageInstallerStatus = null,
            statusMessage = null,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        jobDao.upsertInstallAttempt(attempt)

        var sessionId: Int? = null
        try {
            val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(artifact.packageName)
                setSize(apkPath.toFile().length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            sessionId = packageManager.packageInstaller.createSession(parameters)
            attempt = attempt.copy(
                packageInstallerSessionId = sessionId,
                updatedAt = Instant.now().toString(),
            )
            jobDao.upsertInstallAttempt(attempt)

            packageManager.packageInstaller.openSession(sessionId).use { session ->
                apkPath.toFile().inputStream().use { input ->
                    session.openWrite("base.apk", 0, apkPath.toFile().length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                attempt = attempt.copy(
                    status = InstallAttemptStatus.COMMITTED.name,
                    updatedAt = Instant.now().toString(),
                )
                jobDao.upsertInstallAttempt(attempt)
                session.commit(statusIntent(attemptId, sessionId).intentSender)
            }
            attemptId
        } catch (failure: Throwable) {
            sessionId?.let { runCatching { packageManager.packageInstaller.abandonSession(it) } }
            jobDao.upsertInstallAttempt(
                attempt.copy(
                    status = InstallAttemptStatus.FAILED.name,
                    statusMessage = failure.message ?: "The PackageInstaller session could not be started.",
                    updatedAt = Instant.now().toString(),
                ),
            )
            throw failure
        }
    }

    private fun statusIntent(attemptId: String, sessionId: Int): PendingIntent {
        val intent = Intent(applicationContext, InstallResultActivity::class.java).apply {
            data = "reprodroid://install/$attemptId".toUri()
            putExtra(InstallResultActivity.EXTRA_ATTEMPT_ID, attemptId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(applicationContext, sessionId, intent, flags)
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
