package com.scanium.telemetry.ports

/**
 * Port interface for crash reporting and error tracking.
 *
 * Implementations of this port are responsible for:
 * - Capturing unhandled exceptions and errors
 * - Setting tags and context for error reports (platform, version, session, etc.)
 * - Recording breadcrumbs for debugging crash context
 * - Forwarding crash data to backend systems (Sentry, Crashlytics, custom backend)
 *
 * This port keeps the shared code vendor-neutral while allowing platform-specific
 * implementations to integrate with various crash reporting services.
 *
 * ## Usage in Application Initialization
 * ```kotlin
 * class Application {
 *     fun onCreate() {
 *         val crashPort = AndroidCrashPortAdapter(sentryDsn)
 *
 *         // Set required tags
 *         crashPort.setTag("platform", "android")
 *         crashPort.setTag("app_version", "1.0.0")
 *         crashPort.setTag("build", "42")
 *         crashPort.setTag("env", "prod")
 *         crashPort.setTag("session_id", sessionId)
 *     }
 * }
 * ```
 *
 * ## Usage with Telemetry Bridge
 * ```kotlin
 * class TelemetryCrashBridge(
 *     private val crashPort: CrashPort,
 *     private val telemetry: Telemetry
 * ) {
 *     fun bridgeTelemetryToCrashReports() {
 *         telemetry.subscribe { event ->
 *             if (event.severity >= TelemetrySeverity.WARN) {
 *                 crashPort.addBreadcrumb(
 *                     message = event.name,
 *                     attributes = event.attributes
 *                 )
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## Usage for Manual Error Capture
 * ```kotlin
 * try {
 *     riskyOperation()
 * } catch (e: Exception) {
 *     crashPort.captureException(
 *         throwable = e,
 *         attributes = mapOf(
 *             "operation" to "risky_op",
 *             "user_action" to "scan"
 *         )
 *     )
 * }
 * ```
 */
interface CrashPort {
    /**
     * Sets a tag that will be attached to all crash reports.
     *
     * Tags are key-value pairs used for filtering and grouping crash reports.
     * Common tags include: platform, app_version, build, env, session_id, etc.
     *
     * @param key The tag key (e.g., "platform", "app_version")
     * @param value The tag value (e.g., "android", "1.0.0")
     */
    fun setTag(key: String, value: String)

    /**
     * Adds a breadcrumb that will be attached to crash reports.
     *
     * Breadcrumbs are timestamped events that provide context about what happened
     * before a crash occurred. They help developers understand the sequence of events
     * leading up to an error.
     *
     * Implementations should:
     * - Automatically timestamp breadcrumbs
     * - Store breadcrumbs in a ring buffer (bounded memory)
     * - Include breadcrumbs in all subsequent crash reports
     *
     * @param message A concise description of the event (e.g., "User started scan", "ML classification completed")
     * @param attributes Additional key-value context for the breadcrumb
     */
    fun addBreadcrumb(message: String, attributes: Map<String, String> = emptyMap())

    /**
     * Captures and reports an exception with optional context.
     *
     * This method should be called for:
     * - Handled exceptions that represent errors worth tracking
     * - Unexpected errors that don't crash the app
     * - Manual error reporting
     *
     * Note: Unhandled exceptions (crashes) are typically captured automatically
     * by the crash reporting SDK.
     *
     * @param throwable The exception to report
     * @param attributes Additional key-value context for the error report
     */
    fun captureException(throwable: Throwable, attributes: Map<String, String> = emptyMap())
}
