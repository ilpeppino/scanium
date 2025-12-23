package com.scanium.telemetry.facade

import com.scanium.telemetry.AttributeSanitizer
import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
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
 *
 * ## Usage Example
 * ```kotlin
 * // Initialize (typically in platform-specific code)
 * val telemetry = Telemetry(
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
 * ## Required Attributes
 * The facade enforces that all events include required attributes:
 * - platform, app_version, build, env, session_id
 *
 * These are typically provided by [DefaultAttributesProvider] and merged with user attributes.
 * If required attributes are missing after merging, an [IllegalStateException] is thrown.
 *
 * @param defaultAttributesProvider Provides platform-specific default attributes
 * @param logPort Port for emitting log events (optional, uses NoOp if not provided)
 * @param metricPort Port for emitting metrics (optional, uses NoOp if not provided)
 * @param tracePort Port for emitting traces (optional, uses NoOp if not provided)
 */
class Telemetry(
    private val defaultAttributesProvider: DefaultAttributesProvider,
    private val logPort: LogPort,
    private val metricPort: MetricPort,
    private val tracePort: TracePort
) {
    /**
     * Emits a telemetry event with automatic sanitization and attribute merging.
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
        val mergedAttributes = mergeAndSanitize(userAttributes)
        validateRequiredAttributes(mergedAttributes)

        val event = TelemetryEvent(
            name = name,
            severity = severity,
            timestamp = Clock.System.now(),
            attributes = mergedAttributes
        )

        logPort.emit(event)
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
