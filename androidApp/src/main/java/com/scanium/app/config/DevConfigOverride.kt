package com.scanium.app.config

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.devConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dev_config_override",
)

/**
 * Developer configuration override for debug builds.
 *
 * Provides an EXPLICIT way to override the backend URL for development/testing purposes.
 * This is separate from BuildConfig and is stored in DataStore.
 *
 * ***REMOVED******REMOVED*** Key Behaviors:
 * - **Debug only**: All overrides are ignored in non-debug builds
 * - **Explicit opt-in**: Overrides must be explicitly set, not auto-applied
 * - **Versioned**: Tracks when override was set to detect stale configs
 * - **Observable**: Provides flows for UI to react to changes
 *
 * ***REMOVED******REMOVED*** Usage:
 * ```kotlin
 * // Set an override
 * devConfigOverride.setBaseUrlOverride("https://staging.example.com")
 *
 * // Check if override is active
 * val isOverridden = devConfigOverride.isBaseUrlOverridden()
 *
 * // Get effective URL (override if set, otherwise BuildConfig)
 * val url = devConfigOverride.getEffectiveBaseUrl()
 *
 * // Reset to BuildConfig default
 * devConfigOverride.clearBaseUrlOverride()
 * ```
 *
 * ***REMOVED******REMOVED*** ADB Commands:
 * ```bash
 * ***REMOVED*** View current override
 * adb shell "run-as com.scanium.app.dev cat /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"
 *
 * ***REMOVED*** Clear all dev config overrides
 * adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"
 * ```
 */
class DevConfigOverride(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DevConfigOverride"

        private val KEY_BASE_URL_OVERRIDE = stringPreferencesKey("base_url_override")
        private val KEY_OVERRIDE_SET_AT = longPreferencesKey("override_set_at")
        private val KEY_OVERRIDE_APP_VERSION = stringPreferencesKey("override_app_version")

        // Stale threshold: 30 days
        private const val STALE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000
    }

    /**
     * Flow of the current base URL override status.
     */
    val overrideStatusFlow: Flow<OverrideStatus> =
        context.devConfigDataStore.data.map { prefs ->
            if (!BuildConfig.DEBUG) {
                return@map OverrideStatus.Disabled
            }

            val overrideUrl = prefs[KEY_BASE_URL_OVERRIDE]
            val setAt = prefs[KEY_OVERRIDE_SET_AT] ?: 0L
            val appVersion = prefs[KEY_OVERRIDE_APP_VERSION]

            when {
                overrideUrl.isNullOrBlank() -> {
                    OverrideStatus.UsingDefault(BuildConfig.SCANIUM_API_BASE_URL)
                }

                else -> {
                    val isStale = isOverrideStale(setAt)
                    val isVersionMismatch = appVersion != BuildConfig.VERSION_NAME
                    OverrideStatus.Overridden(
                        url = overrideUrl,
                        defaultUrl = BuildConfig.SCANIUM_API_BASE_URL,
                        setAt = setAt,
                        isStale = isStale,
                        isVersionMismatch = isVersionMismatch,
                    )
                }
            }
        }

    /**
     * Get the effective base URL (override if set, otherwise BuildConfig).
     * Logs a warning in debug builds if override is active.
     */
    fun getEffectiveBaseUrl(): String {
        if (!BuildConfig.DEBUG) {
            return BuildConfig.SCANIUM_API_BASE_URL
        }

        return runBlocking {
            val prefs = context.devConfigDataStore.data.first()
            val override = prefs[KEY_BASE_URL_OVERRIDE]?.takeIf { it.isNotBlank() }

            if (override != null) {
                val defaultUrl = BuildConfig.SCANIUM_API_BASE_URL
                if (override != defaultUrl) {
                    Log.w(
                        TAG,
                        "Base URL override active: $override (BuildConfig: $defaultUrl)",
                    )
                }
                override
            } else {
                BuildConfig.SCANIUM_API_BASE_URL
            }
        }
    }

    /**
     * Check if an override is currently active.
     */
    fun isBaseUrlOverridden(): Boolean {
        if (!BuildConfig.DEBUG) return false

        return runBlocking {
            val prefs = context.devConfigDataStore.data.first()
            !prefs[KEY_BASE_URL_OVERRIDE].isNullOrBlank()
        }
    }

    /**
     * Set a base URL override.
     * Only works in debug builds.
     */
    suspend fun setBaseUrlOverride(url: String) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "Cannot set override in non-debug build")
            return
        }

        if (url.isBlank()) {
            clearBaseUrlOverride()
            return
        }

        context.devConfigDataStore.edit { prefs ->
            prefs[KEY_BASE_URL_OVERRIDE] = url.trim()
            prefs[KEY_OVERRIDE_SET_AT] = System.currentTimeMillis()
            prefs[KEY_OVERRIDE_APP_VERSION] = BuildConfig.VERSION_NAME
        }

        Log.i(TAG, "Base URL override set: $url")
    }

    /**
     * Clear the base URL override, reverting to BuildConfig.
     */
    suspend fun clearBaseUrlOverride() {
        if (!BuildConfig.DEBUG) return

        context.devConfigDataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL_OVERRIDE)
            prefs.remove(KEY_OVERRIDE_SET_AT)
            prefs.remove(KEY_OVERRIDE_APP_VERSION)
        }

        Log.i(TAG, "Base URL override cleared, using BuildConfig: ${BuildConfig.SCANIUM_API_BASE_URL}")
    }

    /**
     * Clear override if it's stale (set too long ago or from different app version).
     * Call this on app startup to auto-clean outdated configs.
     */
    suspend fun clearStaleOverrides() {
        if (!BuildConfig.DEBUG) return

        val prefs = context.devConfigDataStore.data.first()
        val setAt = prefs[KEY_OVERRIDE_SET_AT] ?: return
        val appVersion = prefs[KEY_OVERRIDE_APP_VERSION]
        val overrideUrl = prefs[KEY_BASE_URL_OVERRIDE] ?: return

        val isStale = isOverrideStale(setAt)
        val isVersionMismatch = appVersion != null && appVersion != BuildConfig.VERSION_NAME

        if (isStale || isVersionMismatch) {
            Log.w(
                TAG,
                "Clearing stale override: $overrideUrl (stale=$isStale, versionMismatch=$isVersionMismatch)",
            )
            clearBaseUrlOverride()
        }
    }

    private fun isOverrideStale(setAt: Long): Boolean = System.currentTimeMillis() - setAt > STALE_THRESHOLD_MS

    /**
     * Status of the base URL override.
     */
    sealed class OverrideStatus {
        /** Non-debug build, overrides are disabled */
        data object Disabled : OverrideStatus()

        /** Using BuildConfig default */
        data class UsingDefault(
            val url: String,
        ) : OverrideStatus()

        /** Override is active */
        data class Overridden(
            val url: String,
            val defaultUrl: String,
            val setAt: Long,
            val isStale: Boolean,
            val isVersionMismatch: Boolean,
        ) : OverrideStatus() {
            val hasWarning: Boolean = isStale || isVersionMismatch || url != defaultUrl
        }
    }
}
