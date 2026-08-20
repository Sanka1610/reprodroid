package com.sanka1610.reprodroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JobEntity::class, ArtifactEntity::class, LogEntity::class, InstallAttemptEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ReproDroidDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN resolvedCommitSha TEXT")
                db.execSQL(
                    "ALTER TABLE jobs ADD COLUMN requiresConfirmation INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveBuildRoot TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveBuildTasks TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE artifacts ADD COLUMN downloadStatus TEXT NOT NULL DEFAULT 'NOT_DOWNLOADED'",
                )
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadError TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN localContentPath TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedSizeBytes INTEGER")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN signingCertificateSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN currentSignerSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN existingInstallStatus TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN installedVersionName TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN installedVersionCode INTEGER")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedAt TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS install_attempts (
                        attemptId TEXT NOT NULL,
                        jobId TEXT NOT NULL,
                        artifactId TEXT NOT NULL,
                        packageInstallerSessionId INTEGER,
                        status TEXT NOT NULL,
                        packageInstallerStatus INTEGER,
                        statusMessage TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(attemptId),
                        FOREIGN KEY(jobId) REFERENCES jobs(jobId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_install_attempts_jobId ON install_attempts(jobId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_install_attempts_artifactId ON install_attempts(artifactId)",
                )
            }
        }
    }
}
