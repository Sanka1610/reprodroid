package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class ComparisonRunStatus {
    RESOLVING_RUNNER,
    AWAITING_CONFIRMATION,
    BUILDING,
    COMPARING,
    COMPLETED,
}

enum class ComparisonOutcome {
    NOT_EVALUATED,
    MATCH,
    DIFFERENT,
    INCOMPARABLE,
}

enum class ComparisonEntryResult {
    MATCH,
    ADDED,
    MISSING,
    HASH_MISMATCH,
}

@Entity(
    tableName = "comparison_runs",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReleaseSnapshotEntity::class,
            parentColumns = ["releaseSnapshotId"],
            childColumns = ["releaseSnapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReleaseAssetEntity::class,
            parentColumns = ["releaseAssetId"],
            childColumns = ["referenceAssetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["runnerJobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("registeredAppId"),
        Index("releaseSnapshotId"),
        Index("referenceAssetId"),
        Index("runnerJobId"),
    ],
)
data class ComparisonRunEntity(
    @androidx.room.PrimaryKey val comparisonRunId: String,
    val registeredAppId: String,
    val releaseSnapshotId: String,
    val referenceAssetId: String,
    val runnerJobId: String,
    val localArtifactId: String? = null,
    val expectedCommitSha: String,
    val runnerResolvedCommitSha: String? = null,
    val expectedRecipeId: String,
    val runnerRecipeId: String? = null,
    val expectedVariantName: String,
    val runnerVariantName: String? = null,
    val status: String = ComparisonRunStatus.RESOLVING_RUNNER.name,
    val outcome: String = ComparisonOutcome.NOT_EVALUATED.name,
    val incomparableReason: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String? = null,
)

@Entity(
    tableName = "comparison_entries",
    primaryKeys = ["comparisonRunId", "entryName"],
    foreignKeys = [
        ForeignKey(
            entity = ComparisonRunEntity::class,
            parentColumns = ["comparisonRunId"],
            childColumns = ["comparisonRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comparisonRunId")],
)
data class ComparisonEntryEntity(
    val comparisonRunId: String,
    val entryName: String,
    val result: String,
    val referenceSizeBytes: Long? = null,
    val localSizeBytes: Long? = null,
    val referenceSha256: String? = null,
    val localSha256: String? = null,
)
