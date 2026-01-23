package com.scanium.app.telemetry

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that injects W3C Trace Context headers into HTTP requests.
 *
 * ## W3C Trace Context Format
 * This interceptor adds the `traceparent` header to outgoing HTTP requests:
 * ```
 * traceparent: 00-{traceId}-{spanId}-{flags}
 * ```
 *
 * Where:
 * - **Version**: `00` (W3C Trace Context version 00)
 * - **TraceId**: 32 hex characters (16 bytes) - Identifies the entire trace
 * - **SpanId**: 16 hex characters (8 bytes) - Identifies this specific operation
 * - **Flags**: 2 hex characters - `01` for sampled, `00` for not sampled
 *
 * ## Example
 * ```
 * traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 * ```
 *
 * ## Usage
 * Add this interceptor to your OkHttp client:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(TraceContextInterceptor())
 *     .build()
 * ```
 *
 * The interceptor reads the active span from [TraceContext] and only injects
 * headers when a span is active. If no span is active (or the span is not sampled),
 * no headers are added.
 *
 * ## Distributed Tracing Flow
 * 1. Mobile app creates a root span for an operation
 * 2. Mobile sets the span as active via [TraceContext.setActiveSpan]
 * 3. Mobile makes HTTP request → This interceptor injects `traceparent` header
 * 4. Backend receives request → Extracts `traceparent` → Creates child span
 * 5. Backend calls external service → Propagates `traceparent` → Creates grandchild span
 * 6. All spans share the same traceId, forming a complete trace tree
 *
 * ## Compatibility
 * - **Request Signing**: Works with HMAC request signing (RequestSigner)
 * - **Correlation IDs**: Works alongside existing X-Scanium-Correlation-Id headers
 * - **Privacy**: Respects telemetry opt-out (no headers when opt-out enabled)
 *
 * @param spanProvider Function that returns the current active span (defaults to TraceContext)
 */
class TraceContextInterceptor(
    private val spanProvider: () -> com.scanium.telemetry.ports.SpanContext? = { TraceContext.getActiveSpan() },
) : Interceptor {
    companion object {
        /**
         * W3C traceparent header name.
         * See: https://www.w3.org/TR/trace-context/#traceparent-header
         */
        private const val TRACEPARENT_HEADER = "traceparent"

        /**
         * W3C Trace Context version (currently only version 00 is defined).
         */
        private const val W3C_VERSION = "00"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val span = spanProvider()

        // Only inject traceparent if we have an active span
        if (span == null || span.getTraceId().isEmpty()) {
            return chain.proceed(request)
        }

        val traceId = span.getTraceId()
        val spanId = span.getSpanId()
        val flags = span.getTraceFlags()

        // Build W3C traceparent header
        // Format: version-traceId-spanId-flags
        // Example: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
        val traceparent = "$W3C_VERSION-$traceId-$spanId-$flags"

        // Add traceparent header to request
        val newRequest =
            request
                .newBuilder()
                .header(TRACEPARENT_HEADER, traceparent)
                .build()

        return chain.proceed(newRequest)
    }
}
