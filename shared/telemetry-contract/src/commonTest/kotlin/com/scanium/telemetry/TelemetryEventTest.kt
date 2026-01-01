package com.scanium.telemetry

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelemetryEventTest {
    @Test
    fun `TelemetryEvent can be created with valid data`() {
        val timestamp = Clock.System.now()
        val event =
            TelemetryEvent(
                name = "scan.started",
                severity = TelemetrySeverity.INFO,
                timestamp = timestamp,
                attributes =
                    mapOf(
                        "platform" to "android",
                        "app_version" to "1.0.0",
                    ),
            )

        assertEquals("scan.started", event.name)
        assertEquals(TelemetrySeverity.INFO, event.severity)
        assertEquals(timestamp, event.timestamp)
        assertEquals("android", event.attributes["platform"])
        assertEquals("1.0.0", event.attributes["app_version"])
    }

    @Test
    fun `TelemetryEvent fails with blank name`() {
        assertFailsWith<IllegalArgumentException> {
            TelemetryEvent(
                name = "",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes = emptyMap(),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TelemetryEvent(
                name = "   ",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes = emptyMap(),
            )
        }
    }

    @Test
    fun `TelemetryEvent fails with blank attribute keys`() {
        assertFailsWith<IllegalArgumentException> {
            TelemetryEvent(
                name = "test.event",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes = mapOf("" to "value"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TelemetryEvent(
                name = "test.event",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes = mapOf("   " to "value"),
            )
        }
    }

    @Test
    fun `TelemetryEvent can be created without attributes`() {
        val event =
            TelemetryEvent(
                name = "test.event",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
            )

        assertTrue(event.attributes.isEmpty())
    }

    @Test
    fun `TelemetryEvent constants are defined`() {
        assertEquals("platform", TelemetryEvent.ATTR_PLATFORM)
        assertEquals("app_version", TelemetryEvent.ATTR_APP_VERSION)
        assertEquals("build", TelemetryEvent.ATTR_BUILD)
        assertEquals("env", TelemetryEvent.ATTR_ENV)
        assertEquals("session_id", TelemetryEvent.ATTR_SESSION_ID)
        assertEquals("data_region", TelemetryEvent.ATTR_DATA_REGION)
        assertEquals("trace_id", TelemetryEvent.ATTR_TRACE_ID)

        assertEquals("android", TelemetryEvent.PLATFORM_ANDROID)
        assertEquals("ios", TelemetryEvent.PLATFORM_IOS)

        assertEquals("dev", TelemetryEvent.ENV_DEV)
        assertEquals("staging", TelemetryEvent.ENV_STAGING)
        assertEquals("prod", TelemetryEvent.ENV_PROD)
    }

    @Test
    fun `TelemetryEvent can include all required attributes`() {
        val event =
            TelemetryEvent(
                name = "scan.started",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes =
                    mapOf(
                        TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
                        TelemetryEvent.ATTR_APP_VERSION to "1.0.0",
                        TelemetryEvent.ATTR_BUILD to "42",
                        TelemetryEvent.ATTR_ENV to TelemetryEvent.ENV_PROD,
                        TelemetryEvent.ATTR_SESSION_ID to "session-123",
                        TelemetryEvent.ATTR_DATA_REGION to "EU",
                    ),
            )

        assertEquals(TelemetryEvent.PLATFORM_ANDROID, event.attributes[TelemetryEvent.ATTR_PLATFORM])
        assertEquals("1.0.0", event.attributes[TelemetryEvent.ATTR_APP_VERSION])
        assertEquals("42", event.attributes[TelemetryEvent.ATTR_BUILD])
        assertEquals(TelemetryEvent.ENV_PROD, event.attributes[TelemetryEvent.ATTR_ENV])
        assertEquals("session-123", event.attributes[TelemetryEvent.ATTR_SESSION_ID])
        assertEquals("EU", event.attributes[TelemetryEvent.ATTR_DATA_REGION])
    }

    @Test
    fun `TelemetryEvent can include optional trace_id`() {
        val event =
            TelemetryEvent(
                name = "scan.started",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes =
                    mapOf(
                        TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_IOS,
                        TelemetryEvent.ATTR_TRACE_ID to "trace-abc-123",
                    ),
            )

        assertEquals("trace-abc-123", event.attributes[TelemetryEvent.ATTR_TRACE_ID])
    }

    @Test
    fun `TelemetrySeverity enum has all levels`() {
        val severities = TelemetrySeverity.entries
        assertEquals(5, severities.size)
        assertTrue(severities.contains(TelemetrySeverity.DEBUG))
        assertTrue(severities.contains(TelemetrySeverity.INFO))
        assertTrue(severities.contains(TelemetrySeverity.WARN))
        assertTrue(severities.contains(TelemetrySeverity.ERROR))
        assertTrue(severities.contains(TelemetrySeverity.FATAL))
    }
}
