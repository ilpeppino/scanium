package com.scanium.app.telemetry.otlp

import kotlinx.serialization.Serializable

/**
 * Lightweight OTLP JSON models for exporting telemetry data.
 *
 * Implements a minimal subset of the OTLP/HTTP JSON specification:
 * https://opentelemetry.io/docs/specs/otlp/
 *
 * Only includes fields necessary for basic observability export.
 */

// ============================================================================
// Common Models
// ============================================================================

@Serializable
data class KeyValue(
    val key: String,
    val value: AnyValue
)

@Serializable
data class AnyValue(
    val stringValue: String? = null,
    val intValue: Long? = null,
    val doubleValue: Double? = null,
    val boolValue: Boolean? = null
) {
    companion object {
        fun string(value: String) = AnyValue(stringValue = value)
        fun int(value: Long) = AnyValue(intValue = value)
        fun double(value: Double) = AnyValue(doubleValue = value)
        fun bool(value: Boolean) = AnyValue(boolValue = value)
    }
}

@Serializable
data class Resource(
    val attributes: List<KeyValue>
)

@Serializable
data class InstrumentationScope(
    val name: String = "com.scanium.telemetry",
    val version: String = "1.0.0"
)

// ============================================================================
// Logs Models
// ============================================================================

@Serializable
data class ExportLogsServiceRequest(
    val resourceLogs: List<ResourceLogs>
)

@Serializable
data class ResourceLogs(
    val resource: Resource,
    val scopeLogs: List<ScopeLogs>
)

@Serializable
data class ScopeLogs(
    val scope: InstrumentationScope,
    val logRecords: List<LogRecord>
)

@Serializable
data class LogRecord(
    val timeUnixNano: String,
    val severityNumber: Int,
    val severityText: String,
    val body: AnyValue,
    val attributes: List<KeyValue> = emptyList()
) {
    companion object {
        // OTLP severity numbers
        const val SEVERITY_DEBUG = 5
        const val SEVERITY_INFO = 9
        const val SEVERITY_WARN = 13
        const val SEVERITY_ERROR = 17
        const val SEVERITY_FATAL = 21
    }
}

// ============================================================================
// Metrics Models
// ============================================================================

@Serializable
data class ExportMetricsServiceRequest(
    val resourceMetrics: List<ResourceMetrics>
)

@Serializable
data class ResourceMetrics(
    val resource: Resource,
    val scopeMetrics: List<ScopeMetrics>
)

@Serializable
data class ScopeMetrics(
    val scope: InstrumentationScope,
    val metrics: List<Metric>
)

@Serializable
data class Metric(
    val name: String,
    val description: String = "",
    val unit: String = "",
    val sum: Sum? = null,
    val gauge: Gauge? = null
)

@Serializable
data class Sum(
    val dataPoints: List<NumberDataPoint>,
    val aggregationTemporality: Int = 2, // CUMULATIVE
    val isMonotonic: Boolean = true
)

@Serializable
data class Gauge(
    val dataPoints: List<NumberDataPoint>
)

@Serializable
data class NumberDataPoint(
    val timeUnixNano: String,
    val asInt: Long? = null,
    val asDouble: Double? = null,
    val attributes: List<KeyValue> = emptyList()
)

// ============================================================================
// Traces Models
// ============================================================================

@Serializable
data class ExportTraceServiceRequest(
    val resourceSpans: List<ResourceSpans>
)

@Serializable
data class ResourceSpans(
    val resource: Resource,
    val scopeSpans: List<ScopeSpans>
)

@Serializable
data class ScopeSpans(
    val scope: InstrumentationScope,
    val spans: List<Span>
)

@Serializable
data class Span(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val name: String,
    val kind: Int = 1, // INTERNAL
    val startTimeUnixNano: String,
    val endTimeUnixNano: String,
    val attributes: List<KeyValue> = emptyList(),
    val status: SpanStatus? = null
) {
    companion object {
        const val SPAN_KIND_INTERNAL = 1
        const val SPAN_KIND_SERVER = 2
        const val SPAN_KIND_CLIENT = 3
    }
}

@Serializable
data class SpanStatus(
    val code: Int = 0, // UNSET
    val message: String = ""
) {
    companion object {
        const val STATUS_CODE_UNSET = 0
        const val STATUS_CODE_OK = 1
        const val STATUS_CODE_ERROR = 2
    }
}
