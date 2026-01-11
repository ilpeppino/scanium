package com.scanium.app.monitoring

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.devHealthMonitorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dev_health_monitor",
)

/**
 * Persistent state store for the DEV health monitor.
 * Stores last check results, notification timestamps, and user preferences.
 *
 * DEV-only: This class should only be used when FeatureFlags.isDevBuild is true.
 */
class DevHealthMonitorStateStore(private val context: Context) {
    companion object {
        // State keys
        private val KEY_LAST_STATUS = stringPreferencesKey("last_status")
        private val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        private val KEY_LAST_NOTIFIED_AT = longPreferencesKey("last_notified_at")
        private val KEY_LAST_FAILURE_SIGNATURE = stringPreferencesKey("last_failure_signature")
        private val KEY_LAST_FAILURE_SUMMARY = stringPreferencesKey("last_failure_summary")

        // Configuration keys
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BASE_URL_OVERRIDE = stringPreferencesKey("base_url_override")
        private val KEY_NOTIFY_ON_RECOVERY = booleanPreferencesKey("notify_on_recovery")

        // Rate limit: re-notify at most every 6 hours if failure signature unchanged
        const val REMINDER_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 hours
    }

    // =========================================================================
    // State Flows
    // =========================================================================

    /**
     * Flow of the current monitor state.
     */
    val stateFlow: Flow<MonitorState> = context.devHealthMonitorDataStore.data.map { prefs ->
        MonitorState(
            lastStatus = prefs[KEY_LAST_STATUS]?.let {
                runCatching { MonitorHealthStatus.valueOf(it) }.getOrNull()
            },
            lastCheckedAt = prefs[KEY_LAST_CHECKED_AT],
            lastNotifiedAt = prefs[KEY_LAST_NOTIFIED_AT],
            lastFailureSignature = prefs[KEY_LAST_FAILURE_SIGNATURE],
            lastFailureSummary = prefs[KEY_LAST_FAILURE_SUMMARY],
        )
    }

    /**
     * Flow of the current configuration.
     */
    val configFlow: Flow<MonitorConfig> = context.devHealthMonitorDataStore.data.map { prefs ->
        MonitorConfig(
            enabled = prefs[KEY_ENABLED] ?: FeatureFlags.isDevBuild,
            baseUrlOverride = prefs[KEY_BASE_URL_OVERRIDE],
            notifyOnRecovery = prefs[KEY_NOTIFY_ON_RECOVERY] ?: true,
        )
    }

    /**
     * Get the current state synchronously.
     */
    suspend fun getState(): MonitorState = stateFlow.first()

    /**
     * Get the current configuration synchronously.
     */
    suspend fun getConfig(): MonitorConfig = configFlow.first()

    // =========================================================================
    // State Updates
    // =========================================================================

    /**
     * Update the last check result.
     */
    suspend fun updateLastResult(result: HealthCheckResult) {
        context.devHealthMonitorDataStore.edit { prefs ->
            prefs[KEY_LAST_STATUS] = result.status.name
            prefs[KEY_LAST_CHECKED_AT] = result.checkedAt

            if (result.status == MonitorHealthStatus.FAIL) {
                prefs[KEY_LAST_FAILURE_SIGNATURE] = result.failureSignature
                prefs[KEY_LAST_FAILURE_SUMMARY] = result.failures.firstOrNull()?.failureReason ?: "Unknown failure"
            } else {
                prefs.remove(KEY_LAST_FAILURE_SIGNATURE)
                prefs.remove(KEY_LAST_FAILURE_SUMMARY)
            }
        }
    }

    /**
     * Update the last notification timestamp.
     */
    suspend fun updateLastNotifiedAt(timestamp: Long) {
        context.devHealthMonitorDataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFIED_AT] = timestamp
        }
    }

    // =========================================================================
    // Configuration Updates
    // =========================================================================

    /**
     * Enable or disable the health monitor.
     */
    suspend fun setEnabled(enabled: Boolean) {
        context.devHealthMonitorDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    /**
     * Set a custom base URL for health checks.
     * Pass null to use the default BuildConfig URL.
     */
    suspend fun setBaseUrlOverride(url: String?) {
        context.devHealthMonitorDataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(KEY_BASE_URL_OVERRIDE)
            } else {
                prefs[KEY_BASE_URL_OVERRIDE] = url.trim()
            }
        }
    }

    /**
     * Set whether to notify on recovery.
     */
    suspend fun setNotifyOnRecovery(notify: Boolean) {
        context.devHealthMonitorDataStore.edit { prefs ->
            prefs[KEY_NOTIFY_ON_RECOVERY] = notify
        }
    }

    /**
     * Clear all state (for testing or reset).
     */
    suspend fun clearAll() {
        context.devHealthMonitorDataStore.edit { it.clear() }
    }

    /**
     * Get the effective base URL (override or BuildConfig default).
     */
    fun getEffectiveBaseUrl(config: MonitorConfig): String {
        return config.baseUrlOverride?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SCANIUM_API_BASE_URL
    }

    // =========================================================================
    // Data Classes
    // =========================================================================

    /**
     * Current state of the health monitor.
     */
    data class MonitorState(
        val lastStatus: MonitorHealthStatus?,
        val lastCheckedAt: Long?,
        val lastNotifiedAt: Long?,
        val lastFailureSignature: String?,
        val lastFailureSummary: String?,
    ) {
        val hasEverRun: Boolean get() = lastCheckedAt != null
    }

    /**
     * Configuration for the health monitor.
     */
    data class MonitorConfig(
        val enabled: Boolean,
        val baseUrlOverride: String?,
        val notifyOnRecovery: Boolean,
    )
}
