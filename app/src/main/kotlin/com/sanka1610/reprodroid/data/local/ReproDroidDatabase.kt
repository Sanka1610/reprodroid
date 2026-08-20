package com.sanka1610.reprodroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JobEntity::class, ArtifactEntity::class, LogEntity::class],
    version = 2,
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
    }
}
