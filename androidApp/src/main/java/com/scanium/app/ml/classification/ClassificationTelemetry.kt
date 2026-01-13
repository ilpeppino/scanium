package com.scanium.app.ml.classification

import com.scanium.app.telemetry.TraceContext
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.SpanContext

/**
 * Telemetry helper for classification operations.
 *
 * This class handles:
 * - Span creation and lifecycle management
 * - TraceContext integration for distributed tracing
 * - Metric recording (timers, counters)
 * - Error tracking in spans
 * - Success/failure attribution
 *
 * Separated from CloudClassifier to enable:
 * - Clean separation of concerns (telemetry vs. API vs. coordination)
 * - Easy testing of classification logic without telemetry dependencies
 * - Consistent telemetry patterns across classification modes
 *
 * @param telemetry Telemetry facade for recording metrics and spans
 * @param domainPackId Domain pack ID for span tagging
 */
class ClassificationTelemetry(
    private val telemetry: Telemetry?,
    private val domainPackId: String,
) {
    /**
     * Begin a classification span for the API call.
     *
     * If a parent span is active in TraceContext, creates a child span.
     * Otherwise, creates a root span.
     *
     * Automatically sets the created span as active in TraceContext for
     * HTTP header injection.
     *
     * @return ClassificationSpan wrapper or null if telemetry is disabled
     */
    fun beginClassificationSpan(): ClassificationSpan? {
        if (telemetry == null) return null

        val parentSpan = TraceContext.getActiveSpan()
        val span =
            if (parentSpan != null) {
                telemetry.beginChildSpan(
                    "api.classify",
                    parentSpan,
                    mapOf(
                        "domain_pack_id" to domainPackId,
                        "endpoint" to "/v1/classify",
                    ),
                )
            } else {
                telemetry.beginSpan(
                    "api.classify",
                    mapOf(
                        "domain_pack_id" to domainPackId,
                        "endpoint" to "/v1/classify",
                    ),
                )
            }

        // Set as active for HTTP header injection
        span?.let { TraceContext.setActiveSpan(it) }

        return span?.let { ClassificationSpan(it, telemetry) }
    }

    /**
     * Record successful classification metrics.
     *
     * @param latencyMs Request latency in milliseconds
     * @param requestId Backend request ID
     * @param attempts Number of attempts made
     */
    fun recordSuccess(
        latencyMs: Long,
        requestId: String?,
        attempts: Int,
    ) {
        telemetry?.timer(
            "mobile.api.duration_ms",
            latencyMs,
            mapOf(
                "endpoint" to "/v1/classify",
                "status_code" to "200",
            ),
        )
        telemetry?.counter(
            "mobile.api.request_count",
            1,
            mapOf(
                "endpoint" to "/v1/classify",
                "status" to "success",
            ),
        )
    }

    /**
     * Record error metrics.
     *
     * @param latencyMs Request latency in milliseconds
     * @param statusCode HTTP status code (null for network errors)
     * @param errorType Type of error (e.g., "timeout", "offline", "network")
     */
    fun recordError(
        latencyMs: Long? = null,
        statusCode: Int? = null,
        errorType: String? = null,
    ) {
        if (latencyMs != null && statusCode != null) {
            telemetry?.timer(
                "mobile.api.duration_ms",
                latencyMs,
                mapOf(
                    "endpoint" to "/v1/classify",
                    "status_code" to statusCode.toString(),
                ),
            )
        }

        telemetry?.counter(
            "mobile.api.error_count",
            1,
            buildMap {
                put("endpoint", "/v1/classify")
                statusCode?.let { put("status_code", it.toString()) }
                errorType?.let { put("error_type", it) }
            },
        )
    }

    /**
     * Record attempt-specific error for retry scenarios.
     *
     * @param errorType Type of error
     * @param attempt Attempt number
     */
    fun recordAttemptError(
        errorType: String,
        attempt: Int,
    ) {
        telemetry?.counter(
            "mobile.api.error_count",
            1,
            mapOf(
                "endpoint" to "/v1/classify",
                "error_type" to errorType,
            ),
        )
    }
}

/**
 * Wrapper for classification span with automatic cleanup.
 *
 * Ensures TraceContext is always cleared even if span management fails.
 */
class ClassificationSpan(
    private val span: SpanContext,
    private val telemetry: Telemetry,
) {
    /**
     * End span with success attributes.
     *
     * @param latencyMs Request latency
     * @param requestId Backend request ID
     * @param attempts Number of attempts
     */
    fun endSuccess(
        latencyMs: Long,
        requestId: String?,
        attempts: Int,
    ) {
        try {
            span.end(
                mapOf(
                    "status_code" to "200",
                    "latency_ms" to latencyMs.toString(),
                    "request_id" to (requestId ?: "unknown"),
                    "attempts" to attempts.toString(),
                ),
            )
        } finally {
            TraceContext.clearActiveSpan()
        }
    }

    /**
     * End span with error attributes.
     *
     * @param errorMessage Error message
     * @param statusCode HTTP status code (null for network errors)
     * @param attempts Number of attempts
     */
    fun endError(
        errorMessage: String,
        statusCode: Int? = null,
        attempts: Int? = null,
    ) {
        try {
            span.recordError(
                errorMessage,
                buildMap {
                    statusCode?.let { put("status_code", it.toString()) }
                    attempts?.let { put("attempts", it.toString()) }
                },
            )
            span.end(mapOf("status" to "error"))
        } finally {
            TraceContext.clearActiveSpan()
        }
    }

    /**
     * Record error in span without ending it.
     *
     * Used for retry scenarios where the span continues.
     *
     * @param errorMessage Error message
     * @param attributes Additional attributes
     */
    fun recordError(
        errorMessage: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        span.recordError(errorMessage, attributes)
    }

    /**
     * End span with failure after all retries exhausted.
     *
     * @param attempts Total number of attempts
     */
    fun endAllRetriesFailed(attempts: Int) {
        try {
            span.end(
                mapOf(
                    "status" to "failed_all_retries",
                    "attempts" to attempts.toString(),
                ),
            )
        } finally {
            TraceContext.clearActiveSpan()
        }
    }

    /**
     * End span with exception.
     *
     * @param status Status label (e.g., "exception")
     */
    fun endException(status: String = "exception") {
        try {
            span.end(mapOf("status" to status))
        } finally {
            TraceContext.clearActiveSpan()
        }
    }

    /**
     * Ensure cleanup happens even in unexpected scenarios.
     */
    fun ensureCleanup() {
        TraceContext.clearActiveSpan()
    }
}
