package com.sanka1610.reprodroid.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.network.RunnerConfigurationException
import com.sanka1610.reprodroid.data.network.RunnerApiException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class JobSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as ReproDroidApplication).jobRepository
        return try {
            repository.syncActiveJobs()
            Result.success()
        } catch (failure: RunnerApiException) {
            if (failure.statusCode in 400..499) Result.failure() else Result.retry()
        } catch (_: RunnerConfigurationException) {
            Result.failure()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "reprodroid-job-periodic-sync"
        private const val IMMEDIATE_WORK_NAME = "reprodroid-job-immediate-sync"
        private const val MAX_RETRY_COUNT = 5

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<JobSyncWorker>(15, TimeUnit.MINUTES).build(),
            )
            enqueueImmediate(context)
        }

        fun enqueueImmediate(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<JobSyncWorker>().build(),
            )
        }
    }
}
