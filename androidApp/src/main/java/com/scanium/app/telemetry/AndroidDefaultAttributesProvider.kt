package com.scanium.app.telemetry

import com.scanium.app.BuildConfig
import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.facade.DefaultAttributesProvider

/**
 * Android implementation of [DefaultAttributesProvider].
 *
 * Provides platform-specific default attributes for all telemetry events:
 * - platform: "android"
 * - app_version: From BuildConfig.VERSION_NAME
 * - build: From BuildConfig.VERSION_CODE
 * - env: Based on build type (debug = "dev", release = "prod")
 * - session_id: From CorrelationIds.currentClassificationSessionId()
 * - data_region: Data residency region ("EU" or "US")
 */
class AndroidDefaultAttributesProvider(
    private val dataRegion: String,
) : DefaultAttributesProvider {
    override fun getDefaultAttributes(): Map<String, String> {
        return mapOf(
            TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
            TelemetryEvent.ATTR_APP_VERSION to BuildConfig.VERSION_NAME,
            TelemetryEvent.ATTR_BUILD to BuildConfig.VERSION_CODE.toString(),
            TelemetryEvent.ATTR_ENV to getEnvironment(),
            TelemetryEvent.ATTR_SESSION_ID to getSessionId(),
            TelemetryEvent.ATTR_DATA_REGION to dataRegion,
        )
    }

    private fun getEnvironment(): String {
        return if (BuildConfig.DEBUG) {
            TelemetryEvent.ENV_DEV
        } else {
            TelemetryEvent.ENV_PROD
        }
    }

    private fun getSessionId(): String {
        // Use classification session ID from CorrelationIds
        return com.scanium.app.logging.CorrelationIds.currentClassificationSessionId()
    }
}
