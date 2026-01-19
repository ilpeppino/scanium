package com.scanium.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryEventNamingTest {
    @Test
    fun `isValidEventName accepts valid names`() {
        val validNames =
            listOf(
                "scan.started",
                "scan.completed",
                "ml.classification_failed",
                "storage.export_started",
                "error.network_timeout",
                "ui.button_clicked",
                "export.csv_completed",
            )

        validNames.forEach { name ->
            assertTrue(
                TelemetryEventNaming.isValidEventName(name),
                "Expected '$name' to be valid",
            )
        }
    }

    @Test
    fun `isValidEventName rejects invalid names`() {
        val invalidNames =
            listOf(
                // Empty
                "",
                // No action
                "scan",
                // Uppercase
                "SCAN.STARTED",
                // Hyphen instead of dot
                "scan-started",
                // Space
                "scan started",
                // Mixed case
                "Scan.Started",
                // Blank
                "   ",
                // Missing action
                "scan.",
                // Missing prefix
                ".started",
            )

        invalidNames.forEach { name ->
            assertFalse(
                TelemetryEventNaming.isValidEventName(name),
                "Expected '$name' to be invalid",
            )
        }
    }

    @Test
    fun `isValidEventName accepts multi-level names`() {
        val validNames =
            listOf(
                "ml.inference.started",
                "storage.sync.retry.failed",
                "export.csv.batch.completed",
            )

        validNames.forEach { name ->
            assertTrue(
                TelemetryEventNaming.isValidEventName(name),
                "Expected '$name' to be valid",
            )
        }
    }

    @Test
    fun `isValidEventName accepts underscores in action`() {
        val validNames =
            listOf(
                "scan.very_long_action_name",
                "ml.model_inference_completed",
                "error.network_connection_timeout",
            )

        validNames.forEach { name ->
            assertTrue(
                TelemetryEventNaming.isValidEventName(name),
                "Expected '$name' to be valid",
            )
        }
    }

    @Test
    fun `extractPrefix returns correct prefix`() {
        assertEquals("scan", TelemetryEventNaming.extractPrefix("scan.started"))
        assertEquals("ml", TelemetryEventNaming.extractPrefix("ml.classification_failed"))
        assertEquals("storage", TelemetryEventNaming.extractPrefix("storage.export.completed"))
        assertEquals("error", TelemetryEventNaming.extractPrefix("error.timeout"))
    }

    @Test
    fun `extractPrefix returns null for invalid names`() {
        assertEquals(null, TelemetryEventNaming.extractPrefix(""))
        assertEquals(null, TelemetryEventNaming.extractPrefix("scan"))
        assertEquals(null, TelemetryEventNaming.extractPrefix("invalid"))
    }

    @Test
    fun `prefix constants are defined`() {
        assertEquals("scan", TelemetryEventNaming.PREFIX_SCAN)
        assertEquals("ml", TelemetryEventNaming.PREFIX_ML)
        assertEquals("storage", TelemetryEventNaming.PREFIX_STORAGE)
        assertEquals("export", TelemetryEventNaming.PREFIX_EXPORT)
        assertEquals("ui", TelemetryEventNaming.PREFIX_UI)
        assertEquals("error", TelemetryEventNaming.PREFIX_ERROR)
    }

    @Test
    fun `action constants are defined`() {
        assertEquals("started", TelemetryEventNaming.Actions.STARTED)
        assertEquals("completed", TelemetryEventNaming.Actions.COMPLETED)
        assertEquals("failed", TelemetryEventNaming.Actions.FAILED)
        assertEquals("cancelled", TelemetryEventNaming.Actions.CANCELLED)
        assertEquals("paused", TelemetryEventNaming.Actions.PAUSED)
        assertEquals("resumed", TelemetryEventNaming.Actions.RESUMED)
        assertEquals("timeout", TelemetryEventNaming.Actions.TIMEOUT)
        assertEquals("retry", TelemetryEventNaming.Actions.RETRY)
    }
}
