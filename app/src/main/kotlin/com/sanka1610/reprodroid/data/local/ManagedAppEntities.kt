package com.sanka1610.reprodroid.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class ManagementMode {
    VERIFICATION,
    ACQUISITION,
}

enum class InstallationSource {
    OFFICIAL_RELEASE,
    LOCAL_BUILD,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ReleaseVariantPreference {
    RELEASE,
    PREVIEW,
    DEBUG,
}

enum class PreferredAbi {
    ARM64_V8A,
    ARMEABI_V7A,
    X86_64,
    UNIVERSAL,
}

enum class ReleaseDiscoveryStatus {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    FAILED,
}

enum class ReferenceDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    VERIFIED,
    FAILED,
}

enum class ComparisonEligibility {
    NOT_EVALUATED,
    READY_FOR_COMPARISON,
    INCOMPARABLE,
}

enum class UpdateStatus {
    NOT_EVALUATED,
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    OLDER_THAN_INSTALLED,
    UNKNOWN,
}

enum class TrustLevel {
    REPRODUCIBLE,
    BUILDABLE,
    DIFFERENT,
    INCOMPARABLE,
    FAILED,
}

data class AppSettingsUpdate(
    val managementMode: ManagementMode,
    val installationSource: InstallationSource,
    val releaseVariantPreference: ReleaseVariantPreference,
    val useGlobalReleaseVariant: Boolean,
    val preferredAbi: PreferredAbi,
    val useGlobalPreferredAbi: Boolean,
    val maxApkSizeBytes: Long,
    val useGlobalMaxApkSize: Boolean,
    val localBuildRiskConfirmed: Boolean,
)

enum class AssetSelectionReason {
    SINGLE_APK,
    PREFERRED_ABI_FILENAME,
    PREFERRED_ABI_AND_VARIANT_FILENAME,
}

@Entity(
    tableName = "registered_apps",
    indices = [Index(value = ["canonicalRepositoryUrl"])],
)
data class RegisteredAppEntity(
    @PrimaryKey val registeredAppId: String,
    val displayName: String,
    val repositoryUrl: String,
    val canonicalRepositoryUrl: String,
    val provider: String,
    val managementMode: String,
    @ColumnInfo(defaultValue = "'OFFICIAL_RELEASE'")
    val installationSource: String = InstallationSource.OFFICIAL_RELEASE.name,
    @ColumnInfo(defaultValue = "'RELEASE'")
    val releaseVariantPreference: String = ReleaseVariantPreference.RELEASE.name,
    @ColumnInfo(defaultValue = "'ARM64_V8A'")
    val preferredAbi: String = PreferredAbi.ARM64_V8A.name,
    @ColumnInfo(defaultValue = "536870912")
    val maxApkSizeBytes: Long = GlobalSettingsEntity.MAX_APK_SIZE_BYTES,
    @ColumnInfo(defaultValue = "0")
    val useGlobalReleaseVariant: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val useGlobalPreferredAbi: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val useGlobalMaxApkSize: Boolean = true,
    val releaseDiscoveryStatus: String = ReleaseDiscoveryStatus.NOT_CHECKED.name,
    val releaseDiscoveryErrorCode: String? = null,
    val releaseDiscoveryErrorMessage: String? = null,
    val releaseMetadataEtag: String? = null,
    val lastReleaseCheckedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "global_settings")
data class GlobalSettingsEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val themeMode: String = ThemeMode.DARK.name,
    val defaultManagementMode: String = ManagementMode.VERIFICATION.name,
    val defaultInstallationSource: String = InstallationSource.OFFICIAL_RELEASE.name,
    val defaultReleaseVariantPreference: String = ReleaseVariantPreference.RELEASE.name,
    val defaultPreferredAbi: String = PreferredAbi.ARM64_V8A.name,
    val defaultMaxApkSizeBytes: Long = MAX_APK_SIZE_BYTES,
    @ColumnInfo(defaultValue = "4294967296")
    val androidStorageBudgetBytes: Long = ANDROID_STORAGE_BUDGET_BYTES,
    @ColumnInfo(defaultValue = "80")
    val storageWarningPercent: Int = STORAGE_WARNING_PERCENT,
    val updatedAt: String,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val MAX_APK_SIZE_BYTES = 512L * 1024L * 1024L
        const val ANDROID_STORAGE_BUDGET_BYTES = 4L * 1024L * 1024L * 1024L
        const val STORAGE_WARNING_PERCENT = 80
    }
}

@Entity(
    tableName = "release_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("registeredAppId"),
        Index(value = ["registeredAppId", "observationSha256"], unique = true),
    ],
)
data class ReleaseSnapshotEntity(
    @PrimaryKey val releaseSnapshotId: String,
    val registeredAppId: String,
    val providerReleaseId: Long,
    val tagName: String,
    val resolvedCommitSha: String,
    val releaseName: String,
    val releaseUrl: String,
    val targetCommitishRaw: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val isImmutable: Boolean,
    val releaseCreatedAt: String,
    val publishedAt: String,
    val fetchedAt: String,
    @ColumnInfo(defaultValue = "''")
    val observationSha256: String,
    @ColumnInfo(defaultValue = "''")
    val lastObservedAt: String,
    val selectedProviderAssetId: Long? = null,
)

@Entity(
    tableName = "release_assets",
    foreignKeys = [
        ForeignKey(
            entity = ReleaseSnapshotEntity::class,
            parentColumns = ["releaseSnapshotId"],
            childColumns = ["releaseSnapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("releaseSnapshotId"),
        Index(value = ["releaseSnapshotId", "providerAssetId"], unique = true),
    ],
)
data class ReleaseAssetEntity(
    @PrimaryKey val releaseAssetId: String,
    val releaseSnapshotId: String,
    val providerAssetId: Long,
    val assetName: String,
    val stableAssetUrl: String,
    val selectionReason: String,
    val contentType: String,
    val providerSizeBytes: Long,
    val providerDigestSha256: String?,
    val downloadStatus: String = ReferenceDownloadStatus.NOT_DOWNLOADED.name,
    val downloadErrorCode: String? = null,
    val downloadErrorMessage: String? = null,
    val localContentPath: String? = null,
    val downloadedSizeBytes: Long? = null,
    val computedRawSha256: String? = null,
    val responseEtag: String? = null,
    val finalDownloadHost: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val signingCertificateSha256: String? = null,
    val currentSignerSha256: String? = null,
    val existingInstallStatus: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    @ColumnInfo(defaultValue = "'NOT_EVALUATED'")
    val updateStatus: String = UpdateStatus.NOT_EVALUATED.name,
    val updateEvaluatedAt: String? = null,
    val comparisonEligibility: String = ComparisonEligibility.NOT_EVALUATED.name,
    val incomparableReason: String? = null,
    val downloadedAt: String? = null,
)

@Entity(
    tableName = "release_install_attempts",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReleaseAssetEntity::class,
            parentColumns = ["releaseAssetId"],
            childColumns = ["releaseAssetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("registeredAppId"), Index("releaseAssetId")],
)
data class ReleaseInstallAttemptEntity(
    @PrimaryKey val attemptId: String,
    val registeredAppId: String,
    val releaseAssetId: String,
    val packageInstallerSessionId: Int?,
    val status: String,
    val packageInstallerStatus: Int?,
    val statusMessage: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class ReleaseSnapshotWithAssets(
    @Embedded val snapshot: ReleaseSnapshotEntity,
    @Relation(parentColumn = "releaseSnapshotId", entityColumn = "releaseSnapshotId")
    val assets: List<ReleaseAssetEntity>,
) {
    val selectedAsset: ReleaseAssetEntity?
        get() = assets.firstOrNull { it.providerAssetId == snapshot.selectedProviderAssetId }
            ?: assets.maxWithOrNull(
                compareBy<ReleaseAssetEntity> { it.downloadedAt != null }
                    .thenBy { it.downloadedAt.orEmpty() }
                    .thenBy { it.releaseAssetId },
            )
}

data class RegisteredAppRecord(
    @Embedded val app: RegisteredAppEntity,
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val repositoryBinding: AppRepositoryBindingEntity? = null,
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val sourceDiscoveries: List<SourceDiscoveryEntity> = emptyList(),
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val buildConfigurations: List<AppBuildConfigurationEntity> = emptyList(),
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val sourceHead: AppSourceHeadEntity? = null,
    @Relation(
        entity = ReleaseSnapshotEntity::class,
        parentColumn = "registeredAppId",
        entityColumn = "registeredAppId",
    )
    val releases: List<ReleaseSnapshotWithAssets>,
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val comparisons: List<ComparisonRunEntity>,
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val advancedComparisonSummaries: List<AdvancedComparisonSummaryEntity> = emptyList(),
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val semanticDifferenceEvidence: List<SemanticDifferenceEvidenceEntity> = emptyList(),
    @Relation(parentColumn = "registeredAppId", entityColumn = "registeredAppId")
    val releaseInstallAttempts: List<ReleaseInstallAttemptEntity>,
) {
    val latestSourceDiscovery: SourceDiscoveryEntity?
        get() = sourceHead?.latestDiscoveryId?.let { currentId ->
            sourceDiscoveries.firstOrNull { it.discoveryId == currentId }
        }

    val selectedBuildConfiguration: AppBuildConfigurationEntity?
        get() = sourceHead?.selectedConfigurationRevision?.let { selectedRevision ->
            buildConfigurations.firstOrNull { it.revision == selectedRevision }
        }

    val latestRelease: ReleaseSnapshotWithAssets?
        get() = releases.maxWithOrNull(
            compareBy<ReleaseSnapshotWithAssets> { it.snapshot.lastObservedAt }
                .thenBy { it.snapshot.releaseSnapshotId },
        )

    val currentComparison: ComparisonRunEntity?
        get() {
            val release = latestRelease ?: return null
            val asset = release.selectedAsset ?: return null
            if (asset.comparisonEligibility == ComparisonEligibility.NOT_EVALUATED.name) return null
            return comparisons
                .asSequence()
                .filter { comparison ->
                    comparison.releaseSnapshotId == release.snapshot.releaseSnapshotId &&
                        comparison.referenceAssetId == asset.releaseAssetId &&
                        comparison.expectedCommitSha == release.snapshot.resolvedCommitSha
                }
                .maxWithOrNull(compareBy<ComparisonRunEntity> { it.createdAt }.thenBy { it.comparisonRunId })
        }

    val currentAdvancedComparisonSummaries: List<AdvancedComparisonSummaryEntity>
        get() = currentComparison?.comparisonRunId?.let { runId ->
            advancedComparisonSummaries.filter { it.comparisonRunId == runId }.sortedBy { it.axis }
        }.orEmpty()

    val currentSemanticDifferenceEvidence: List<SemanticDifferenceEvidenceEntity>
        get() = currentComparison?.comparisonRunId?.let { runId ->
            semanticDifferenceEvidence
                .filter { it.comparisonRunId == runId }
                .sortedWith(compareBy({ it.axis }, { it.component }, { it.stableKey }))
        }.orEmpty()

    val trustLevel: TrustLevel?
        get() = currentComparison?.let { comparison ->
            if (comparison.protocolVersion >= REPEATED_BUILD_PROTOCOL_VERSION) {
                return@let repeatedBuildTrustLevel(comparison)
            }
            when (comparison.outcome) {
                ComparisonOutcome.MATCH.name -> TrustLevel.REPRODUCIBLE
                ComparisonOutcome.DIFFERENT.name -> TrustLevel.DIFFERENT
                ComparisonOutcome.INCOMPARABLE.name -> if (
                    comparison.incomparableReason?.startsWith("RUNNER_JOB_FAILED") == true
                ) {
                    TrustLevel.FAILED
                } else {
                    TrustLevel.INCOMPARABLE
                }
                ComparisonOutcome.NOT_EVALUATED.name -> if (comparison.localArtifactId != null) {
                    TrustLevel.BUILDABLE
                } else {
                    null
                }
                else -> null
            }
        }

    private fun repeatedBuildTrustLevel(comparison: ComparisonRunEntity): TrustLevel? {
        val failureReason = comparison.incomparableReason ?: comparison.repeatIncomparableReason
        if (
            comparison.outcome == ComparisonOutcome.DIFFERENT.name ||
            comparison.repeatOfficialOutcome == ComparisonOutcome.DIFFERENT.name ||
            comparison.repeatabilityOutcome == ComparisonOutcome.DIFFERENT.name
        ) {
            return TrustLevel.DIFFERENT
        }
        if (
            comparison.outcome == ComparisonOutcome.INCOMPARABLE.name ||
            comparison.repeatOfficialOutcome == ComparisonOutcome.INCOMPARABLE.name ||
            comparison.repeatabilityOutcome == ComparisonOutcome.INCOMPARABLE.name
        ) {
            return if (failureReason?.startsWith("RUNNER_JOB_FAILED") == true) {
                TrustLevel.FAILED
            } else {
                TrustLevel.INCOMPARABLE
            }
        }
        if (
            comparison.outcome == ComparisonOutcome.MATCH.name &&
            comparison.repeatOfficialOutcome == ComparisonOutcome.MATCH.name &&
            comparison.repeatabilityOutcome == ComparisonOutcome.MATCH.name
        ) {
            return TrustLevel.REPRODUCIBLE
        }
        return if (comparison.localArtifactId != null) TrustLevel.BUILDABLE else null
    }

    val latestReleaseInstallAttempt: ReleaseInstallAttemptEntity?
        get() = releaseInstallAttempts.maxWithOrNull(
            compareBy<ReleaseInstallAttemptEntity> { it.createdAt }.thenBy { it.attemptId },
        )
}

private const val REPEATED_BUILD_PROTOCOL_VERSION = 2
