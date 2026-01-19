package com.scanium.test

import com.scanium.core.models.ml.ItemCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for test utility builders.
 *
 * These tests validate that the test-utils infrastructure works correctly.
 */
class BuildersTest {
    @Test
    fun testNormalizedRect_createsValidRect() {
        val rect = testNormalizedRect(0.1f, 0.2f, 0.5f, 0.8f)

        assertEquals(0.1f, rect.left)
        assertEquals(0.2f, rect.top)
        assertEquals(0.5f, rect.right)
        assertEquals(0.8f, rect.bottom)
        assertEquals(0.4f, rect.width)
        assertEquals(0.6f, rect.height)
        assertEquals(0.24f, rect.area, 0.001f)
    }

    @Test
    fun testCenteredRect_createsCenteredSquare() {
        val rect = testCenteredRect(size = 0.4f)

        // Should be centered at (0.5, 0.5)
        assertEquals(0.3f, rect.left, 0.001f)
        assertEquals(0.3f, rect.top, 0.001f)
        assertEquals(0.7f, rect.right, 0.001f)
        assertEquals(0.7f, rect.bottom, 0.001f)
        assertEquals(0.4f, rect.width, 0.001f)
        assertEquals(0.4f, rect.height, 0.001f)
    }

    @Test
    fun testDetectionInfo_createsValidDetection() {
        val detection =
            testDetectionInfo(
                trackingId = "test_123",
                boundingBox = testNormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
                confidence = 0.85f,
                category = ItemCategory.FASHION,
                labelText = "Test Item",
            )

        assertEquals("test_123", detection.trackingId)
        assertEquals(0.85f, detection.confidence)
        assertEquals(ItemCategory.FASHION, detection.category)
        assertEquals("Test Item", detection.labelText)
        assertNotNull(detection.boundingBox)
    }

    @Test
    fun testDetectionInfo_defaultValues() {
        val detection = testDetectionInfo()

        assertNotNull(detection.trackingId)
        assertEquals(0.7f, detection.confidence) // Default
        assertEquals(ItemCategory.FASHION, detection.category) // Default
        assertEquals("Test Item", detection.labelText) // Default
    }

    @Test
    fun testScannedItem_createsValidItem() {
        val item =
            testScannedItem(
                id = "item_456",
                category = ItemCategory.ELECTRONICS,
                labelText = "Laptop",
                confidence = 0.92f,
            )

        assertEquals("item_456", item.aggregatedId)
        assertEquals(ItemCategory.ELECTRONICS, item.category)
        assertEquals("Laptop", item.labelText)
        assertEquals(0.92f, item.confidence)
        assertEquals(1, item.mergeCount) // Default
    }

    @Test
    fun testImageRef_createsValidImage() {
        val image = testImageRef(width = 1920, height = 1080)

        assertEquals(1920, image.width)
        assertEquals(1080, image.height)
        assertEquals("image/jpeg", image.mimeType)
        assertTrue(image.bytes.isNotEmpty())
    }

    @Test
    fun testDetectionGrid_createsMultipleDetections() {
        val detections = testDetectionGrid(count = 4)

        assertEquals(4, detections.size)

        // Verify all detections have unique IDs
        val trackingIds = detections.mapNotNull { it.trackingId }
        assertEquals(4, trackingIds.toSet().size, "All tracking IDs should be unique")

        // Verify detections are spatially separated
        for (i in detections.indices) {
            for (j in i + 1 until detections.size) {
                val box1 = detections[i].boundingBox
                val box2 = detections[j].boundingBox

                // Boxes should not be identical
                assertTrue(
                    box1.left != box2.left || box1.top != box2.top,
                    "Detections $i and $j should have different positions",
                )
            }
        }
    }
}
