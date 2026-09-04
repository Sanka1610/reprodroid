package com.sanka1610.reprodroid.data.local

import androidx.room.Entity

@Entity(
    tableName = "toolchain_installation_references",
    primaryKeys = ["runnerId", "installationId"],
)
data class ToolchainInstallationReferenceEntity(
    val runnerId: String,
    val installationId: String,
    val operationId: String,
    val registeredAppId: String?,
    val buildSettingsRevision: Long?,
    val planSha256: String,
    val catalogSha256: String,
    val state: String,
    val observedAt: String,
)
