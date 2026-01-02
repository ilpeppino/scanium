package com.scanium.telemetry.facade

/**
 * Provider for default telemetry attributes that are automatically added to all events.
 *
 * This typically includes required attributes like platform, app_version, build, env, and session_id.
 * Platform-specific implementations should provide these values.
 *
 * Example implementation:
 * ```kotlin
 * class AndroidDefaultAttributes(
 *     private val context: Context,
 *     private val sessionManager: SessionManager
 * ) : DefaultAttributesProvider {
 *     override fun getDefaultAttributes(): Map<String, String> = mapOf(
 *         TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
 *         TelemetryEvent.ATTR_APP_VERSION to BuildConfig.VERSION_NAME,
 *         TelemetryEvent.ATTR_BUILD to BuildConfig.VERSION_CODE.toString(),
 *         TelemetryEvent.ATTR_ENV to getEnvironment(),
 *         TelemetryEvent.ATTR_SESSION_ID to sessionManager.currentSessionId
 *     )
 * }
 * ```
 */
interface DefaultAttributesProvider {
    /**
     * Returns the default attributes to be included in all telemetry events.
     *
     * These attributes should include at minimum:
     * - platform: "android" or "ios"
     * - app_version: Semantic version (e.g., "1.2.3")
     * - build: Build number (e.g., "42")
     * - env: Environment ("dev", "staging", "prod")
     * - session_id: Unique session identifier
     * - data_region: Data residency region ("EU", "US")
     *
     * @return Map of attribute key-value pairs
     */
    fun getDefaultAttributes(): Map<String, String>
}
