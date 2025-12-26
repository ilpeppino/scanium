package com.scanium.app.camera

import com.scanium.core.models.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for document scanning feature.
 *
 * Tests the integration of document/text mode across different components
 * of the scanning system.
 */
class DocumentScanningIntegrationTest {

    @Test
    fun `verify ScanMode has DOCUMENT_TEXT mode`() {
        val modes = ScanMode.values()

        assertTrue(
            "DOCUMENT_TEXT mode should exist",
            modes.contains(ScanMode.DOCUMENT_TEXT)
        )
    }

    @Test
    fun `verify ItemCategory has DOCUMENT category`() {
        val categories = ItemCategory.values()

        assertTrue(
            "DOCUMENT category should exist",
            categories.contains(ItemCategory.DOCUMENT)
        )
    }

    @Test
    fun `verify all scan modes can be iterated`() {
        val modes = ScanMode.values()
        val modeNames = mutableListOf<String>()

        for (mode in modes) {
            modeNames.add(mode.displayName)
        }

        assertEquals(3, modeNames.size)
        assertTrue(modeNames.contains("Items"))
        assertTrue(modeNames.contains("Barcode"))
        assertTrue(modeNames.contains("Document"))
    }

    @Test
    fun `verify when expression covers all modes`() {
        // This test ensures that when statements in the codebase
        // will handle all three modes correctly
        val modes = ScanMode.values()

        for (mode in modes) {
            val result = when (mode) {
                ScanMode.OBJECT_DETECTION -> "object"
                ScanMode.BARCODE -> "barcode"
                ScanMode.DOCUMENT_TEXT -> "document"
            }

            assertNotNull("When expression should handle $mode", result)
        }
    }

    @Test
    fun `verify mode display names are suitable for UI`() {
        val modes = ScanMode.values()

        for (mode in modes) {
            // Display names should not be empty
            assertTrue(mode.displayName.isNotEmpty())

            // Display names should be reasonably short for UI
            assertTrue(mode.displayName.length <= 20)

            // Display names should not contain special characters
            assertTrue(mode.displayName.matches(Regex("[a-zA-Z ]+")))
        }
    }

    @Test
    fun `verify DOCUMENT category is compatible with pricing`() {
        // Document category should work with pricing system
        val category = ItemCategory.DOCUMENT

        assertEquals("Document", category.displayName)
        assertNotNull(category.name)
        assertEquals("DOCUMENT", category.name)
    }

    @Test
    fun `verify category count includes DOCUMENT`() {
        // Ensure DOCUMENT is included in total category count
        val categories = ItemCategory.values()
        val documentCategory = categories.find { it == ItemCategory.DOCUMENT }

        assertNotNull("DOCUMENT category should be findable", documentCategory)
        assertEquals("Document", documentCategory?.displayName)
    }

    @Test
    fun `verify enum ordinals are sequential`() {
        // Verify that mode ordinals are sequential (important for array indexing)
        assertEquals(0, ScanMode.OBJECT_DETECTION.ordinal)
        assertEquals(1, ScanMode.BARCODE.ordinal)
        assertEquals(2, ScanMode.DOCUMENT_TEXT.ordinal)
    }

    @Test
    fun `verify category ordinals include DOCUMENT before UNKNOWN`() {
        // DOCUMENT should come before UNKNOWN (last category)
        assertTrue(
            "DOCUMENT should come before UNKNOWN",
            ItemCategory.DOCUMENT.ordinal < ItemCategory.UNKNOWN.ordinal
        )
    }

    @Test
    fun `verify mode to category mapping compatibility`() {
        // Verify that document mode maps to document category logically
        val mode = ScanMode.DOCUMENT_TEXT
        val category = ItemCategory.DOCUMENT

        // Both should have "Document" in their names
        assertTrue(mode.displayName.contains("Document", ignoreCase = true))
        assertTrue(category.displayName.contains("Document", ignoreCase = true))
    }

    @Test
    fun `verify all modes have toString implementation`() {
        val modes = ScanMode.values()

        for (mode in modes) {
            val stringRepresentation = mode.toString()

            assertNotNull(stringRepresentation)
            assertTrue(stringRepresentation.isNotEmpty())

            // Enum toString should return the enum constant name
            assertTrue(
                stringRepresentation in listOf(
                    "OBJECT_DETECTION",
                    "BARCODE",
                    "DOCUMENT_TEXT"
                )
            )
        }
    }

    @Test
    fun `verify document mode is last in sequence`() {
        val modes = ScanMode.values()
        val lastMode = modes.last()

        assertEquals(
            "Document mode should be last",
            ScanMode.DOCUMENT_TEXT,
            lastMode
        )
    }

    @Test
    fun `verify mode enum can be used in collections`() {
        val modeSet = setOf(
            ScanMode.OBJECT_DETECTION,
            ScanMode.BARCODE,
            ScanMode.DOCUMENT_TEXT
        )

        assertEquals(3, modeSet.size)
        assertTrue(modeSet.contains(ScanMode.DOCUMENT_TEXT))

        val modeList = listOf(
            ScanMode.OBJECT_DETECTION,
            ScanMode.BARCODE,
            ScanMode.DOCUMENT_TEXT
        )

        assertEquals(3, modeList.size)
        assertEquals(ScanMode.DOCUMENT_TEXT, modeList[2])
    }

    @Test
    fun `verify category enum can be used in when expressions`() {
        val category = ItemCategory.DOCUMENT

        val result = when (category) {
            ItemCategory.FASHION -> "fashion"
            ItemCategory.HOME_GOOD -> "home"
            ItemCategory.FOOD -> "food"
            ItemCategory.PLACE -> "place"
            ItemCategory.PLANT -> "plant"
            ItemCategory.ELECTRONICS -> "electronics"
            ItemCategory.DOCUMENT -> "document"
            ItemCategory.UNKNOWN -> "unknown"
        }

        assertEquals("document", result)
    }
}
