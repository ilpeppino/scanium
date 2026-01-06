package com.scanium.app.monitoring

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Scheduler for the DEV health monitor worker.
 * Manages periodic work scheduling, on-demand checks, and status queries.
 *
 * DEV-only: This class should only be used when FeatureFlags.isDevBuild is true.
 */
class DevHealthMonitorScheduler(private val context: Context) {
    companion object {
        private const val TAG = "DevHealthMonitor"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        private const val ONE_TIME_WORK_NAME = "dev_health_monitor_once"
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Enable periodic health monitoring.
     * Schedules a PeriodicWorkRequest that runs every 15 minutes.
     * Uses UPDATE policy to ensure latest configuration is applied.
     *
     * This is idempotent - calling multiple times will update the existing work.
     */
    fun enable() {
        if (!FeatureFlags.isDevBuild) {
            Log.w(TAG, "Cannot enable health monitor - not a dev build")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DevHealthMonitorWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DevHealthMonitorWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )

        Log.i(TAG, "Health monitor enabled (every ${PERIODIC_INTERVAL_MINUTES}min)")
    }

    /**
     * Disable periodic health monitoring.
     * Cancels the scheduled periodic work.
     */
    fun disable() {
        workManager.cancelUniqueWork(DevHealthMonitorWorker.WORK_NAME)
        Log.i(TAG, "Health monitor disabled")
    }

    /**
     * Reschedule the health monitor.
     * Useful after configuration changes.
     */
    fun reschedule() {
        if (!FeatureFlags.isDevBuild) return

        // Cancel and re-enable
        disable()
        enable()
    }

    /**
     * Run a one-time health check immediately.
     * Useful for manual testing from the UI.
     */
    fun runNow() {
        if (!FeatureFlags.isDevBuild) {
            Log.w(TAG, "Cannot run health check - not a dev build")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DevHealthMonitorWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        Log.i(TAG, "One-time health check enqueued")
    }

    /**
     * Get the current state of the periodic work.
     */
    fun getWorkInfoFlow(): Flow<WorkState> {
        return workManager.getWorkInfosForUniqueWorkFlow(DevHealthMonitorWorker.WORK_NAME)
            .map { infos ->
                val info = infos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.ENQUEUED -> WorkState.Scheduled
                    WorkInfo.State.RUNNING -> WorkState.Running
                    WorkInfo.State.SUCCEEDED -> WorkState.Completed
                    WorkInfo.State.FAILED -> WorkState.Failed
                    WorkInfo.State.BLOCKED -> WorkState.Blocked
                    WorkInfo.State.CANCELLED -> WorkState.Cancelled
                    null -> WorkState.NotScheduled
                }
            }
    }

    /**
     * Check if periodic work is currently scheduled.
     */
    suspend fun isScheduled(): Boolean {
        val infos = workManager.getWorkInfosForUniqueWork(DevHealthMonitorWorker.WORK_NAME).get()
        return infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }

    /**
     * State of the worker.
     */
    enum class WorkState {
        NotScheduled,
        Scheduled,
        Running,
        Completed,
        Failed,
        Blocked,
        Cancelled,
    }
}
