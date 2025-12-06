package com.example.objecta.items

import com.example.objecta.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ScannedItem data class.
 *
 * Tests the extended model with recognizedText and barcodeValue fields.
 */
class ScannedItemTest {

    @Test
    fun `create basic scanned item without optional fields`() {
        val item = ScannedItem(
            id = "test-id",
            thumbnail = null,
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 50.0)
        )

        assertEquals("test-id", item.id)
        assertNull(item.thumbnail)
        assertEquals(ItemCategory.FASHION, item.category)
        assertEquals(Pair(10.0, 50.0), item.priceRange)
        assertNull(item.recognizedText)
        assertNull(item.barcodeValue)
    }

    @Test
    fun `create document scanned item with recognized text`() {
        val recognizedText = "This is a test document with some text."
        val item = ScannedItem(
            id = "doc-id",
            thumbnail = null,
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = recognizedText
        )

        assertEquals("doc-id", item.id)
        assertEquals(ItemCategory.DOCUMENT, item.category)
        assertEquals(recognizedText, item.recognizedText)
        assertNull(item.barcodeValue)
    }

    @Test
    fun `create barcode scanned item with barcode value`() {
        val barcodeValue = "123456789012"
        val item = ScannedItem(
            id = "barcode-id",
            thumbnail = null,
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(5.0, 25.0),
            barcodeValue = barcodeValue
        )

        assertEquals("barcode-id", item.id)
        assertEquals(barcodeValue, item.barcodeValue)
        assertNull(item.recognizedText)
    }

    @Test
    fun `verify formatted price range with whole numbers`() {
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.ELECTRONICS,
            priceRange = Pair(100.0, 500.0)
        )

        assertEquals("€100 - €500", item.formattedPriceRange)
    }

    @Test
    fun `verify formatted price range with decimals rounds correctly`() {
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.FOOD,
            priceRange = Pair(2.99, 15.49)
        )

        assertEquals("€3 - €15", item.formattedPriceRange)
    }

    @Test
    fun `verify formatted price range with zero`() {
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0)
        )

        assertEquals("€0 - €0", item.formattedPriceRange)
    }

    @Test
    fun `verify timestamp defaults to current time`() {
        val beforeTime = System.currentTimeMillis()
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.HOME_GOOD,
            priceRange = Pair(20.0, 80.0)
        )
        val afterTime = System.currentTimeMillis()

        assertTrue(item.timestamp >= beforeTime)
        assertTrue(item.timestamp <= afterTime)
    }

    @Test
    fun `verify custom timestamp is preserved`() {
        val customTimestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.PLANT,
            priceRange = Pair(5.0, 15.0),
            timestamp = customTimestamp
        )

        assertEquals(customTimestamp, item.timestamp)
    }

    @Test
    fun `verify item with both recognizedText and barcodeValue`() {
        // While unusual, the model should support both fields
        val item = ScannedItem(
            id = "hybrid-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = "Some text",
            barcodeValue = "12345"
        )

        assertEquals("Some text", item.recognizedText)
        assertEquals("12345", item.barcodeValue)
    }

    @Test
    fun `verify empty string recognized text is preserved`() {
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = ""
        )

        assertEquals("", item.recognizedText)
    }

    @Test
    fun `verify long recognized text is preserved`() {
        val longText = "A".repeat(1000)
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = longText
        )

        assertEquals(longText, item.recognizedText)
        assertEquals(1000, item.recognizedText?.length)
    }

    @Test
    fun `verify data class copy works with new fields`() {
        val original = ScannedItem(
            id = "original-id",
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 50.0),
            recognizedText = "Original text"
        )

        val copy = original.copy(
            recognizedText = "Updated text",
            barcodeValue = "NEW123"
        )

        assertEquals("original-id", copy.id)
        assertEquals(ItemCategory.FASHION, copy.category)
        assertEquals("Updated text", copy.recognizedText)
        assertEquals("NEW123", copy.barcodeValue)
    }

    @Test
    fun `verify data class equals works with new fields`() {
        val item1 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            timestamp = 1000L,
            recognizedText = "Text"
        )

        val item2 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            timestamp = 1000L,
            recognizedText = "Text"
        )

        assertEquals(item1, item2)
    }

    @Test
    fun `verify data class hashCode works with new fields`() {
        val item1 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            timestamp = 1000L,
            recognizedText = "Text"
        )

        val item2 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            timestamp = 1000L,
            recognizedText = "Text"
        )

        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun `verify items with different recognizedText are not equal`() {
        val item1 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = "Text A"
        )

        val item2 = ScannedItem(
            id = "id1",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = "Text B"
        )

        assertNotEquals(item1, item2)
    }

    @Test
    fun `verify multiline recognized text is preserved`() {
        val multilineText = "Line 1\nLine 2\nLine 3"
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = multilineText
        )

        assertEquals(multilineText, item.recognizedText)
        assertTrue(item.recognizedText?.contains("\n") == true)
    }

    @Test
    fun `verify special characters in recognized text are preserved`() {
        val specialText = "Price: €50.00\nDate: 2024-01-15\nItems: #12345"
        val item = ScannedItem(
            id = "test-id",
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            recognizedText = specialText
        )

        assertEquals(specialText, item.recognizedText)
    }

    @Test
    fun `verify barcode value with various formats`() {
        val barcodeFormats = listOf(
            "123456789012", // EAN-13
            "12345678", // EAN-8
            "https://example.com", // QR code URL
            "VCARD:John Doe" // QR code vCard
        )

        barcodeFormats.forEach { barcodeValue ->
            val item = ScannedItem(
                id = "test-id",
                category = ItemCategory.UNKNOWN,
                priceRange = Pair(0.0, 0.0),
                barcodeValue = barcodeValue
            )

            assertEquals(barcodeValue, item.barcodeValue)
        }
    }
}
