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

    @Query("SELECT * FROM artifacts WHERE artifactId = :artifactId AND jobId = :jobId")
    suspend fun getArtifact(jobId: String, artifactId: String): ArtifactEntity?

    @Query("SELECT * FROM artifacts WHERE jobId = :jobId")
    suspend fun getArtifacts(jobId: String): List<ArtifactEntity>

    @Transaction
    @Query("SELECT * FROM build_environment_manifests WHERE jobId = :jobId")
    suspend fun getBuildEnvironmentManifest(jobId: String): BuildEnvironmentManifestWithDependencies?

    @Transaction
    @Query("SELECT * FROM build_environment_manifests ORDER BY jobId")
    fun observeBuildEnvironmentManifests(): Flow<List<BuildEnvironmentManifestWithDependencies>>

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

    @Upsert
    suspend fun upsertInstallAttempt(attempt: InstallAttemptEntity)

    @Upsert
    suspend fun upsertBuildEnvironmentManifest(manifest: BuildEnvironmentManifestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildEnvironmentDependencies(dependencies: List<BuildEnvironmentDependencyEntity>)

    @Query("SELECT * FROM install_attempts WHERE attemptId = :attemptId")
    suspend fun getInstallAttempt(attemptId: String): InstallAttemptEntity?

    @Query(
        """
        SELECT * FROM install_attempts
        WHERE status IN ('PREPARING', 'COMMITTED', 'PENDING_USER_ACTION')
        """,
    )
    suspend fun getPendingInstallAttempts(): List<InstallAttemptEntity>

    @Query("DELETE FROM artifacts WHERE jobId = :jobId")
    suspend fun deleteArtifacts(jobId: String)

    @Query("DELETE FROM build_environment_dependencies WHERE jobId = :jobId")
    suspend fun deleteBuildEnvironmentDependencies(jobId: String)

    @Transaction
    suspend fun replaceBuildEnvironmentManifest(
        manifest: BuildEnvironmentManifestEntity,
        dependencies: List<BuildEnvironmentDependencyEntity>,
    ) {
        upsertBuildEnvironmentManifest(manifest)
        deleteBuildEnvironmentDependencies(manifest.jobId)
        if (dependencies.isNotEmpty()) insertBuildEnvironmentDependencies(dependencies)
    }
}
