package com.scanium.telemetry.facade

import com.scanium.diagnostics.DiagnosticsPort
import com.scanium.telemetry.AttributeSanitizer
import com.scanium.telemetry.TelemetryConfig
import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import com.scanium.telemetry.ports.CrashPort
import com.scanium.telemetry.ports.LogPort
import com.scanium.telemetry.ports.MetricPort
import com.scanium.telemetry.ports.SpanContext
import com.scanium.telemetry.ports.TracePort
import kotlinx.datetime.Clock

/**
 * Main telemetry facade for emitting logs, metrics, and traces from shared code.
 *
 * This facade provides a clean API for shared business logic to emit telemetry without
 * depending on specific backend implementations. It handles:
 * - Automatic attribute sanitization (PII removal)
 * - Merging default attributes with user-provided attributes
 * - Enforcing required attribute presence (fail-fast)
 * - Event name validation
 * - Runtime filtering by severity level
 * - Enable/disable toggle
 *
 * ***REMOVED******REMOVED*** Usage Example
 * ```kotlin
 * // Initialize (typically in platform-specific code)
 * val config = TelemetryConfig.development()
 * val telemetry = Telemetry(
 *     config = config,
 *     defaultAttributesProvider = myDefaultAttributesProvider,
 *     logPort = myLogPort,
 *     metricPort = myMetricPort,
 *     tracePort = myTracePort
 * )
 *
 * // Use in shared code
 * telemetry.event("scan.started", TelemetrySeverity.INFO, mapOf(
 *     "scan_mode" to "continuous"
 * ))
 *
 * telemetry.counter("ml.classification_count", delta = 1)
 *
 * val span = telemetry.beginSpan("scan.process_frame")
 * try {
 *     // ... do work
 * } finally {
 *     span.end()
 * }
 * ```
 *
 * ***REMOVED******REMOVED*** Required Attributes
 * The facade enforces that all events include required attributes:
 * - platform, app_version, build, env, session_id, data_region
 *
 * These are typically provided by [DefaultAttributesProvider] and merged with user attributes.
 * If required attributes are missing after merging, an [IllegalStateException] is thrown.
 *
 * @param config Runtime configuration for telemetry behavior
 * @param defaultAttributesProvider Provides platform-specific default attributes
 * @param logPort Port for emitting log events (optional, uses NoOp if not provided)
 * @param metricPort Port for emitting metrics (optional, uses NoOp if not provided)
 * @param tracePort Port for emitting traces (optional, uses NoOp if not provided)
 * @param crashPort Port for crash reporting (optional, null if not provided). When provided,
 *                  WARN and ERROR events are automatically forwarded as breadcrumbs to crash reports.
 * @param diagnosticsPort Port for diagnostics breadcrumb collection (optional, null if not provided).
 *                        When provided, all events are automatically recorded as breadcrumbs for crash reports.
 */
class Telemetry(
    private val config: TelemetryConfig = TelemetryConfig(),
    private val defaultAttributesProvider: DefaultAttributesProvider,
    private val logPort: LogPort,
    private val metricPort: MetricPort,
    private val tracePort: TracePort,
    private val crashPort: CrashPort? = null,
    private val diagnosticsPort: DiagnosticsPort? = null
) {
    /**
     * Emits a telemetry event with automatic sanitization and attribute merging.
     *
     * Events are filtered based on configuration:
     * - If telemetry is disabled, event is dropped
     * - If severity is below minSeverity, event is dropped
     *
     * If a [crashPort] is configured, WARN and ERROR events are automatically forwarded
     * as breadcrumbs to crash reports. This helps provide context when crashes occur.
     *
     * @param name Event name (e.g., "scan.started")
     * @param severity Event severity level
     * @param userAttributes User-provided attributes (will be merged with defaults and sanitized)
     * @throws IllegalStateException if required attributes are missing after merging
     */
    fun event(
        name: String,
        severity: TelemetrySeverity,
        userAttributes: Map<String, String> = emptyMap()
    ) {
        // Filter by config: enabled and minSeverity
        if (!config.enabled) return
        if (severity.ordinal < config.minSeverity.ordinal) return

        val mergedAttributes = mergeAndSanitize(userAttributes)
        validateRequiredAttributes(mergedAttributes)

        val event = TelemetryEvent(
            name = name,
            severity = severity,
            timestamp = Clock.System.now(),
            attributes = mergedAttributes
        )

        logPort.emit(event)

        // Append to diagnostics buffer for crash-time attachment
        // All events are recorded (INFO, WARN, ERROR) for context
        diagnosticsPort?.appendBreadcrumb(event)

        // Forward WARN and ERROR events to crash reporting as breadcrumbs
        // This provides context for debugging crashes
        if (crashPort != null && (severity == TelemetrySeverity.WARN || severity == TelemetrySeverity.ERROR)) {
            crashPort.addBreadcrumb(
                message = name,
                attributes = filterRelevantAttributes(mergedAttributes)
            )
        }
    }

    /**
     * Filters attributes to keep only relevant ones for breadcrumbs.
     * Excludes common attributes that are already set as tags on crash reports.
     * This keeps breadcrumb payloads focused and small.
     */
    private fun filterRelevantAttributes(attributes: Map<String, String>): Map<String, String> {
        val excludeKeys = setOf(
            TelemetryEvent.ATTR_PLATFORM,
            TelemetryEvent.ATTR_APP_VERSION,
            TelemetryEvent.ATTR_BUILD,
            TelemetryEvent.ATTR_ENV,
            TelemetryEvent.ATTR_SESSION_ID
        )
        return attributes.filterKeys { it !in excludeKeys }
    }

    /**
     * Convenience method for INFO-level events.
     */
    fun info(name: String, userAttributes: Map<String, String> = emptyMap()) {
        event(name, TelemetrySeverity.INFO, userAttributes)
    }

    /**
     * Convenience method for WARN-level events.
     */
    fun warn(name: String, userAttributes: Map<String, String> = emptyMap()) {
        event(name, TelemetrySeverity.WARN, userAttributes)
    }

    /**
     * Convenience method for ERROR-level events.
     */
    fun error(name: String, userAttributes: Map<String, String> = emptyMap()) {
        event(name, TelemetrySeverity.ERROR, userAttributes)
    }

    /**
     * Increments a counter metric.
     *
     * @param name Metric name (e.g., "scan.items_detected")
     * @param delta Amount to increment (default: 1)
     * @param userAttributes User-provided attributes (will be merged with defaults and sanitized)
     */
    fun counter(
        name: String,
        delta: Long = 1,
        userAttributes: Map<String, String> = emptyMap()
    ) {
        if (!config.enabled) return
        val mergedAttributes = mergeAndSanitize(userAttributes)
        metricPort.counter(name, delta, mergedAttributes)
    }

    /**
     * Records a timer/duration metric.
     *
     * @param name Metric name (e.g., "scan.duration_ms")
     * @param millis Duration in milliseconds
     * @param userAttributes User-provided attributes (will be merged with defaults and sanitized)
     */
    fun timer(
        name: String,
        millis: Long,
        userAttributes: Map<String, String> = emptyMap()
    ) {
        if (!config.enabled) return
        val mergedAttributes = mergeAndSanitize(userAttributes)
        metricPort.timer(name, millis, mergedAttributes)
    }

    /**
     * Records a gauge metric.
     *
     * @param name Metric name (e.g., "storage.items_count")
     * @param value Current value
     * @param userAttributes User-provided attributes (will be merged with defaults and sanitized)
     */
    fun gauge(
        name: String,
        value: Double,
        userAttributes: Map<String, String> = emptyMap()
    ) {
        if (!config.enabled) return
        val mergedAttributes = mergeAndSanitize(userAttributes)
        metricPort.gauge(name, value, mergedAttributes)
    }

    /**
     * Begins a new tracing span.
     *
     * @param name Span name (e.g., "scan.process_frame")
     * @param userAttributes User-provided attributes (will be merged with defaults and sanitized)
     * @return SpanContext that must be ended when the operation completes
     */
    fun beginSpan(
        name: String,
        userAttributes: Map<String, String> = emptyMap()
    ): SpanContext {
        if (!config.enabled) return tracePort.beginSpan(name, emptyMap()) // Return no-op span
        val mergedAttributes = mergeAndSanitize(userAttributes)
        return tracePort.beginSpan(name, mergedAttributes)
    }

    /**
     * Executes a block within a span, automatically ending the span when complete.
     *
     * @param name Span name
     * @param userAttributes User-provided attributes
     * @param block Code to execute within the span
     * @return Result of the block
     */
    inline fun <T> span(
        name: String,
        userAttributes: Map<String, String> = emptyMap(),
        block: (SpanContext) -> T
    ): T {
        val span = beginSpan(name, userAttributes)
        return try {
            block(span)
        } catch (e: Exception) {
            span.recordError(e.message ?: "Unknown error")
            throw e
        } finally {
            span.end()
        }
    }

    /**
     * Merges user attributes with default attributes and sanitizes the result.
     * User attributes take precedence over defaults.
     */
    private fun mergeAndSanitize(userAttributes: Map<String, String>): Map<String, String> {
        val defaultAttributes = defaultAttributesProvider.getDefaultAttributes()
        val merged = defaultAttributes + userAttributes  // User attrs override defaults
        return AttributeSanitizer.sanitize(merged)
    }

    /**
     * Validates that required attributes are present.
     * Throws IllegalStateException if any are missing.
     */
    private fun validateRequiredAttributes(attributes: Map<String, String>) {
        val missing = AttributeSanitizer.validateRequiredAttributes(attributes)
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Missing required telemetry attributes: ${missing.joinToString(", ")}. " +
                "Ensure DefaultAttributesProvider includes all required fields."
            )
        }
    }
}
