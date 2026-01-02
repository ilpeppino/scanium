package com.scanium.app.camera

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ScanMode enum.
 *
 * Tests that all three modes are properly defined and have correct display names.
 */
class ScanModeTest {
    @Test
    fun `verify all three scan modes exist`() {
        val modes = ScanMode.values()
        assertEquals("Should have exactly 3 scan modes", 3, modes.size)
    }

    @Test
    fun `verify OBJECT_DETECTION mode properties`() {
        val mode = ScanMode.OBJECT_DETECTION
        assertEquals("Items", mode.displayName)
    }

    @Test
    fun `verify BARCODE mode properties`() {
        val mode = ScanMode.BARCODE
        assertEquals("Barcode", mode.displayName)
    }

    @Test
    fun `verify DOCUMENT_TEXT mode properties`() {
        val mode = ScanMode.DOCUMENT_TEXT
        assertEquals("Document", mode.displayName)
    }

    @Test
    fun `verify mode order in enum`() {
        val modes = ScanMode.values()
        assertEquals(ScanMode.OBJECT_DETECTION, modes[0])
        assertEquals(ScanMode.BARCODE, modes[1])
        assertEquals(ScanMode.DOCUMENT_TEXT, modes[2])
    }

    @Test
    fun `verify mode valueOf works for all modes`() {
        assertEquals(ScanMode.OBJECT_DETECTION, ScanMode.valueOf("OBJECT_DETECTION"))
        assertEquals(ScanMode.BARCODE, ScanMode.valueOf("BARCODE"))
        assertEquals(ScanMode.DOCUMENT_TEXT, ScanMode.valueOf("DOCUMENT_TEXT"))
    }

    @Test
    fun `verify all modes have unique display names`() {
        val modes = ScanMode.values()
        val displayNames = modes.map { it.displayName }.toSet()
        assertEquals(
            "All modes should have unique display names",
            modes.size,
            displayNames.size,
        )
    }
}
