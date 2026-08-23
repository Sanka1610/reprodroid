package com.sanka1610.reprodroid.data.local

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

enum class AssetSelectionReason {
    SINGLE_APK,
    ARM64_V8A_FILENAME,
}

@Entity(
    tableName = "registered_apps",
    indices = [Index(value = ["canonicalRepositoryUrl"], unique = true)],
)
data class RegisteredAppEntity(
    @PrimaryKey val registeredAppId: String,
    val displayName: String,
    val repositoryUrl: String,
    val canonicalRepositoryUrl: String,
    val provider: String,
    val managementMode: String,
    val releaseDiscoveryStatus: String = ReleaseDiscoveryStatus.NOT_CHECKED.name,
    val releaseDiscoveryErrorCode: String? = null,
    val releaseDiscoveryErrorMessage: String? = null,
    val releaseMetadataEtag: String? = null,
    val lastReleaseCheckedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

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
        Index(value = ["registeredAppId", "providerReleaseId"], unique = true),
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
    val comparisonEligibility: String = ComparisonEligibility.NOT_EVALUATED.name,
    val incomparableReason: String? = null,
    val downloadedAt: String? = null,
)

data class ReleaseSnapshotWithAssets(
    @Embedded val snapshot: ReleaseSnapshotEntity,
    @Relation(parentColumn = "releaseSnapshotId", entityColumn = "releaseSnapshotId")
    val assets: List<ReleaseAssetEntity>,
)

data class RegisteredAppRecord(
    @Embedded val app: RegisteredAppEntity,
    @Relation(
        entity = ReleaseSnapshotEntity::class,
        parentColumn = "registeredAppId",
        entityColumn = "registeredAppId",
    )
    val releases: List<ReleaseSnapshotWithAssets>,
) {
    val latestRelease: ReleaseSnapshotWithAssets?
        get() = releases.maxByOrNull { it.snapshot.publishedAt }
}
