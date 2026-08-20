package com.sanka1610.reprodroid

import android.app.Application
import androidx.room.Room
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.repository.JobRepository
import com.sanka1610.reprodroid.work.JobSyncWorker

class ReproDroidApplication : Application() {
    lateinit var jobRepository: JobRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            ReproDroidDatabase::class.java,
            "reprodroid.sqlite3",
        ).addMigrations(ReproDroidDatabase.MIGRATION_1_2).build()
        jobRepository = JobRepository(
            database = database,
            runnerApi = RunnerApiClient(BuildConfig.RUNNER_BASE_URL),
        )
        JobSyncWorker.schedule(this)
    }
}
