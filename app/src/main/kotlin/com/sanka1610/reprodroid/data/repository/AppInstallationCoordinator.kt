package com.sanka1610.reprodroid.data.repository

import android.content.Context
import android.content.pm.PackageInstaller
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.ReleaseApkInstaller
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.InstallerMode
import com.sanka1610.reprodroid.data.local.ManagedAppDao
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppInstallationCoordinator(
    context: Context,
    private val dao: ManagedAppDao,
    private val jobRepository: JobRepository,
    private val storageManager: AndroidStorageManager,
    private val currentSettings: suspend () -> GlobalSettingsEntity,
) {
    private val inspector = ApkInspector(context.packageManager)
    private val releaseInstaller = ReleaseApkInstaller(context, dao)
    private val packageInstaller = context.packageManager.packageInstaller
    suspend fun refreshInstalledStateForApp(registeredAppId: String) {
        val record = dao.getRegisteredAppRecord(registeredAppId) ?: return
        val asset = record.latestRelease?.selectedAsset ?: return
        if (asset.downloadStatus != ReferenceDownloadStatus.VERIFIED.name) return
        if (!storageManager.isPresent("REFERENCE_APK", asset.releaseAssetId)) return
        val path = asset.localContentPath?.let(::File) ?: return
        val inspection = withContext(Dispatchers.IO) { inspector.inspect(path) }
        check(inspection.packageName == asset.packageName) {
            "The saved official APK package identity changed after verification."
        }
        val evaluatedAt = Instant.now().toString()
        dao.upsertReleaseAsset(
            asset.copy(
                signingCertificateSha256 = inspection.signingCertificateSha256.joinToString(","),
                currentSignerSha256 = inspection.currentSignerSha256.joinToString(","),
                existingInstallStatus = inspection.existingInstallStatus?.name,
                installedVersionName = inspection.installedVersionName,
                installedVersionCode = inspection.installedVersionCode,
                updateStatus = evaluateUpdateStatus(asset.versionCode, inspection.installedVersionCode).name,
                updateEvaluatedAt = evaluatedAt,
            ),
        )
    }

    suspend fun installManagedApp(registeredAppId: String, riskConfirmed: Boolean): String {
        val trackedApp = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(trackedApp.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before installing an app."
        }
        refreshInstalledStateForApp(registeredAppId)
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val asset = record.latestRelease?.selectedAsset
            ?: throw IllegalStateException("No selected official APK is available.")
        val updateStatus = enumValueOrDefault(asset.updateStatus, UpdateStatus.UNKNOWN)
        check(updateStatus == UpdateStatus.NOT_INSTALLED || updateStatus == UpdateStatus.UPDATE_AVAILABLE) {
            "Only a new installation or a newer verified APK can start the installer."
        }
        val requiresRiskConfirmation =
            asset.existingInstallStatus == com.sanka1610.reprodroid.data.local.ExistingInstallStatus.SIGNER_MISMATCH.name ||
                (
                    record.app.managementMode == ManagementMode.VERIFICATION.name &&
                        record.trustLevel != com.sanka1610.reprodroid.data.local.TrustLevel.REPRODUCIBLE
                    )
        check(!requiresRiskConfirmation || riskConfirmed) {
            "The signer or reproducibility warning must be acknowledged before installation."
        }
        val settings = currentSettings()
        val requestedInstallerMode = enumValueOrDefault(settings.installerMode, InstallerMode.SYSTEM)
        val privilegedEligible = PrivilegedInstallPolicy.isEligible(
            requiresRiskConfirmation = requiresRiskConfirmation,
            existingInstallStatus = asset.existingInstallStatus,
            trustLevel = record.trustLevel,
        )
        return when (enumValueOrDefault(record.app.installationSource, InstallationSource.OFFICIAL_RELEASE)) {
            InstallationSource.OFFICIAL_RELEASE -> {
                storageManager.requirePresent("REFERENCE_APK", asset.releaseAssetId)
                storageManager.markUsed("REFERENCE_APK", asset.releaseAssetId)
                releaseInstaller.install(
                    registeredAppId = registeredAppId,
                    asset = asset,
                    requestedMode = requestedInstallerMode,
                    recordGooglePlayAsInstaller = settings.recordGooglePlayAsInstaller,
                    privilegedEligible = privilegedEligible,
                )
            }
            InstallationSource.LOCAL_BUILD -> {
                check(record.app.managementMode == ManagementMode.VERIFICATION.name) {
                    "Only verification mode can install a local build."
                }
                val comparison = record.currentComparison
                    ?: throw IllegalStateException("A current comparison run is required for local installation.")
                val artifactId = comparison.localArtifactId
                    ?: throw IllegalStateException("The comparison did not retain a local artifact.")
                val artifact = jobRepository.getArtifacts(comparison.runnerJobId)
                    .singleOrNull { it.artifactId == artifactId }
                    ?: throw IllegalStateException("The compared local artifact is unavailable.")
                check(
                    !artifact.signingCertificateSha256.isNullOrBlank() &&
                        !artifact.currentSignerSha256.isNullOrBlank(),
                ) {
                    "Local build installation is unavailable because the compared artifact is unsigned. " +
                        "Phase 2C does not generate or manage a ReproDroid signing key."
                }
                jobRepository.installArtifact(
                    comparison.runnerJobId,
                    artifactId,
                    privilegedEligible = privilegedEligible,
                )
            }
        }
    }

    suspend fun recordReleaseInstallStatus(
        attemptId: String,
        status: InstallAttemptStatus,
        packageInstallerStatus: Int,
        statusMessage: String?,
    ) {
        val attempt = dao.getReleaseInstallAttempt(attemptId) ?: return
        dao.upsertReleaseInstallAttempt(
            attempt.copy(
                status = status.name,
                packageInstallerStatus = packageInstallerStatus,
                statusMessage = statusMessage,
                updatedAt = Instant.now().toString(),
            ),
        )
        if (status in setOf(InstallAttemptStatus.SUCCEEDED, InstallAttemptStatus.FAILED, InstallAttemptStatus.CANCELLED)) {
            refreshInstalledStateForApp(attempt.registeredAppId)
        }
    }

    suspend fun recoverOrphanedReleaseInstallAttempts() {
        val now = Instant.now()
        val activeSessionIds = packageInstaller.mySessions.mapTo(mutableSetOf()) { it.sessionId }
        dao.getPendingReleaseInstallAttempts()
            .filter { attempt ->
                val stale = runCatching {
                    Instant.parse(attempt.updatedAt).plusSeconds(INSTALL_CALLBACK_GRACE_SECONDS) <= now
                }.getOrDefault(false)
                stale && (
                    attempt.installerMode == InstallerMode.SHIZUKU.name ||
                        attempt.packageInstallerSessionId !in activeSessionIds
                    )
            }
            .forEach { attempt ->
                dao.upsertReleaseInstallAttempt(
                    attempt.copy(
                        status = InstallAttemptStatus.FAILED.name,
                        packageInstallerStatus = PackageInstaller.STATUS_FAILURE,
                        statusMessage = if (attempt.installerMode == InstallerMode.SHIZUKU.name) {
                            "The privileged PackageInstaller callback was not received before the recovery timeout."
                        } else {
                            "The PackageInstaller session is no longer active, but no terminal callback was received."
                        },
                        updatedAt = now.toString(),
                    ),
                )
                refreshInstalledStateForApp(attempt.registeredAppId)
            }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private companion object {
        const val INSTALL_CALLBACK_GRACE_SECONDS = 30L
    }
}
