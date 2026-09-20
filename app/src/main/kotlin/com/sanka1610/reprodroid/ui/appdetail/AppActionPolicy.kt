package com.sanka1610.reprodroid.ui.appdetail

import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.data.repository.PrivilegedInstallPolicy

internal fun canInstallFromTracking(
    record: RegisteredAppRecord,
    availability: List<ResourceAvailabilityEntity>,
    installerMode: String,
    systemInstallAllowed: Boolean,
    shizukuAllowed: Boolean,
    runnerJobs: Map<String, JobRecord> = emptyMap(),
): Boolean {
    val asset = record.latestRelease?.selectedAsset ?: return false
    if (record.app.trackingState != AppTrackingState.ACTIVE.name ||
        asset.updateStatus !in setOf(UpdateStatus.NOT_INSTALLED.name, UpdateStatus.UPDATE_AVAILABLE.name)
    ) return false
    when (record.app.installationSource) {
        InstallationSource.OFFICIAL_RELEASE.name -> if (!referenceApkAvailable(asset, availability)) return false
        InstallationSource.LOCAL_BUILD.name -> {
            if (record.app.managementMode != ManagementMode.VERIFICATION.name) return false
            val comparison = record.currentComparison ?: return false
            val artifact = runnerJobs[comparison.runnerJobId]?.artifacts?.singleOrNull {
                it.artifactId == comparison.localArtifactId
            } ?: return false
            if (artifact.downloadStatus != ArtifactDownloadStatus.VERIFIED.name ||
                artifact.signingCertificateSha256.isNullOrBlank() || artifact.currentSignerSha256.isNullOrBlank() ||
                artifact.existingInstallStatus == ExistingInstallStatus.SIGNER_MISMATCH.name ||
                availability.none { it.ownerType == "ANDROID" && it.resourceKind == "RUNNER_APK" &&
                    it.resourceId == artifact.artifactId && it.state == ResourceAvailabilityState.PRESENT.name }
            ) return false
        }
        else -> return false
    }
    val requiresConfirmation = (record.app.managementMode == ManagementMode.VERIFICATION.name &&
        record.trustLevel != TrustLevel.REPRODUCIBLE) || asset.existingInstallStatus == "SIGNER_MISMATCH"
    if (requiresConfirmation) return false
    return if (installerMode == InstallerMode.SHIZUKU.name) {
        shizukuAllowed && PrivilegedInstallPolicy.isEligible(false, asset.existingInstallStatus, record.trustLevel)
    } else installerMode == InstallerMode.SYSTEM.name && systemInstallAllowed
}

internal fun List<ReleaseCandidateEntity>.latestInstallableCandidate(
    inspectedRelease: ReleaseSnapshotWithAssets? = null,
): ReleaseCandidateEntity? =
    asSequence()
        .filter {
            it.state != ReleaseCandidateState.NO_APK_ASSET.name &&
                it.state != ReleaseCandidateState.OBSOLETE.name
        }
        .maxWithOrNull(
            compareBy<ReleaseCandidateEntity> { it.publishedAt.orEmpty() }
                .thenBy { it.providerReleaseId.length }
                .thenBy { it.providerReleaseId },
        )?.takeUnless { candidate ->
            val snapshot = inspectedRelease?.snapshot
            val sameObservation = snapshot?.registeredAppId == candidate.registeredAppId &&
                snapshot.providerReleaseId == candidate.providerReleaseId &&
                candidate.observationSha256.isNotBlank() &&
                candidate.observationSha256 == (snapshot.metadataObservationSha256 ?: snapshot.observationSha256)
            sameObservation && inspectedRelease.selectedAsset?.updateStatus in setOf(
                UpdateStatus.UP_TO_DATE.name,
                UpdateStatus.OLDER_THAN_INSTALLED.name,
            )
        }

internal enum class AppInformationPrimaryAction {
    VIEW_VERIFICATION,
    ACQUIRE,
    INSTALL,
    UPDATE,
    VERIFY,
    VERIFY_UPDATE,
}

internal fun appInformationPrimaryAction(
    managementMode: String,
    trustLevel: TrustLevel?,
    updateStatus: String?,
    candidate: ReleaseCandidateEntity?,
    comparisonStatus: String? = null,
): AppInformationPrimaryAction? {
    if (managementMode == ManagementMode.VERIFICATION.name &&
        ComparisonRunStatus.entries.any { it.name == comparisonStatus && it != ComparisonRunStatus.COMPLETED }
    ) return AppInformationPrimaryAction.VIEW_VERIFICATION
    if (candidate != null) return AppInformationPrimaryAction.ACQUIRE
    if (updateStatus !in setOf(UpdateStatus.NOT_INSTALLED.name, UpdateStatus.UPDATE_AVAILABLE.name)) return null
    val isUpdate = updateStatus == UpdateStatus.UPDATE_AVAILABLE.name
    val verificationRequired = managementMode == ManagementMode.VERIFICATION.name && trustLevel != TrustLevel.REPRODUCIBLE
    return when {
        verificationRequired && isUpdate -> AppInformationPrimaryAction.VERIFY_UPDATE
        verificationRequired -> AppInformationPrimaryAction.VERIFY
        isUpdate -> AppInformationPrimaryAction.UPDATE
        else -> AppInformationPrimaryAction.INSTALL
    }
}

internal fun candidateNeedsPreparation(candidate: ReleaseCandidateEntity, release: ReleaseSnapshotWithAssets?): Boolean {
    val snapshot = release?.snapshot ?: return true
    return snapshot.registeredAppId != candidate.registeredAppId ||
        snapshot.providerReleaseId != candidate.providerReleaseId ||
        candidate.observationSha256.isBlank() ||
        candidate.observationSha256 != (snapshot.metadataObservationSha256 ?: snapshot.observationSha256) ||
        release.selectedAsset?.downloadStatus != "VERIFIED"
}

internal fun referenceApkAvailable(asset: ReleaseAssetEntity?, availability: List<ResourceAvailabilityEntity>): Boolean =
    asset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name && availability.any {
        it.ownerType == "ANDROID" && it.resourceKind == "REFERENCE_APK" &&
            it.resourceId == asset.releaseAssetId && it.state == ResourceAvailabilityState.PRESENT.name
    }
