package com.scanium.app.telemetry

import com.scanium.telemetry.ports.SpanContext

/**
 * Thread-local storage for active trace context.
 *
 * This allows the OkHttp interceptor to access the current span without explicit passing
 * through method parameters. When a span is set as active, HTTP requests made on that
 * thread will automatically include W3C trace context headers.
 *
 * ***REMOVED******REMOVED*** Usage Pattern
 * ```kotlin
 * val span = telemetry.beginSpan("operation.name")
 * TraceContext.setActiveSpan(span)
 * try {
 *     // Make HTTP calls - interceptor will inject trace headers
 *     repository.callApi()
 * } finally {
 *     TraceContext.clearActiveSpan()
 *     span.end()
 * }
 * ```
 *
 * ***REMOVED******REMOVED*** Alternative: Explicit Parameter Passing
 * Instead of ThreadLocal, spans could be passed explicitly to repositories.
 * ThreadLocal was chosen for:
 * - Simplicity: No need to change repository method signatures
 * - Backwards compatibility: Existing code continues to work
 * - Transparency: Trace propagation happens automatically
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * Each thread maintains its own active span. This is safe for concurrent operations
 * but requires proper cleanup to avoid span leaks.
 */
object TraceContext {
    private val activeSpan = ThreadLocal<SpanContext?>()

    /**
     * Sets the active span for the current thread.
     *
     * HTTP requests made after calling this will include W3C trace context headers
     * derived from this span (traceparent: 00-{traceId}-{spanId}-{flags}).
     *
     * Important: Always call [clearActiveSpan] when done to prevent memory leaks.
     *
     * @param span The span to set as active
     */
    fun setActiveSpan(span: SpanContext) {
        activeSpan.set(span)
    }

    /**
     * Gets the active span for the current thread.
     *
     * @return The active span, or null if no span is active
     */
    fun getActiveSpan(): SpanContext? {
        return activeSpan.get()
    }

    /**
     * Clears the active span for the current thread.
     *
     * Always call this in a finally block to prevent memory leaks, especially
     * in thread pool scenarios where threads are reused.
     */
    fun clearActiveSpan() {
        activeSpan.remove()
    }

    /**
     * Executes a block with an active span context.
     *
     * The span is automatically set before the block executes and cleared
     * after (even if an exception occurs). The span is NOT automatically
     * ended - the caller is responsible for calling span.end().
     *
     * @param span The span to set as active
     * @param block The block to execute with the span active
     * @return The result of the block
     */
    inline fun <T> withSpan(
        span: SpanContext,
        block: () -> T,
    ): T {
        setActiveSpan(span)
        return try {
            block()
        } finally {
            clearActiveSpan()
        }
    }
}
