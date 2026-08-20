package com.sanka1610.reprodroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JobEntity::class, ArtifactEntity::class, LogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReproDroidDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}
