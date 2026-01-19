package com.scanium.app.startup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.scanium.app.startup.StartupGuard.Companion.CRASH_THRESHOLD
import com.scanium.app.startup.StartupGuard.Companion.CRASH_WINDOW_MS

/**
 * Crash-loop detection and safe mode guard for app startup.
 *
 * This class tracks startup attempts and detects crash-loops by monitoring
 * how quickly the app restarts after launch. If the app crashes repeatedly
 * within a short time window, it enters "safe mode" on the next launch.
 *
 * **How it works:**
 * 1. On each startup, [recordStartupAttempt] is called
 * 2. If the app crashes before [recordStartupSuccess], it's counted as a failed startup
 * 3. If [CRASH_THRESHOLD] consecutive failures occur within [CRASH_WINDOW_MS], safe mode activates
 * 4. In safe mode, optional startup tasks are skipped to maximize boot reliability
 * 5. After a successful startup, the crash counter resets
 *
 * **Usage:**
 * ```
 * // In Application.onCreate()
 * val startupGuard = StartupGuard.getInstance(this)
 * startupGuard.recordStartupAttempt()
 *
 * if (startupGuard.isSafeMode) {
 *     // Skip optional/risky initialization
 * } else {
 *     // Normal initialization
 * }
 *
 * // After first Activity is fully rendered
 * startupGuard.recordStartupSuccess()
 * ```
 *
 * **Safe mode behavior:**
 * - Disables non-essential warmups (ML models, caches)
 * - Skips preflight network checks
 * - Uses default settings instead of loading from storage
 * - In dev builds, shows diagnostic info
 */
class StartupGuard private constructor(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val TAG = "StartupGuard"
        private const val PREFS_NAME = "startup_guard"

        // Keys for SharedPreferences (using SharedPrefs for reliability over DataStore)
        private const val KEY_STARTUP_TIMESTAMPS = "startup_timestamps"
        private const val KEY_LAST_SUCCESSFUL_STARTUP = "last_successful_startup"
        private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"
        private const val KEY_CONSECUTIVE_CRASHES = "consecutive_crashes"

        // Crash detection thresholds

        /** Time window for crash detection (30 seconds) */
        const val CRASH_WINDOW_MS = 30_000L

        /** Number of consecutive crashes to trigger safe mode */
        const val CRASH_THRESHOLD = 3

        /** Minimum time between startup and crash to count as "quick crash" (5 seconds) */
        const val QUICK_CRASH_THRESHOLD_MS = 5_000L

        @Volatile
        private var instance: StartupGuard? = null

        /**
         * Get the singleton instance of StartupGuard.
         *
         * Uses SharedPreferences directly (not DataStore) for maximum reliability
         * since this needs to work even when DataStore is corrupted.
         */
        fun getInstance(context: Context): StartupGuard =
            instance ?: synchronized(this) {
                instance ?: StartupGuard(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }

        /**
         * Reset the singleton instance (for testing only).
         */
        @VisibleForTesting
        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }

    /**
     * Whether safe mode is currently active.
     *
     * In safe mode, the app should:
     * - Skip optional warmups
     * - Use default settings
     * - Avoid risky initialization that may have caused crashes
     */
    val isSafeMode: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE_ENABLED, false)

    /**
     * Number of consecutive crashes detected.
     */
    val consecutiveCrashes: Int
        get() = prefs.getInt(KEY_CONSECUTIVE_CRASHES, 0)

    /**
     * Timestamp of the last successful startup.
     */
    val lastSuccessfulStartup: Long
        get() = prefs.getLong(KEY_LAST_SUCCESSFUL_STARTUP, 0L)

    /**
     * Record a startup attempt. Call this at the beginning of Application.onCreate().
     *
     * This method:
     * 1. Records the current timestamp
     * 2. Checks if the previous startup failed (no success recorded)
     * 3. Increments crash counter if it was a quick crash
     * 4. Enables safe mode if crash threshold is reached
     */
    fun recordStartupAttempt() {
        val now = System.currentTimeMillis()
        val timestamps = getStartupTimestamps()

        // Check if last startup was a failure (success was never recorded)
        val lastTimestamp = timestamps.lastOrNull() ?: 0L
        val lastSuccess = lastSuccessfulStartup

        val wasQuickCrash =
            lastTimestamp > 0 &&
                lastTimestamp > lastSuccess &&
                (now - lastTimestamp) < CRASH_WINDOW_MS

        val newCrashCount =
            if (wasQuickCrash) {
                val count = consecutiveCrashes + 1
                Log.w(TAG, "Quick crash detected. Consecutive crashes: $count")
                count
            } else {
                // Reset if enough time passed (not a crash loop)
                0
            }

        // Update crash counter
        prefs
            .edit()
            .putInt(KEY_CONSECUTIVE_CRASHES, newCrashCount)
            .apply()

        // Enable safe mode if threshold reached
        if (newCrashCount >= CRASH_THRESHOLD) {
            Log.e(TAG, "Crash loop detected ($newCrashCount crashes). Enabling safe mode.")
            prefs
                .edit()
                .putBoolean(KEY_SAFE_MODE_ENABLED, true)
                .apply()
        }

        // Record this startup attempt
        val newTimestamps = (timestamps + now).takeLast(5) // Keep last 5
        saveStartupTimestamps(newTimestamps)

        Log.i(
            TAG,
            "Startup attempt recorded. Safe mode: $isSafeMode, " +
                "Consecutive crashes: $newCrashCount",
        )
    }

    /**
     * Record a successful startup. Call this after the first Activity is fully rendered.
     *
     * This resets the crash counter and disables safe mode for the next launch.
     */
    fun recordStartupSuccess() {
        val now = System.currentTimeMillis()

        prefs
            .edit()
            .putLong(KEY_LAST_SUCCESSFUL_STARTUP, now)
            .putInt(KEY_CONSECUTIVE_CRASHES, 0)
            .putBoolean(KEY_SAFE_MODE_ENABLED, false)
            .apply()

        Log.i(TAG, "Startup success recorded. Safe mode disabled for next launch.")
    }

    /**
     * Manually exit safe mode (e.g., from a "Try normal mode" button).
     *
     * Note: This takes effect on the next app launch, not immediately.
     */
    fun requestExitSafeMode() {
        prefs
            .edit()
            .putBoolean(KEY_SAFE_MODE_ENABLED, false)
            .putInt(KEY_CONSECUTIVE_CRASHES, 0)
            .apply()

        Log.i(TAG, "Safe mode exit requested. Will take effect on next launch.")
    }

    /**
     * Get diagnostic information for debugging crash loops.
     *
     * @return A map of diagnostic key-value pairs (safe to log, no secrets)
     */
    fun getDiagnostics(): Map<String, String> =
        mapOf(
            "safe_mode" to isSafeMode.toString(),
            "consecutive_crashes" to consecutiveCrashes.toString(),
            "last_successful_startup" to lastSuccessfulStartup.toString(),
            "startup_timestamps" to getStartupTimestamps().joinToString(","),
        )

    /**
     * Clear all startup guard state (for testing or manual reset).
     */
    @VisibleForTesting
    fun reset() {
        prefs.edit().clear().apply()
        Log.i(TAG, "StartupGuard state cleared")
    }

    private fun getStartupTimestamps(): List<Long> {
        val raw = prefs.getString(KEY_STARTUP_TIMESTAMPS, "") ?: ""
        return raw
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.toLongOrNull() }
    }

    private fun saveStartupTimestamps(timestamps: List<Long>) {
        prefs
            .edit()
            .putString(KEY_STARTUP_TIMESTAMPS, timestamps.joinToString(","))
            .apply()
    }
}
