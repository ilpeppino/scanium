package com.scanium.telemetry.ports

/**
 * Represents an active tracing span.
 *
 * Spans are used to track the duration and context of operations.
 * Always call [end] when the operation completes, ideally in a try-finally block.
 */
interface SpanContext {
    /**
     * Ends the span, recording its duration and any final attributes.
     *
     * @param additionalAttributes Optional attributes to add when ending the span
     */
    fun end(additionalAttributes: Map<String, String> = emptyMap())

    /**
     * Adds an attribute to the active span.
     *
     * @param key Attribute key
     * @param value Attribute value
     */
    fun setAttribute(key: String, value: String)

    /**
     * Records an error/exception within this span.
     *
     * @param error The error message
     * @param attributes Optional error context attributes
     */
    fun recordError(error: String, attributes: Map<String, String> = emptyMap())
}

/**
 * Port interface for emitting distributed tracing telemetry.
 *
 * Implementations of this port are responsible for:
 * - Creating and managing trace spans
 * - Propagating trace context across operations
 * - Forwarding spans to backend systems (OpenTelemetry, Jaeger, custom)
 *
 * The Telemetry facade guarantees that:
 * - Attributes are already sanitized (PII removed)
 * - Required attributes are present
 * - Span names follow naming conventions
 */
interface TracePort {
    /**
     * Begins a new tracing span.
     *
     * @param name Span name (e.g., "scan.process_frame")
     * @param attributes Sanitized attributes for this span
     * @return A SpanContext that must be ended when the operation completes
     */
    fun beginSpan(name: String, attributes: Map<String, String> = emptyMap()): SpanContext
}
