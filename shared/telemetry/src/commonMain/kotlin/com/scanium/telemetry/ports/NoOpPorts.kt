package com.scanium.telemetry.ports

import com.scanium.telemetry.TelemetryEvent

/**
 * No-op implementation of [LogPort] that discards all events.
 *
 * Useful for:
 * - Testing without actual telemetry backend
 * - Disabling telemetry in certain builds
 * - Default implementation when no backend is configured
 */
object NoOpLogPort : LogPort {
    override fun emit(event: TelemetryEvent) {
        // No-op: discard event
    }
}

/**
 * No-op implementation of [MetricPort] that discards all metrics.
 *
 * Useful for:
 * - Testing without actual metrics backend
 * - Disabling metrics in certain builds
 * - Default implementation when no backend is configured
 */
object NoOpMetricPort : MetricPort {
    override fun counter(name: String, delta: Long, attributes: Map<String, String>) {
        // No-op: discard metric
    }

    override fun timer(name: String, millis: Long, attributes: Map<String, String>) {
        // No-op: discard metric
    }

    override fun gauge(name: String, value: Double, attributes: Map<String, String>) {
        // No-op: discard metric
    }
}

/**
 * No-op implementation of [SpanContext].
 */
private object NoOpSpanContext : SpanContext {
    override fun end(additionalAttributes: Map<String, String>) {
        // No-op
    }

    override fun setAttribute(key: String, value: String) {
        // No-op
    }

    override fun recordError(error: String, attributes: Map<String, String>) {
        // No-op
    }
}

/**
 * No-op implementation of [TracePort] that creates no-op spans.
 *
 * Useful for:
 * - Testing without actual tracing backend
 * - Disabling tracing in certain builds
 * - Default implementation when no backend is configured
 */
object NoOpTracePort : TracePort {
    override fun beginSpan(name: String, attributes: Map<String, String>): SpanContext {
        return NoOpSpanContext
    }
}
