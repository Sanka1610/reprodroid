package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class RepositoryIdentityStatus {
    VERIFIED,
    LEGACY_UNRESOLVED,
    LEGACY_INVALID,
}

enum class SourceDiscoveryState {
    RESOLVING,
    SCANNING_TREE,
    COMPLETE,
    INCOMPLETE,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class BuildConfigurationValidationState {
    DRAFT,
    CONFIGURED,
}

@Entity(
    tableName = "app_repository_bindings",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["provider", "instance", "providerRepositoryId", "registrationSlot"],
            unique = true,
        ),
    ],
)
data class AppRepositoryBindingEntity(
    @androidx.room.PrimaryKey val registeredAppId: String,
    val provider: String,
    val instance: String,
    val providerRepositoryId: String?,
    val identityStatus: String,
    val registrationSlot: String,
    val verifiedAt: String?,
)

@Entity(
    tableName = "source_discoveries",
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
        Index(value = ["registeredAppId", "discoveryId"], unique = true),
    ],
)
data class SourceDiscoveryEntity(
    @androidx.room.PrimaryKey val discoveryId: String,
    val registeredAppId: String,
    val repositoryProvider: String,
    val repositoryInstance: String,
    val providerRepositoryId: String,
    val requestedBranch: String,
    val resolvedCommitSha: String?,
    val rootTreeSha: String?,
    val state: String,
    val reason: String?,
    val entryCount: Long,
    val requestCount: Int,
    val receivedBytes: Long,
    val maxDepth: Int,
    val candidateCount: Int,
    val excludedSymlinkCount: Long,
    val excludedSubmoduleCount: Long,
    val excludedCacheTreeCount: Long,
    val startedAt: String,
    val finishedAt: String?,
)

@Entity(
    tableName = "gradle_candidates",
    primaryKeys = ["discoveryId", "relativePath"],
    foreignKeys = [
        ForeignKey(
            entity = SourceDiscoveryEntity::class,
            parentColumns = ["discoveryId"],
            childColumns = ["discoveryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("discoveryId")],
)
data class GradleCandidateEntity(
    val discoveryId: String,
    val relativePath: String,
    val buildRoot: String,
    val fileKind: String,
    val blobSha: String,
    val mode: String,
    val dsl: String,
)

@Entity(
    tableName = "app_build_configurations",
    primaryKeys = ["registeredAppId", "revision"],
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
        Index(value = ["registeredAppId", "revision"], unique = true),
        Index(value = ["registeredAppId", "contentSha256"], unique = true),
    ],
)
data class AppBuildConfigurationEntity(
    val registeredAppId: String,
    val revision: Long,
    val schemaVersion: Int,
    val canonicalJson: String,
    val contentSha256: String,
    val validationState: String,
    val createdAt: String,
)

@Entity(
    tableName = "app_source_heads",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceDiscoveryEntity::class,
            parentColumns = ["registeredAppId", "discoveryId"],
            childColumns = ["registeredAppId", "latestDiscoveryId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AppBuildConfigurationEntity::class,
            parentColumns = ["registeredAppId", "revision"],
            childColumns = ["registeredAppId", "selectedConfigurationRevision"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["registeredAppId", "latestDiscoveryId"]),
        Index(value = ["registeredAppId", "selectedConfigurationRevision"]),
    ],
)
data class AppSourceHeadEntity(
    @androidx.room.PrimaryKey val registeredAppId: String,
    val latestDiscoveryId: String?,
    val selectedConfigurationRevision: Long?,
    val updatedAt: String,
)
