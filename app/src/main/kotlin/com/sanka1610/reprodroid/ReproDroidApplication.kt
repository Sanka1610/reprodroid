package com.sanka1610.reprodroid

import android.app.Application
import androidx.room.Room
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.DatabaseMigrationGate
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.repository.JobRepository
import com.sanka1610.reprodroid.data.repository.ManagedAppRepository
import com.sanka1610.reprodroid.data.repository.ReleaseCheckRepository
import com.sanka1610.reprodroid.work.JobSyncWorker
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import com.sanka1610.reprodroid.data.storage.AndroidCleanupManager
import com.sanka1610.reprodroid.data.storage.RunnerRetentionCoordinator
import com.sanka1610.reprodroid.data.storage.AuditExportManager
import com.sanka1610.reprodroid.data.toolchain.ToolchainCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReproDroidApplication : Application() {
    lateinit var jobRepository: JobRepository
        private set
    lateinit var managedAppRepository: ManagedAppRepository
        private set
    lateinit var storageManager: AndroidStorageManager
        private set
    lateinit var cleanupManager: AndroidCleanupManager
        private set
    lateinit var retentionCoordinator: RunnerRetentionCoordinator
        private set
    lateinit var auditExportManager: AuditExportManager
        private set
    lateinit var toolchainCoordinator: ToolchainCoordinator
        private set
    lateinit var releaseCheckRepository: ReleaseCheckRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DatabaseMigrationGate.prepare(
            context = applicationContext,
            databaseName = "reprodroid.sqlite3",
            targetVersion = 20,
        )
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
            ReproDroidDatabase.MIGRATION_8_9,
            ReproDroidDatabase.MIGRATION_9_10,
            ReproDroidDatabase.MIGRATION_10_11,
            ReproDroidDatabase.MIGRATION_11_12,
            ReproDroidDatabase.MIGRATION_12_13,
            ReproDroidDatabase.MIGRATION_13_14,
            ReproDroidDatabase.MIGRATION_14_15,
            ReproDroidDatabase.MIGRATION_15_16,
            ReproDroidDatabase.MIGRATION_16_17,
            ReproDroidDatabase.MIGRATION_17_18,
            ReproDroidDatabase.MIGRATION_18_19,
            ReproDroidDatabase.MIGRATION_19_20,
        ).build()
        storageManager = AndroidStorageManager(applicationContext, database)
        cleanupManager = AndroidCleanupManager(applicationContext, database)
        val runnerApi = RunnerApiClient(
            BuildConfig.RUNNER_BASE_URL,
            allowDevelopmentV2 = BuildConfig.DEBUG,
        )
        retentionCoordinator = RunnerRetentionCoordinator(database, runnerApi)
        toolchainCoordinator = ToolchainCoordinator(database, runnerApi)
        auditExportManager = AuditExportManager(applicationContext, database, storageManager)
        jobRepository = JobRepository(
            applicationContext = applicationContext,
            database = database,
            runnerApi = runnerApi,
            storageManager = storageManager,
        )
        managedAppRepository = ManagedAppRepository(
            context = applicationContext,
            database = database,
            jobRepository = jobRepository,
            storageManager = storageManager,
            cleanupManager = cleanupManager,
            retentionCoordinator = retentionCoordinator,
        )
        releaseCheckRepository = ReleaseCheckRepository(applicationContext, database)
        JobSyncWorker.schedule(this)
        applicationScope.launch {
            releaseCheckRepository.ensureInitialized()
            ReleaseCheckScheduler.enqueueDelivery(applicationContext)
            ReleaseCheckScheduler.scheduleNext(applicationContext, releaseCheckRepository)
        }
    }
}
