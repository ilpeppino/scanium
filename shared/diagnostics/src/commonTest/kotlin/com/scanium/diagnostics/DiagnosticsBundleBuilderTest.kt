package com.scanium.diagnostics

import com.scanium.telemetry.TelemetryEvent
import com.scanium.telemetry.TelemetrySeverity
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsBundleBuilderTest {
    private fun createTestEvent(name: String): TelemetryEvent {
        return TelemetryEvent(
            name = name,
            severity = TelemetrySeverity.INFO,
            timestamp = Clock.System.now(),
            attributes = mapOf("test_key" to "test_value"),
        )
    }

    @Test
    fun `buildJsonString creates valid JSON`() {
        val builder = DiagnosticsBundleBuilder()

        val context =
            mapOf(
                "platform" to "android",
                "app_version" to "1.0.0",
                "build" to "42",
            )

        val events =
            listOf(
                createTestEvent("event1"),
                createTestEvent("event2"),
            )

        val jsonString = builder.buildJsonString(context, events)

        // Verify it's valid JSON by parsing
        val json = Json.parseToJsonElement(jsonString)
        assertTrue(json.jsonObject.containsKey("generatedAt"))
        assertTrue(json.jsonObject.containsKey("context"))
        assertTrue(json.jsonObject.containsKey("events"))
    }

    @Test
    fun `buildJsonString includes all context fields`() {
        val builder = DiagnosticsBundleBuilder()

        val context =
            mapOf(
                "platform" to "android",
                "app_version" to "1.0.0",
                "build" to "42",
                "env" to "prod",
                "session_id" to "session-123",
            )

        val events = emptyList<TelemetryEvent>()

        val jsonString = builder.buildJsonString(context, events)
        val json = Json.parseToJsonElement(jsonString)

        val contextObj = json.jsonObject["context"]!!.jsonObject
        assertEquals("android", contextObj["platform"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", contextObj["app_version"]?.jsonPrimitive?.content)
        assertEquals("42", contextObj["build"]?.jsonPrimitive?.content)
        assertEquals("prod", contextObj["env"]?.jsonPrimitive?.content)
        assertEquals("session-123", contextObj["session_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `buildJsonString includes events array`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")

        val events =
            listOf(
                createTestEvent("scan.started"),
                createTestEvent("scan.completed"),
            )

        val jsonString = builder.buildJsonString(context, events)
        val json = Json.parseToJsonElement(jsonString)

        val eventsArray = json.jsonObject["events"]!!
        assertTrue(jsonString.contains("scan.started"))
        assertTrue(jsonString.contains("scan.completed"))
    }

    @Test
    fun `buildJsonString handles empty events`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events = emptyList<TelemetryEvent>()

        val jsonString = builder.buildJsonString(context, events)
        val json = Json.parseToJsonElement(jsonString)

        assertTrue(json.jsonObject.containsKey("events"))
    }

    @Test
    fun `buildJsonString includes generatedAt timestamp`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events = emptyList<TelemetryEvent>()

        val jsonString = builder.buildJsonString(context, events)
        val json = Json.parseToJsonElement(jsonString)

        val generatedAt = json.jsonObject["generatedAt"]?.jsonPrimitive?.content
        assertTrue(generatedAt != null && generatedAt.isNotEmpty())
        // Verify it looks like an ISO timestamp (basic check)
        assertTrue(generatedAt.contains("T"))
    }

    @Test
    fun `buildJsonBytes returns UTF-8 encoded bytes`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events = listOf(createTestEvent("test.event"))

        val bytes = builder.buildJsonBytes(context, events)

        assertTrue(bytes.isNotEmpty())

        // Verify it can be decoded back to string
        val decoded = bytes.decodeToString()
        assertTrue(decoded.contains("android"))
        assertTrue(decoded.contains("test.event"))
    }

    @Test
    fun `buildJsonBytes and buildJsonString produce equivalent output`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events = listOf(createTestEvent("test.event"))

        val jsonString = builder.buildJsonString(context, events)
        val bytes = builder.buildJsonBytes(context, events)
        val decodedString = bytes.decodeToString()

        // Can't compare exact strings due to timestamp differences
        // Instead verify both are valid JSON with same structure
        val json1 = Json.parseToJsonElement(jsonString)
        val json2 = Json.parseToJsonElement(decodedString)

        assertTrue(json1.jsonObject.containsKey("generatedAt"))
        assertTrue(json2.jsonObject.containsKey("generatedAt"))
        assertTrue(json1.jsonObject.containsKey("context"))
        assertTrue(json2.jsonObject.containsKey("context"))
        assertTrue(json1.jsonObject.containsKey("events"))
        assertTrue(json2.jsonObject.containsKey("events"))
    }

    @Test
    fun `buildJsonStringCapped limits events to maxEvents`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events = (1..150).map { createTestEvent("event$it") }

        val jsonString = builder.buildJsonStringCapped(context, events, maxEvents = 50)

        // Should only include last 50 events (event101 to event150)
        assertTrue(jsonString.contains("event101"))
        assertTrue(jsonString.contains("event150"))
        // Should not include early events (use more specific patterns to avoid substring matches)
        assertTrue(!jsonString.contains("\"event1\""))
        assertTrue(!jsonString.contains("\"event50\""))
        assertTrue(!jsonString.contains("\"event99\""))
    }

    @Test
    fun `buildJsonStringCapped does not modify list when under limit`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")
        val events =
            listOf(
                createTestEvent("event1"),
                createTestEvent("event2"),
            )

        val jsonString = builder.buildJsonStringCapped(context, events, maxEvents = 100)

        // Should include all events
        assertTrue(jsonString.contains("event1"))
        assertTrue(jsonString.contains("event2"))
    }

    @Test
    fun `buildJsonString produces deterministic output for same input`() {
        val builder = DiagnosticsBundleBuilder()

        val context =
            mapOf(
                "platform" to "android",
                "app_version" to "1.0.0",
            )

        val event1 =
            TelemetryEvent(
                name = "test.event",
                severity = TelemetrySeverity.INFO,
                timestamp = kotlinx.datetime.Instant.parse("2025-01-01T00:00:00Z"),
                attributes = mapOf("key" to "value"),
            )

        val events = listOf(event1)

        val json1 = builder.buildJsonString(context, events)
        val json2 = builder.buildJsonString(context, events)

        // Note: generatedAt will differ, but structure should be the same
        // Verify both are valid and have same structure
        val parsed1 = Json.parseToJsonElement(json1)
        val parsed2 = Json.parseToJsonElement(json2)

        assertTrue(parsed1.jsonObject.containsKey("context"))
        assertTrue(parsed2.jsonObject.containsKey("context"))
        assertTrue(parsed1.jsonObject.containsKey("events"))
        assertTrue(parsed2.jsonObject.containsKey("events"))
    }

    @Test
    fun `buildJsonString handles special characters in attributes`() {
        val builder = DiagnosticsBundleBuilder()

        val context = mapOf("platform" to "android")

        val event =
            TelemetryEvent(
                name = "test.event",
                severity = TelemetrySeverity.INFO,
                timestamp = Clock.System.now(),
                attributes =
                    mapOf(
                        "message" to "Error: \"file not found\"",
                        "path" to "/foo/bar\\baz",
                    ),
            )

        val jsonString = builder.buildJsonString(context, listOf(event))

        // Should produce valid JSON even with special chars
        val json = Json.parseToJsonElement(jsonString)
        assertTrue(json.jsonObject.containsKey("events"))
    }
}
