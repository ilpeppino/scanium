package com.scanium.app.telemetry.otlp

import com.google.common.truth.Truth.assertThat
import com.scanium.app.telemetry.OtlpConfiguration
import com.scanium.telemetry.TelemetryConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtlpHttpExporterTest {
    private lateinit var exporter: OtlpHttpExporter
    private val otlpConfig = OtlpConfiguration(
        enabled = true,
        endpoint = "https://test.example.com",
        environment = "test",
        serviceName = "scanium-test",
        serviceVersion = "1.0.0",
        debugLogging = false
    )
    private val telemetryConfig = TelemetryConfig(
        enabled = true,
        maxRetries = 3,
        retryBackoffMs = 1000L
    )

    @Before
    fun setUp() {
        exporter = OtlpHttpExporter(otlpConfig, telemetryConfig)
    }

    @After
    fun tearDown() {
        exporter.close()
    }

    @Test
    fun `exportLogs does nothing when OTLP disabled`() {
        val disabledConfig = OtlpConfiguration(enabled = false)
        val disabledExporter = OtlpHttpExporter(disabledConfig, telemetryConfig)

        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = Resource(attributes = emptyList()),
                    scopeLogs = emptyList()
                )
            )
        )

        disabledExporter.exportLogs(request)

        disabledExporter.close()
    }

    @Test
    fun `exportMetrics does nothing when OTLP disabled`() {
        val disabledConfig = OtlpConfiguration(enabled = false)
        val disabledExporter = OtlpHttpExporter(disabledConfig, telemetryConfig)

        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = Resource(attributes = emptyList()),
                    scopeMetrics = emptyList()
                )
            )
        )

        disabledExporter.exportMetrics(request)

        disabledExporter.close()
    }

    @Test
    fun `exportTraces does nothing when OTLP disabled`() {
        val disabledConfig = OtlpConfiguration(enabled = false)
        val disabledExporter = OtlpHttpExporter(disabledConfig, telemetryConfig)

        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = Resource(attributes = emptyList()),
                    scopeSpans = emptyList()
                )
            )
        )

        disabledExporter.exportTraces(request)

        disabledExporter.close()
    }

    @Test
    fun `buildResource creates correct resource attributes`() {
        val resource = exporter.buildResource()

        assertThat(resource.attributes).hasSize(6)

        val serviceAttr = resource.attributes.find { it.key == "service.name" }
        assertThat(serviceAttr).isNotNull()
        assertThat(serviceAttr?.value?.stringValue).isEqualTo("scanium-test")

        val versionAttr = resource.attributes.find { it.key == "service.version" }
        assertThat(versionAttr).isNotNull()
        assertThat(versionAttr?.value?.stringValue).isEqualTo("1.0.0")

        val envAttr = resource.attributes.find { it.key == "deployment.environment" }
        assertThat(envAttr).isNotNull()
        assertThat(envAttr?.value?.stringValue).isEqualTo("test")
    }

    @Test
    fun `buildResource includes telemetry SDK attributes`() {
        val resource = exporter.buildResource()

        val sdkNameAttr = resource.attributes.find { it.key == "telemetry.sdk.name" }
        assertThat(sdkNameAttr).isNotNull()
        assertThat(sdkNameAttr?.value?.stringValue).isEqualTo("scanium-telemetry")

        val sdkLangAttr = resource.attributes.find { it.key == "telemetry.sdk.language" }
        assertThat(sdkLangAttr).isNotNull()
        assertThat(sdkLangAttr?.value?.stringValue).isEqualTo("kotlin")

        val sdkVersionAttr = resource.attributes.find { it.key == "telemetry.sdk.version" }
        assertThat(sdkVersionAttr).isNotNull()
        assertThat(sdkVersionAttr?.value?.stringValue).isEqualTo("1.0.0")
    }

    @Test
    fun `exportLogs processes log records`() {
        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = exporter.buildResource(),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = listOf(
                                LogRecord(
                                    timeUnixNano = "1234567890000000000",
                                    severityNumber = LogRecord.SEVERITY_INFO,
                                    severityText = "INFO",
                                    body = AnyValue.string("Test log message")
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportLogs(request)
    }

    @Test
    fun `exportMetrics processes metric data`() {
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(),
                            metrics = listOf(
                                Metric(
                                    name = "test.metric",
                                    description = "Test metric",
                                    unit = "1",
                                    gauge = Gauge(
                                        dataPoints = listOf(
                                            NumberDataPoint(
                                                timeUnixNano = "1234567890000000000",
                                                asDouble = 42.0
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportMetrics(request)
    }

    @Test
    fun `exportTraces processes span data`() {
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(),
                            spans = listOf(
                                Span(
                                    traceId = "trace123",
                                    spanId = "span123",
                                    name = "test.span",
                                    kind = Span.SPAN_KIND_INTERNAL,
                                    startTimeUnixNano = "1234567890000000000",
                                    endTimeUnixNano = "1234567891000000000"
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportTraces(request)
    }

    @Test
    fun `exportLogs handles empty log records`() {
        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = exporter.buildResource(),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = emptyList()
                        )
                    )
                )
            )
        )

        exporter.exportLogs(request)
    }

    @Test
    fun `exportMetrics handles empty metrics`() {
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(),
                            metrics = emptyList()
                        )
                    )
                )
            )
        )

        exporter.exportMetrics(request)
    }

    @Test
    fun `exportTraces handles empty spans`() {
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(),
                            spans = emptyList()
                        )
                    )
                )
            )
        )

        exporter.exportTraces(request)
    }

    @Test
    fun `exportLogs handles multiple log records`() {
        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = exporter.buildResource(),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = listOf(
                                LogRecord(
                                    timeUnixNano = "1234567890000000000",
                                    severityNumber = LogRecord.SEVERITY_INFO,
                                    severityText = "INFO",
                                    body = AnyValue.string("First log")
                                ),
                                LogRecord(
                                    timeUnixNano = "1234567890000000001",
                                    severityNumber = LogRecord.SEVERITY_WARN,
                                    severityText = "WARN",
                                    body = AnyValue.string("Second log")
                                ),
                                LogRecord(
                                    timeUnixNano = "1234567890000000002",
                                    severityNumber = LogRecord.SEVERITY_ERROR,
                                    severityText = "ERROR",
                                    body = AnyValue.string("Third log")
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportLogs(request)
    }

    @Test
    fun `exportMetrics handles gauge metric`() {
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(),
                            metrics = listOf(
                                Metric(
                                    name = "gauge.metric",
                                    unit = "ms",
                                    gauge = Gauge(
                                        dataPoints = listOf(
                                            NumberDataPoint(
                                                timeUnixNano = "1234567890000000000",
                                                asDouble = 100.0
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportMetrics(request)
    }

    @Test
    fun `exportMetrics handles sum metric`() {
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(),
                            metrics = listOf(
                                Metric(
                                    name = "sum.metric",
                                    unit = "1",
                                    sum = Sum(
                                        dataPoints = listOf(
                                            NumberDataPoint(
                                                timeUnixNano = "1234567890000000000",
                                                asInt = 1000L
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportMetrics(request)
    }

    @Test
    fun `exportMetrics handles histogram metric`() {
        val request = ExportMetricsServiceRequest(
            resourceMetrics = listOf(
                ResourceMetrics(
                    resource = exporter.buildResource(),
                    scopeMetrics = listOf(
                        ScopeMetrics(
                            scope = InstrumentationScope(),
                            metrics = listOf(
                                Metric(
                                    name = "histogram.metric",
                                    unit = "ms",
                                    histogram = Histogram(
                                        dataPoints = listOf(
                                            HistogramDataPoint(
                                                timeUnixNano = "1234567890000000000",
                                                count = 100,
                                                sum = 10000.0,
                                                bucketCounts = listOf(10L, 20L, 30L, 40L),
                                                explicitBounds = listOf(10.0, 20.0, 30.0)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportMetrics(request)
    }

    @Test
    fun `exportTraces handles span with parent`() {
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(),
                            spans = listOf(
                                Span(
                                    traceId = "trace123",
                                    spanId = "child_span",
                                    parentSpanId = "parent_span",
                                    name = "child.operation",
                                    kind = Span.SPAN_KIND_CLIENT,
                                    startTimeUnixNano = "1234567890000000000",
                                    endTimeUnixNano = "1234567891000000000"
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportTraces(request)
    }

    @Test
    fun `exportTraces handles span with status`() {
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(),
                            spans = listOf(
                                Span(
                                    traceId = "trace123",
                                    spanId = "span123",
                                    name = "operation.with.error",
                                    kind = Span.SPAN_KIND_SERVER,
                                    startTimeUnixNano = "1234567890000000000",
                                    endTimeUnixNano = "1234567891000000000",
                                    status = SpanStatus(
                                        code = SpanStatus.STATUS_CODE_ERROR,
                                        message = "Operation failed"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportTraces(request)
    }

    @Test
    fun `exportTraces handles span with attributes`() {
        val request = ExportTraceServiceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = exporter.buildResource(),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(),
                            spans = listOf(
                                Span(
                                    traceId = "trace123",
                                    spanId = "span123",
                                    name = "operation.with.attrs",
                                    kind = Span.SPAN_KIND_INTERNAL,
                                    startTimeUnixNano = "1234567890000000000",
                                    endTimeUnixNano = "1234567891000000000",
                                    attributes = listOf(
                                        KeyValue("http.method", AnyValue.string("GET")),
                                        KeyValue("http.status_code", AnyValue.int(200)),
                                        KeyValue("http.url", AnyValue.string("/api/test"))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportTraces(request)
    }

    @Test
    fun `close shuts down exporter cleanly`() {
        exporter.exportLogs(
            ExportLogsServiceRequest(
                resourceLogs = listOf(
                    ResourceLogs(
                        resource = exporter.buildResource(),
                        scopeLogs = listOf(
                            ScopeLogs(
                                scope = InstrumentationScope(),
                                logRecords = listOf(
                                    LogRecord(
                                        timeUnixNano = "1234567890000000000",
                                        severityNumber = LogRecord.SEVERITY_INFO,
                                        severityText = "INFO",
                                        body = AnyValue.string("Test log")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.close()
    }

    @Test
    fun `exportLogs with debugLogging enabled`() {
        val debugConfig = otlpConfig.copy(debugLogging = true)
        val debugExporter = OtlpHttpExporter(debugConfig, telemetryConfig)

        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = exporter.buildResource(),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = listOf(
                                LogRecord(
                                    timeUnixNano = "1234567890000000000",
                                    severityNumber = LogRecord.SEVERITY_INFO,
                                    severityText = "INFO",
                                    body = AnyValue.string("Debug log")
                                )
                            )
                        )
                    )
                )
            )
        )

        debugExporter.exportLogs(request)

        debugExporter.close()
    }

    @Test
    fun `exportLogs with multiple resource logs`() {
        val request = ExportLogsServiceRequest(
            resourceLogs = listOf(
                ResourceLogs(
                    resource = Resource(attributes = listOf(KeyValue("resource1", AnyValue.string("value1")))),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = listOf(
                                LogRecord(
                                    timeUnixNano = "1234567890000000000",
                                    severityNumber = LogRecord.SEVERITY_INFO,
                                    severityText = "INFO",
                                    body = AnyValue.string("Log 1")
                                )
                            )
                        )
                    )
                ),
                ResourceLogs(
                    resource = Resource(attributes = listOf(KeyValue("resource2", AnyValue.string("value2")))),
                    scopeLogs = listOf(
                        ScopeLogs(
                            scope = InstrumentationScope(),
                            logRecords = listOf(
                                LogRecord(
                                    timeUnixNano = "1234567890000000000",
                                    severityNumber = LogRecord.SEVERITY_INFO,
                                    severityText = "INFO",
                                    body = AnyValue.string("Log 2")
                                )
                            )
                        )
                    )
                )
            )
        )

        exporter.exportLogs(request)
    }
}
