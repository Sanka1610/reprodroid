package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolchainDao {
    @Query("SELECT * FROM toolchain_installation_references ORDER BY observedAt DESC")
    fun observeInstallationReferences(): Flow<List<ToolchainInstallationReferenceEntity>>

    @Query("SELECT * FROM toolchain_installation_references WHERE state NOT IN ('INSTALLED','CANCELLED','FAILED','RECONCILIATION_REQUIRED') ORDER BY observedAt")
    suspend fun activeInstallationReferences(): List<ToolchainInstallationReferenceEntity>

    @Upsert
    suspend fun upsertInstallationReference(reference: ToolchainInstallationReferenceEntity)
}
