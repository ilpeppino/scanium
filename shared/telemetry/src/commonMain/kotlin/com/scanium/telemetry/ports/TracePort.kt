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
    fun setAttribute(
        key: String,
        value: String,
    )

    /**
     * Records an error/exception within this span.
     *
     * @param error The error message
     * @param attributes Optional error context attributes
     */
    fun recordError(
        error: String,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Gets the W3C trace ID for this span.
     * Format: 32 hex characters (16 bytes)
     *
     * @return The trace ID, or empty string if span is not sampled/active
     */
    fun getTraceId(): String

    /**
     * Gets the W3C span ID for this span.
     * Format: 16 hex characters (8 bytes)
     *
     * @return The span ID, or empty string if span is not sampled/active
     */
    fun getSpanId(): String

    /**
     * Gets the W3C trace flags for this span.
     * Format: 2 hex characters
     * - "01" = sampled
     * - "00" = not sampled
     *
     * @return The trace flags
     */
    fun getTraceFlags(): String
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
    fun beginSpan(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    ): SpanContext

    /**
     * Begins a child tracing span with a parent relationship.
     *
     * The child span will inherit the traceId from the parent and set
     * the parent's spanId as its parentSpanId. This creates a hierarchical
     * trace structure for distributed tracing.
     *
     * @param name Span name (e.g., "api.classify")
     * @param parent The parent span context
     * @param attributes Sanitized attributes for this span
     * @return A SpanContext that must be ended when the operation completes
     */
    fun beginChildSpan(
        name: String,
        parent: SpanContext,
        attributes: Map<String, String> = emptyMap(),
    ): SpanContext
}
