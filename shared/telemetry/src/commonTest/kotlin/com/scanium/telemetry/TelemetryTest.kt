package com.scanium.telemetry

import com.scanium.telemetry.facade.DefaultAttributesProvider
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.LogPort
import com.scanium.telemetry.ports.MetricPort
import com.scanium.telemetry.ports.NoOpMetricPort
import com.scanium.telemetry.ports.NoOpTracePort
import com.scanium.telemetry.ports.SpanContext
import com.scanium.telemetry.ports.TracePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelemetryTest {

    // Test default attributes provider
    private class TestDefaultAttributesProvider(
        private val attributes: Map<String, String> = mapOf(
            TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
            TelemetryEvent.ATTR_APP_VERSION to "1.0.0",
            TelemetryEvent.ATTR_BUILD to "1",
            TelemetryEvent.ATTR_ENV to TelemetryEvent.ENV_DEV,
            TelemetryEvent.ATTR_SESSION_ID to "test-session-123"
        )
    ) : DefaultAttributesProvider {
        override fun getDefaultAttributes(): Map<String, String> = attributes
    }

    // Test log port that captures events
    private class CapturingLogPort : LogPort {
        val events = mutableListOf<TelemetryEvent>()

        override fun emit(event: TelemetryEvent) {
            events.add(event)
        }
    }

    // Test metric port that captures metrics
    private class CapturingMetricPort : MetricPort {
        val counters = mutableListOf<Triple<String, Long, Map<String, String>>>()
        val timers = mutableListOf<Triple<String, Long, Map<String, String>>>()
        val gauges = mutableListOf<Triple<String, Double, Map<String, String>>>()

        override fun counter(name: String, delta: Long, attributes: Map<String, String>) {
            counters.add(Triple(name, delta, attributes))
        }

        override fun timer(name: String, millis: Long, attributes: Map<String, String>) {
            timers.add(Triple(name, millis, attributes))
        }

        override fun gauge(name: String, value: Double, attributes: Map<String, String>) {
            gauges.add(Triple(name, value, attributes))
        }
    }

    // Test trace port that captures spans
    private class CapturingTracePort : TracePort {
        val spans = mutableListOf<Pair<String, Map<String, String>>>()

        override fun beginSpan(name: String, attributes: Map<String, String>): SpanContext {
            spans.add(Pair(name, attributes))
            return object : SpanContext {
                override fun end(additionalAttributes: Map<String, String>) {}
                override fun setAttribute(key: String, value: String) {}
                override fun recordError(error: String, attributes: Map<String, String>) {}
            }
        }
    }

    @Test
    fun `telemetry applies sanitization to user attributes`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.event(
            name = "test.event",
            severity = TelemetrySeverity.INFO,
            userAttributes = mapOf(
                "safe_attribute" to "safe_value",
                "email" to "user@example.com",  // Should be redacted
                "phone" to "+1234567890"         // Should be redacted
            )
        )

        assertEquals(1, logPort.events.size)
        val event = logPort.events[0]

        assertEquals("safe_value", event.attributes["safe_attribute"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, event.attributes["email"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, event.attributes["phone"])
    }

    @Test
    fun `telemetry merges user attributes with defaults`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.event(
            name = "test.event",
            severity = TelemetrySeverity.INFO,
            userAttributes = mapOf("custom_field" to "custom_value")
        )

        assertEquals(1, logPort.events.size)
        val event = logPort.events[0]

        // Verify default attributes are present
        assertEquals(TelemetryEvent.PLATFORM_ANDROID, event.attributes[TelemetryEvent.ATTR_PLATFORM])
        assertEquals("1.0.0", event.attributes[TelemetryEvent.ATTR_APP_VERSION])
        assertEquals("1", event.attributes[TelemetryEvent.ATTR_BUILD])
        assertEquals(TelemetryEvent.ENV_DEV, event.attributes[TelemetryEvent.ATTR_ENV])
        assertEquals("test-session-123", event.attributes[TelemetryEvent.ATTR_SESSION_ID])

        // Verify user attribute is present
        assertEquals("custom_value", event.attributes["custom_field"])
    }

    @Test
    fun `telemetry user attributes override defaults`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.event(
            name = "test.event",
            severity = TelemetrySeverity.INFO,
            userAttributes = mapOf(TelemetryEvent.ATTR_ENV to TelemetryEvent.ENV_PROD)
        )

        assertEquals(1, logPort.events.size)
        val event = logPort.events[0]

        // User-provided env should override default
        assertEquals(TelemetryEvent.ENV_PROD, event.attributes[TelemetryEvent.ATTR_ENV])
    }

    @Test
    fun `telemetry fails fast when required attributes are missing`() {
        val incompleteProvider = TestDefaultAttributesProvider(
            attributes = mapOf(
                TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID
                // Missing: app_version, build, env, session_id
            )
        )

        val telemetry = Telemetry(
            defaultAttributesProvider = incompleteProvider,
            logPort = CapturingLogPort(),
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        val exception = assertFailsWith<IllegalStateException> {
            telemetry.event("test.event", TelemetrySeverity.INFO)
        }

        assertTrue(exception.message!!.contains("Missing required telemetry attributes"))
        assertTrue(exception.message!!.contains(TelemetryEvent.ATTR_APP_VERSION))
        assertTrue(exception.message!!.contains(TelemetryEvent.ATTR_BUILD))
        assertTrue(exception.message!!.contains(TelemetryEvent.ATTR_ENV))
        assertTrue(exception.message!!.contains(TelemetryEvent.ATTR_SESSION_ID))
    }

    @Test
    fun `info convenience method creates INFO event`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.info("test.info", mapOf("key" to "value"))

        assertEquals(1, logPort.events.size)
        assertEquals(TelemetrySeverity.INFO, logPort.events[0].severity)
        assertEquals("test.info", logPort.events[0].name)
    }

    @Test
    fun `warn convenience method creates WARN event`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.warn("test.warn")

        assertEquals(1, logPort.events.size)
        assertEquals(TelemetrySeverity.WARN, logPort.events[0].severity)
    }

    @Test
    fun `error convenience method creates ERROR event`() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        telemetry.error("test.error")

        assertEquals(1, logPort.events.size)
        assertEquals(TelemetrySeverity.ERROR, logPort.events[0].severity)
    }

    @Test
    fun `counter metric includes sanitized attributes`() {
        val metricPort = CapturingMetricPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = metricPort,
            tracePort = NoOpTracePort
        )

        telemetry.counter(
            name = "test.counter",
            delta = 5,
            userAttributes = mapOf(
                "metric_type" to "test",
                "email" to "user@example.com"  // Should be redacted
            )
        )

        assertEquals(1, metricPort.counters.size)
        val (name, delta, attrs) = metricPort.counters[0]

        assertEquals("test.counter", name)
        assertEquals(5L, delta)
        assertEquals("test", attrs["metric_type"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, attrs["email"])
        assertEquals(TelemetryEvent.PLATFORM_ANDROID, attrs[TelemetryEvent.ATTR_PLATFORM])
    }

    @Test
    fun `timer metric includes sanitized attributes`() {
        val metricPort = CapturingMetricPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = metricPort,
            tracePort = NoOpTracePort
        )

        telemetry.timer(
            name = "test.timer",
            millis = 1234,
            userAttributes = mapOf("operation" to "test")
        )

        assertEquals(1, metricPort.timers.size)
        val (name, millis, attrs) = metricPort.timers[0]

        assertEquals("test.timer", name)
        assertEquals(1234L, millis)
        assertEquals("test", attrs["operation"])
    }

    @Test
    fun `gauge metric includes sanitized attributes`() {
        val metricPort = CapturingMetricPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = metricPort,
            tracePort = NoOpTracePort
        )

        telemetry.gauge(
            name = "test.gauge",
            value = 42.5,
            userAttributes = mapOf("unit" to "percent")
        )

        assertEquals(1, metricPort.gauges.size)
        val (name, value, attrs) = metricPort.gauges[0]

        assertEquals("test.gauge", name)
        assertEquals(42.5, value)
        assertEquals("percent", attrs["unit"])
    }

    @Test
    fun `beginSpan includes sanitized attributes`() {
        val tracePort = CapturingTracePort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = NoOpMetricPort,
            tracePort = tracePort
        )

        val span = telemetry.beginSpan(
            name = "test.span",
            userAttributes = mapOf(
                "operation" to "test",
                "password" to "secret123"  // Should be redacted
            )
        )

        assertEquals(1, tracePort.spans.size)
        val (name, attrs) = tracePort.spans[0]

        assertEquals("test.span", name)
        assertEquals("test", attrs["operation"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, attrs["password"])

        span.end()
    }

    @Test
    fun `span helper automatically ends span`() {
        val tracePort = CapturingTracePort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = NoOpMetricPort,
            tracePort = tracePort
        )

        val result = telemetry.span("test.span") { span ->
            // Span is active here
            "result_value"
        }

        assertEquals("result_value", result)
        assertEquals(1, tracePort.spans.size)
    }

    @Test
    fun `span helper records error on exception`() {
        val tracePort = CapturingTracePort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = CapturingLogPort(),
            metricPort = NoOpMetricPort,
            tracePort = tracePort
        )

        assertFailsWith<RuntimeException> {
            telemetry.span("test.span") { span ->
                throw RuntimeException("Test error")
            }
        }

        // Span should still be created even though exception was thrown
        assertEquals(1, tracePort.spans.size)
    }
}
