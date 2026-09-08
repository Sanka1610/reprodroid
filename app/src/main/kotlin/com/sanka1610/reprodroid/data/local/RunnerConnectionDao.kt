package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunnerConnectionDao {
    @Query("SELECT * FROM runner_connections ORDER BY active DESC, updatedAt DESC, runnerId")
    fun observeConnections(): Flow<List<RunnerConnectionEntity>>

    @Query("SELECT * FROM runner_connections ORDER BY runnerId")
    suspend fun getConnections(): List<RunnerConnectionEntity>

    @Query("SELECT * FROM runner_connections WHERE runnerId = :runnerId")
    suspend fun getConnection(runnerId: String): RunnerConnectionEntity?

    @Query("SELECT * FROM runner_connections WHERE active = 1 ORDER BY runnerId")
    suspend fun getActiveConnections(): List<RunnerConnectionEntity>

    @Query("SELECT * FROM runner_connections WHERE pairingState = 'PENDING_APPROVAL' ORDER BY runnerId")
    suspend fun getPendingConnections(): List<RunnerConnectionEntity>

    @Upsert
    suspend fun upsertConnection(connection: RunnerConnectionEntity)

    @Query("UPDATE runner_connections SET active = 0, updatedAt = :updatedAt WHERE active = 1")
    suspend fun deactivateAll(updatedAt: String)

    @Query("DELETE FROM runner_connections WHERE runnerId = :runnerId")
    suspend fun deleteConnection(runnerId: String)

    @Transaction
    suspend fun activate(connection: RunnerConnectionEntity) {
        deactivateAll(connection.updatedAt)
        upsertConnection(connection.copy(active = true))
    }
}
