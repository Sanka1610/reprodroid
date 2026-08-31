package com.sanka1610.reprodroid.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(tableName = "jobs", primaryKeys = ["jobId"])
data class JobEntity(
    val jobId: String,
    val executionMode: String,
    val repositoryUrl: String,
    val revisionType: String,
    val revisionValue: String,
    val simulationOutcome: String?,
    val resolvedCommitSha: String? = null,
    @ColumnInfo(defaultValue = "0")
    val requiresConfirmation: Boolean = false,
    val effectiveRecipeId: String? = null,
    val effectiveVariantName: String? = null,
    val effectiveBuildRoot: String? = null,
    val effectiveJavaMajor: Int? = null,
    val effectiveBuildTasks: String? = null,
    @ColumnInfo(defaultValue = "'NONE'")
    val effectiveDependencyPinning: String = "NONE",
    val effectiveSourceDateEpoch: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val effectiveNoBuildCache: Boolean = false,
    val effectiveFixedLocale: String? = null,
    val state: String,
    val progressPercent: Int,
    val latestLogSequence: Long,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val downloadResult: String? = null,
    val installResult: String? = null,
    val sandboxMode: String? = null,
    val sandboxOrigin: String? = null,
    val sandboxProfileId: String? = null,
    val sandboxCleanupStatus: String? = null,
    @ColumnInfo(defaultValue = "0")
    val sandboxResponseSeen: Boolean = false,
)

@Entity(
    tableName = "artifacts",
    primaryKeys = ["artifactId"],
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class ArtifactEntity(
    val artifactId: String,
    val jobId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    @ColumnInfo(defaultValue = "'NOT_DOWNLOADED'")
    val downloadStatus: String = ArtifactDownloadStatus.NOT_DOWNLOADED.name,
    val downloadError: String? = null,
    val localContentPath: String? = null,
    val downloadedSizeBytes: Long? = null,
    val downloadedSha256: String? = null,
    val signingCertificateSha256: String? = null,
    val currentSignerSha256: String? = null,
    val existingInstallStatus: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val downloadedAt: String? = null,
)

enum class ArtifactDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    VERIFIED,
    FAILED,
}

enum class ExistingInstallStatus {
    NOT_INSTALLED_OR_NOT_VISIBLE,
    SIGNER_MATCH,
    SIGNER_MISMATCH,
}

@Entity(
    tableName = "install_attempts",
    primaryKeys = ["attemptId"],
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId"), Index("artifactId")],
)
data class InstallAttemptEntity(
    val attemptId: String,
    val jobId: String,
    val artifactId: String,
    val packageInstallerSessionId: Int?,
    val status: String,
    val packageInstallerStatus: Int?,
    val statusMessage: String?,
    val createdAt: String,
    val updatedAt: String,
)

enum class InstallAttemptStatus {
    PREPARING,
    COMMITTED,
    PENDING_USER_ACTION,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

@Entity(
    tableName = "logs",
    primaryKeys = ["jobId", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class LogEntity(
    val jobId: String,
    val sequence: Long,
    val timestamp: String,
    val level: String,
    val message: String,
)

@Entity(
    tableName = "build_environment_manifests",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BuildEnvironmentManifestEntity(
    @androidx.room.PrimaryKey val jobId: String,
    val schemaVersion: Int,
    val commitSha: String,
    val javaVersion: String,
    val javaVendor: String,
    val gradleVersion: String,
    val androidSdkApiLevel: Int,
    val buildToolsVersion: String,
    val apkSha256: String,
    val sourceDateEpoch: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val noBuildCache: Boolean = false,
    val fixedLocale: String? = null,
    val retrievedAt: String,
    val sandboxJson: String? = null,
)

@Entity(
    tableName = "build_environment_dependencies",
    primaryKeys = ["jobId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class BuildEnvironmentDependencyEntity(
    val jobId: String,
    val ordinal: Int,
    val fileName: String,
    val sha256: String,
)

data class BuildEnvironmentManifestWithDependencies(
    @Embedded val manifest: BuildEnvironmentManifestEntity,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val dependencies: List<BuildEnvironmentDependencyEntity>,
)

@Entity(
    tableName = "source_scans",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SourceScanEntity(
    @androidx.room.PrimaryKey val jobId: String,
    val schemaVersion: Int,
    val resolvedCommitSha: String,
    val scannerVersion: String,
    val resultSha256: String,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val skippedBinaryFiles: Int,
    val skippedSymlinks: Int,
    val findingCount: Int,
    val requiresReview: Boolean,
    val reviewed: Boolean,
    val retrievedAt: String,
)

@Entity(
    tableName = "source_scan_detector_counts",
    primaryKeys = ["jobId", "detectorId"],
    foreignKeys = [
        ForeignKey(
            entity = SourceScanEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class SourceScanDetectorCountEntity(
    val jobId: String,
    val detectorId: String,
    val count: Int,
)

@Entity(
    tableName = "source_scan_findings",
    primaryKeys = ["jobId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = SourceScanEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId")],
)
data class SourceScanFindingEntity(
    val jobId: String,
    val ordinal: Int,
    val detectorId: String,
    val displayPath: String,
    val line: Int?,
    val column: Int?,
)

data class SourceScanWithDetails(
    @Embedded val scan: SourceScanEntity,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val detectorCounts: List<SourceScanDetectorCountEntity>,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val findings: List<SourceScanFindingEntity>,
)

data class JobRecord(
    @Embedded val job: JobEntity,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val artifacts: List<ArtifactEntity>,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val logs: List<LogEntity>,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val installAttempts: List<InstallAttemptEntity>,
    @Relation(parentColumn = "jobId", entityColumn = "jobId", entity = BuildEnvironmentManifestEntity::class)
    val buildEnvironmentManifest: BuildEnvironmentManifestWithDependencies? = null,
    @Relation(parentColumn = "jobId", entityColumn = "jobId", entity = SourceScanEntity::class)
    val sourceScan: SourceScanWithDetails? = null,
)
