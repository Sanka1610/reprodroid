package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

enum class ComparisonRunStatus {
    RESOLVING_RUNNER,
    AWAITING_CONFIRMATION,
    AWAITING_SCAN_REVIEW,
    BUILDING,
    COMPARING,
    RESOLVING_REPEAT_RUNNER,
    AWAITING_REPEAT_CONFIRMATION,
    AWAITING_REPEAT_SCAN_REVIEW,
    REPEAT_BUILDING,
    COMPARING_REPEAT,
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

enum class AdvancedComparisonAxis {
    OFFICIAL_PRIMARY,
    OFFICIAL_REPEAT,
    LOCAL_REPEATABILITY,
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
        Index("repeatRunnerJobId"),
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
    @ColumnInfo(defaultValue = "'NONE'")
    val runnerDependencyPinning: String = "NONE",
    val status: String = ComparisonRunStatus.RESOLVING_RUNNER.name,
    val outcome: String = ComparisonOutcome.NOT_EVALUATED.name,
    val incomparableReason: String? = null,
    @ColumnInfo(defaultValue = "1")
    val protocolVersion: Int = 1,
    val repeatRunnerJobId: String? = null,
    val repeatLocalArtifactId: String? = null,
    val repeatRunnerResolvedCommitSha: String? = null,
    val repeatRunnerRecipeId: String? = null,
    val repeatRunnerVariantName: String? = null,
    @ColumnInfo(defaultValue = "'NONE'")
    val repeatRunnerDependencyPinning: String = "NONE",
    @ColumnInfo(defaultValue = "'NOT_EVALUATED'")
    val repeatOfficialOutcome: String = ComparisonOutcome.NOT_EVALUATED.name,
    @ColumnInfo(defaultValue = "'NOT_EVALUATED'")
    val repeatabilityOutcome: String = ComparisonOutcome.NOT_EVALUATED.name,
    val repeatIncomparableReason: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String? = null,
    @ColumnInfo(defaultValue = "'legacy-v1'")
    val runnerContract: String = "legacy-v1",
    val buildConfigurationRevision: Long? = null,
    val buildConfigurationSha256: String? = null,
    val officialIdentitySha256: String? = null,
    val officialApkSha256: String? = null,
    val officialApkSizeBytes: Long? = null,
    val officialPackageName: String? = null,
    val officialVersionName: String? = null,
    val officialVersionCode: Long? = null,
    val selectedArtifactFileName: String? = null,
    val runnerComparisonId: String? = null,
    val resourceRetryOfComparisonRunId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val resourceRetryCount: Int = 0,
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

@Entity(
    tableName = "advanced_comparison_entries",
    primaryKeys = ["comparisonRunId", "axis", "entryName"],
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
data class AdvancedComparisonEntryEntity(
    val comparisonRunId: String,
    val axis: String,
    val entryName: String,
    val result: String,
    val leftSizeBytes: Long? = null,
    val rightSizeBytes: Long? = null,
    val leftSha256: String? = null,
    val rightSha256: String? = null,
)

@Entity(
    tableName = "apk_entry_evidence",
    primaryKeys = ["comparisonRunId", "axis", "entryName"],
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
data class ApkEntryEvidenceEntity(
    val comparisonRunId: String,
    val axis: String,
    val entryName: String,
    val category: String,
    val result: String,
    val leftSizeBytes: Long? = null,
    val rightSizeBytes: Long? = null,
    val leftCrc32: Long? = null,
    val rightCrc32: Long? = null,
    val leftCompressionMethod: Int? = null,
    val rightCompressionMethod: Int? = null,
    val leftUncompressedSha256: String? = null,
    val rightUncompressedSha256: String? = null,
    val archiveMetadataChanged: Boolean = false,
)

@Entity(
    tableName = "advanced_comparison_summaries",
    primaryKeys = ["comparisonRunId", "axis"],
    foreignKeys = [
        ForeignKey(
            entity = ComparisonRunEntity::class,
            parentColumns = ["comparisonRunId"],
            childColumns = ["comparisonRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comparisonRunId"), Index("registeredAppId")],
)
data class AdvancedComparisonSummaryEntity(
    val comparisonRunId: String,
    val registeredAppId: String,
    val axis: String,
    val inventoryOutcome: String,
    val dexStructuralOutcome: String,
    val manifestSemanticOutcome: String,
    val resourceTableSemanticOutcome: String,
    val reason: String? = null,
    val entryCount: Int = 0,
    val sameCount: Int = 0,
    val changedCount: Int = 0,
    val addedCount: Int = 0,
    val missingCount: Int = 0,
    val semanticDifferenceCount: Int = 0,
)

@Entity(
    tableName = "semantic_difference_evidence",
    primaryKeys = ["comparisonRunId", "axis", "component", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = ComparisonRunEntity::class,
            parentColumns = ["comparisonRunId"],
            childColumns = ["comparisonRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comparisonRunId"), Index("registeredAppId")],
)
data class SemanticDifferenceEvidenceEntity(
    val comparisonRunId: String,
    val registeredAppId: String,
    val axis: String,
    val component: String,
    val stableKey: String,
    val result: String,
    val leftSha256: String? = null,
    val rightSha256: String? = null,
)
