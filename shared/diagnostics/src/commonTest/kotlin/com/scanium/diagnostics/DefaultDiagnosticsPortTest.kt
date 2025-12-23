package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultDiagnosticsPortTest {

    private fun createTestEvent(name: String): TelemetryEvent {
        return TelemetryEvent(
            name = name,
            severity = TelemetrySeverity.INFO,
            timestamp = Clock.System.now(),
            attributes = mapOf("test" to "value")
        )
    }

    @Test
    fun `port appends breadcrumbs`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = { mapOf("platform" to "android") },
            maxEvents = 10,
            maxBytes = 10000
        )

        port.appendBreadcrumb(createTestEvent("event1"))
        port.appendBreadcrumb(createTestEvent("event2"))

        assertEquals(2, port.breadcrumbCount())
    }

    @Test
    fun `port builds bundle with context and events`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = {
                mapOf(
                    "platform" to "android",
                    "app_version" to "1.0.0",
                    "build" to "42"
                )
            },
            maxEvents = 10,
            maxBytes = 10000
        )

        port.appendBreadcrumb(createTestEvent("scan.started"))
        port.appendBreadcrumb(createTestEvent("scan.completed"))

        val bundle = port.buildDiagnosticsBundle()
        val jsonString = bundle.decodeToString()

        val json = Json.parseToJsonElement(jsonString)

        // Verify context
        val context = json.jsonObject["context"]!!.jsonObject
        assertEquals("android", context["platform"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", context["app_version"]?.jsonPrimitive?.content)
        assertEquals("42", context["build"]?.jsonPrimitive?.content)

        // Verify events
        val events = json.jsonObject["events"]!!.jsonArray
        assertEquals(2, events.size)
    }

    @Test
    fun `port clears breadcrumbs`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = { mapOf("platform" to "android") },
            maxEvents = 10,
            maxBytes = 10000
        )

        port.appendBreadcrumb(createTestEvent("event1"))
        port.appendBreadcrumb(createTestEvent("event2"))

        assertEquals(2, port.breadcrumbCount())

        port.clearBreadcrumbs()

        assertEquals(0, port.breadcrumbCount())
    }

    @Test
    fun `port respects max events limit`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = { mapOf("platform" to "android") },
            maxEvents = 3,
            maxBytes = 100000
        )

        for (i in 1..10) {
            port.appendBreadcrumb(createTestEvent("event$i"))
        }

        assertEquals(3, port.breadcrumbCount())
    }

    @Test
    fun `port bundle includes generatedAt timestamp`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = { mapOf("platform" to "android") }
        )

        val bundle = port.buildDiagnosticsBundle()
        val jsonString = bundle.decodeToString()
        val json = Json.parseToJsonElement(jsonString)

        val generatedAt = json.jsonObject["generatedAt"]?.jsonPrimitive?.content
        assertTrue(generatedAt != null && generatedAt.isNotEmpty())
    }

    @Test
    fun `port contextProvider is called when building bundle`() {
        var contextCallCount = 0

        val port = DefaultDiagnosticsPort(
            contextProvider = {
                contextCallCount++
                mapOf("platform" to "android", "call_count" to contextCallCount.toString())
            }
        )

        port.appendBreadcrumb(createTestEvent("event1"))

        val bundle1 = port.buildDiagnosticsBundle()
        val bundle2 = port.buildDiagnosticsBundle()

        assertEquals(2, contextCallCount)

        // Verify context was updated in second call
        val json2 = Json.parseToJsonElement(bundle2.decodeToString())
        val context2 = json2.jsonObject["context"]!!.jsonObject
        assertEquals("2", context2["call_count"]?.jsonPrimitive?.content)
    }

    @Test
    fun `NoOpDiagnosticsPort discards all breadcrumbs`() {
        val port = NoOpDiagnosticsPort

        port.appendBreadcrumb(createTestEvent("event1"))
        port.appendBreadcrumb(createTestEvent("event2"))

        assertEquals(0, port.breadcrumbCount())
    }

    @Test
    fun `NoOpDiagnosticsPort returns empty bundle`() {
        val port = NoOpDiagnosticsPort

        port.appendBreadcrumb(createTestEvent("event1"))

        val bundle = port.buildDiagnosticsBundle()
        val jsonString = bundle.decodeToString()

        assertEquals("{}", jsonString)
    }

    @Test
    fun `NoOpDiagnosticsPort clear does nothing`() {
        val port = NoOpDiagnosticsPort

        port.appendBreadcrumb(createTestEvent("event1"))
        port.clearBreadcrumbs()

        assertEquals(0, port.breadcrumbCount())
    }

    @Test
    fun `port handles empty breadcrumbs`() {
        val port = DefaultDiagnosticsPort(
            contextProvider = { mapOf("platform" to "android") }
        )

        val bundle = port.buildDiagnosticsBundle()
        val jsonString = bundle.decodeToString()
        val json = Json.parseToJsonElement(jsonString)

        val events = json.jsonObject["events"]!!.jsonArray
        assertEquals(0, events.size)
    }
}
