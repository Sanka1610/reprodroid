package com.sanka1610.reprodroid.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.repository.ReleaseCheckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class ReleaseCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ReproDroidApplication ?: return Result.failure()
        val completed = try {
            app.releaseCheckRepository.runDueChecks()
            app.releaseCheckRepository.runRetentionMaintenance()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        return try {
            ReleaseCheckScheduler.enqueueDelivery(applicationContext)
            ReleaseCheckScheduler.scheduleNext(
                applicationContext,
                app.releaseCheckRepository,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
            )
            if (completed) Result.success() else Result.failure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.failure()
        }
    }
}

class NotificationDeliveryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ReproDroidApplication ?: return Result.failure()
        return try {
            app.releaseCheckRepository.deliverPendingNotifications()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.failure()
        }
    }
}

object ReleaseCheckScheduler {
    private const val CHECK_WORK_NAME = "reprodroid-release-check"
    private const val DELIVERY_WORK_NAME = "reprodroid-release-notification-delivery"

    suspend fun reconcile(
        context: Context,
        repository: ReleaseCheckRepository,
        forceRecalculate: Boolean,
    ) {
        repository.reconcileSchedules(forceRecalculate = forceRecalculate)
        scheduleNext(context, repository, ExistingWorkPolicy.REPLACE)
    }

    suspend fun scheduleNext(
        context: Context,
        repository: ReleaseCheckRepository,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val workManager = WorkManager.getInstance(context)
        val dueAt = repository.nextDispatchAt()
        if (dueAt == null) {
            workManager.cancelUniqueWork(CHECK_WORK_NAME)
            return
        }
        val delay = Duration.between(Instant.now(), dueAt).toMillis().coerceAtLeast(0L)
        val requiresCharging = repository.allScheduledChecksRequireCharging()
        val request = OneTimeWorkRequestBuilder<ReleaseCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(requiresCharging)
                    .build(),
            )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(CHECK_WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(CHECK_WORK_NAME, policy, request)
    }

    fun enqueueDelivery(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotificationDeliveryWorker>()
            .addTag(DELIVERY_WORK_NAME)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DELIVERY_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class ReleaseScheduleReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCEPTED_ACTIONS) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = appContext as? ReproDroidApplication ?: return@launch
                ReleaseCheckScheduler.reconcile(appContext, app.releaseCheckRepository, forceRecalculate = true)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
