package com.scanium.app.monitoring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scanium.app.MainActivity
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.app.config.SecureApiKeyStore

/**
 * Background worker that performs periodic health checks.
 * DEV-only: Only runs in dev flavor builds.
 *
 * This worker:
 * 1. Performs health checks on backend endpoints
 * 2. Decides whether to send a notification based on state transitions
 * 3. Updates the persistent state store
 *
 * @see DevHealthMonitorScheduler for scheduling logic
 */
class DevHealthMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DevHealthMonitor"
        const val WORK_NAME = "dev_health_monitor"
        const val NOTIFICATION_CHANNEL_ID = "dev_health_monitor_channel"
        private const val NOTIFICATION_ID_FAILURE = 9001
        private const val NOTIFICATION_ID_RECOVERY = 9002
    }

    private val stateStore = DevHealthMonitorStateStore(applicationContext)
    private val repository = HealthCheckRepository()

    override suspend fun doWork(): Result {
        // Guard: Only run in dev builds
        if (!FeatureFlags.isDevBuild) {
            Log.w(TAG, "Skipping health check - not a dev build")
            return Result.success()
        }

        val config = stateStore.getConfig()

        // Guard: Only run if enabled
        if (!config.enabled) {
            Log.d(TAG, "Skipping health check - monitoring disabled")
            return Result.success()
        }

        Log.d(TAG, "Starting health check...")

        return try {
            performHealthCheck(config)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed with exception", e)
            // Don't retry on exception - we'll try again on next periodic run
            Result.success()
        }
    }

    private suspend fun performHealthCheck(config: DevHealthMonitorStateStore.MonitorConfig) {
        val previousState = stateStore.getState()
        val apiKey = SecureApiKeyStore(applicationContext).getApiKey()

        val healthConfig = HealthMonitorConfig(
            baseUrl = stateStore.getEffectiveBaseUrl(config),
            apiKey = apiKey,
            notifyOnRecovery = config.notifyOnRecovery,
        )

        // Perform the health check
        val result = repository.performHealthCheck(healthConfig)

        // Decide whether to notify
        val decision = NotificationDecision.shouldNotify(
            previousStatus = previousState.lastStatus,
            currentResult = result,
            previousFailureSignature = previousState.lastFailureSignature,
            lastNotifiedAt = previousState.lastNotifiedAt,
            notifyOnRecovery = config.notifyOnRecovery,
        )

        Log.d(TAG, "Health check result: ${result.status}, Decision: $decision")

        // Update state
        stateStore.updateLastResult(result)

        // Handle notification decision
        when (decision) {
            is NotificationDecision.Decision.NotifyFailure -> {
                sendFailureNotification(decision.reason)
                stateStore.updateLastNotifiedAt(System.currentTimeMillis())
            }
            is NotificationDecision.Decision.NotifyRecovery -> {
                sendRecoveryNotification()
                stateStore.updateLastNotifiedAt(System.currentTimeMillis())
            }
            NotificationDecision.Decision.NoNotification -> {
                // No action needed
            }
        }
    }

    private fun sendFailureNotification(reason: String) {
        ensureNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add extra to navigate to settings
            putExtra("navigate_to", "developer_options")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Scanium backend issue")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID_FAILURE, notification)
            Log.d(TAG, "Sent failure notification: $reason")
        } else {
            Log.w(TAG, "Cannot send notification - permission not granted")
        }
    }

    private fun sendRecoveryNotification() {
        ensureNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "developer_options")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Scanium backend recovered")
            .setContentText("All checks passing")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID_RECOVERY, notification)
            Log.d(TAG, "Sent recovery notification")
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scanium Dev Monitoring",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Background health check notifications (dev builds only)"
            }

            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
