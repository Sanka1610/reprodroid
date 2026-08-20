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
    val effectiveBuildRoot: String? = null,
    val effectiveBuildTasks: String? = null,
    val state: String,
    val progressPercent: Int,
    val latestLogSequence: Long,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val downloadResult: String? = null,
    val installResult: String? = null,
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
)

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

data class JobRecord(
    @Embedded val job: JobEntity,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val artifacts: List<ArtifactEntity>,
    @Relation(parentColumn = "jobId", entityColumn = "jobId")
    val logs: List<LogEntity>,
)
