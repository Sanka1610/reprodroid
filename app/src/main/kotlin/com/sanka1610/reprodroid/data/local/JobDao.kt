package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Transaction
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<JobRecord>>

    @Query("SELECT * FROM jobs WHERE jobId = :jobId")
    suspend fun getJob(jobId: String): JobEntity?

    @Query(
        """
        SELECT jobId FROM jobs
        WHERE state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED')
        ORDER BY createdAt
        """,
    )
    suspend fun getActiveJobIds(): List<String>

    @Upsert
    suspend fun upsertJob(job: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<LogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtifacts(artifacts: List<ArtifactEntity>)

    @Query("DELETE FROM artifacts WHERE jobId = :jobId")
    suspend fun deleteArtifacts(jobId: String)
}
