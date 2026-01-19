package com.scanium.app.monitoring

/**
 * Pure functions for deciding whether to send health monitor notifications.
 * These functions are deterministic and easily testable.
 *
 * DEV-only: Used by the background health monitor.
 */
object NotificationDecision {
    /**
     * Decision result for notification logic.
     */
    sealed class Decision {
        /** Do not send any notification */
        data object NoNotification : Decision()

        /** Send a failure notification */
        data class NotifyFailure(
            val reason: String,
        ) : Decision()

        /** Send a recovery notification */
        data object NotifyRecovery : Decision()
    }

    /**
     * Determines whether to send a notification based on state transition.
     *
     * Rules:
     * - OK -> FAIL: Notify immediately
     * - FAIL -> FAIL (same signature within reminder interval): No notify
     * - FAIL -> FAIL (different signature): Notify immediately
     * - FAIL -> FAIL (same signature but reminder interval elapsed): Notify as reminder
     * - FAIL -> OK (with notifyOnRecovery = true): Notify recovery
     * - FAIL -> OK (with notifyOnRecovery = false): No notify
     * - OK -> OK: No notify
     *
     * @param previousStatus Previous health status (null if first run)
     * @param currentResult Current health check result
     * @param previousFailureSignature Previous failure signature (null if last was OK)
     * @param lastNotifiedAt Timestamp of last notification (null if never notified)
     * @param notifyOnRecovery Whether to notify on recovery
     * @param currentTimeMs Current timestamp for time comparisons
     * @param reminderIntervalMs Minimum interval between reminder notifications
     * @return Decision on whether and what to notify
     */
    fun shouldNotify(
        previousStatus: MonitorHealthStatus?,
        currentResult: HealthCheckResult,
        previousFailureSignature: String?,
        lastNotifiedAt: Long?,
        notifyOnRecovery: Boolean,
        currentTimeMs: Long = System.currentTimeMillis(),
        reminderIntervalMs: Long = DevHealthMonitorStateStore.REMINDER_INTERVAL_MS,
    ): Decision {
        val currentStatus = currentResult.status
        val currentSignature = currentResult.failureSignature

        return when {
            // First run - only notify if it's a failure
            previousStatus == null -> {
                if (currentStatus == MonitorHealthStatus.FAIL) {
                    Decision.NotifyFailure(currentResult.failures.firstOrNull()?.failureReason ?: "Health check failed")
                } else {
                    Decision.NoNotification
                }
            }

            // OK -> FAIL: Notify immediately
            previousStatus == MonitorHealthStatus.OK && currentStatus == MonitorHealthStatus.FAIL -> {
                Decision.NotifyFailure(currentResult.failures.firstOrNull()?.failureReason ?: "Health check failed")
            }

            // FAIL -> OK: Notify recovery if enabled
            previousStatus == MonitorHealthStatus.FAIL && currentStatus == MonitorHealthStatus.OK -> {
                if (notifyOnRecovery) {
                    Decision.NotifyRecovery
                } else {
                    Decision.NoNotification
                }
            }

            // FAIL -> FAIL: Check signature and rate limiting
            previousStatus == MonitorHealthStatus.FAIL && currentStatus == MonitorHealthStatus.FAIL -> {
                // Different failure signature - notify immediately
                if (previousFailureSignature != null && currentSignature != previousFailureSignature) {
                    Decision.NotifyFailure(currentResult.failures.firstOrNull()?.failureReason ?: "Health check failed")
                }
                // Same signature - check rate limit
                else if (shouldSendReminder(lastNotifiedAt, currentTimeMs, reminderIntervalMs)) {
                    Decision.NotifyFailure(currentResult.failures.firstOrNull()?.failureReason ?: "Health check still failing")
                } else {
                    Decision.NoNotification
                }
            }

            // OK -> OK: No notification needed
            else -> {
                Decision.NoNotification
            }
        }
    }

    /**
     * Checks if enough time has passed since the last notification to send a reminder.
     */
    private fun shouldSendReminder(
        lastNotifiedAt: Long?,
        currentTimeMs: Long,
        reminderIntervalMs: Long,
    ): Boolean {
        if (lastNotifiedAt == null) return true
        return (currentTimeMs - lastNotifiedAt) >= reminderIntervalMs
    }
}
