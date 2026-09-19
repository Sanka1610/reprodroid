package com.sanka1610.reprodroid.ui.appdetail

import com.sanka1610.reprodroid.data.local.*

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
