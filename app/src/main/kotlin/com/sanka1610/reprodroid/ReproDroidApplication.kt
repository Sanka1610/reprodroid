package com.sanka1610.reprodroid

import android.app.Application
import androidx.room.Room
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.repository.JobRepository
import com.sanka1610.reprodroid.data.repository.ManagedAppRepository
import com.sanka1610.reprodroid.work.JobSyncWorker

class ReproDroidApplication : Application() {
    lateinit var jobRepository: JobRepository
        private set
    lateinit var managedAppRepository: ManagedAppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            ReproDroidDatabase::class.java,
            "reprodroid.sqlite3",
        ).addMigrations(
            ReproDroidDatabase.MIGRATION_1_2,
            ReproDroidDatabase.MIGRATION_2_3,
            ReproDroidDatabase.MIGRATION_3_4,
            ReproDroidDatabase.MIGRATION_4_5,
            ReproDroidDatabase.MIGRATION_5_6,
            ReproDroidDatabase.MIGRATION_6_7,
            ReproDroidDatabase.MIGRATION_7_8,
        ).build()
        jobRepository = JobRepository(
            applicationContext = applicationContext,
            database = database,
            runnerApi = RunnerApiClient(BuildConfig.RUNNER_BASE_URL),
        )
        managedAppRepository = ManagedAppRepository(
            context = applicationContext,
            database = database,
            jobRepository = jobRepository,
        )
        JobSyncWorker.schedule(this)
    }
}
