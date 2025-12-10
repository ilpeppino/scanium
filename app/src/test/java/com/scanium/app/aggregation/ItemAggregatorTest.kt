package com.scanium.app.aggregation

import android.graphics.RectF
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for ItemAggregator.
 *
 * These tests verify:
 * - Similar detections merge correctly
 * - Distinct detections remain separate
 * - Aggregation works even when trackingId changes
 * - No regression to the "no items appear" bug
 * - Jitter in bounding boxes does not create duplicates
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ItemAggregatorTest {

    private lateinit var aggregator: ItemAggregator

    @Before
    fun setup() {
        // Use default configuration for most tests
        aggregator = ItemAggregator()
    }

    @Test
    fun `test similar detections merge into one item`() {
        // Create two similar detections with different IDs
        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.32f, 0.32f, 0.52f, 0.52f), // Slightly shifted
            confidence = 0.85f
        )

        // Process both detections
        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should return the same aggregated item
        assertEquals("Should merge into same item", item1.aggregatedId, item2.aggregatedId)
        assertEquals("Should have merge count of 2", 2, item1.mergeCount)
        assertEquals("Should track both source IDs", 2, item1.sourceDetectionIds.size)

        // Should only have 1 aggregated item total
        assertEquals("Should have only 1 aggregated item", 1, aggregator.getAllItems().size)
    }

    @Test
    fun `test distinct detections remain separate`() {
        // Create two detections that are clearly different
        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.1f, 0.1f, 0.3f, 0.3f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.HOME_GOOD,
            labelText = "Chair",
            boundingBox = RectF(0.7f, 0.7f, 0.9f, 0.9f), // Far away
            confidence = 0.85f
        )

        // Process both detections
        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should create separate aggregated items
        assertNotEquals("Should create different items", item1.aggregatedId, item2.aggregatedId)
        assertEquals("First item should have merge count of 1", 1, item1.mergeCount)
        assertEquals("Second item should have merge count of 1", 1, item2.mergeCount)

        // Should have 2 aggregated items total
        assertEquals("Should have 2 aggregated items", 2, aggregator.getAllItems().size)
    }

    @Test
    fun `test aggregation works when trackingId changes`() {
        // Simulate ML Kit changing tracking ID for the same physical object
        val detection1 = createTestDetection(
            id = "mlkit_track_123",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "mlkit_track_456", // Different tracking ID
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.31f, 0.31f, 0.51f, 0.51f), // Slightly shifted
            confidence = 0.82f
        )

        val detection3 = createTestDetection(
            id = "mlkit_track_789", // Yet another tracking ID
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.29f, 0.29f, 0.49f, 0.49f), // Shifted back
            confidence = 0.85f
        )

        // Process all detections
        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)
        val item3 = aggregator.processDetection(detection3)

        // All should merge into the same aggregated item
        assertEquals("Should merge despite tracking ID changes", item1.aggregatedId, item2.aggregatedId)
        assertEquals("Should merge despite tracking ID changes", item1.aggregatedId, item3.aggregatedId)
        assertEquals("Should have merge count of 3", 3, item1.mergeCount)

        // Should only have 1 aggregated item
        assertEquals("Should have only 1 aggregated item", 1, aggregator.getAllItems().size)
    }

    @Test
    fun `test bounding box jitter does not create duplicates`() {
        // Simulate camera movement causing slight bounding box shifts
        val baseBox = RectF(0.4f, 0.4f, 0.6f, 0.6f)
        val detections = mutableListOf<ScannedItem>()

        // Create 10 detections with slight jitter
        for (i in 0 until 10) {
            val jitterX = (i % 3 - 1) * 0.02f // Oscillate -0.02, 0, +0.02
            val jitterY = (i % 2) * 0.01f - 0.005f // Oscillate -0.005, +0.005

            val detection = createTestDetection(
                id = "det_$i",
                category = ItemCategory.ELECTRONICS,
                labelText = "Phone",
                boundingBox = RectF(
                    baseBox.left + jitterX,
                    baseBox.top + jitterY,
                    baseBox.right + jitterX,
                    baseBox.bottom + jitterY
                ),
                confidence = 0.75f + (i * 0.01f) // Slightly increasing confidence
            )
            detections.add(detection)
        }

        // Process all detections
        val aggregatedItems = detections.map { aggregator.processDetection(it) }

        // Should all merge into one item
        val uniqueIds = aggregatedItems.map { it.aggregatedId }.toSet()
        assertEquals("Jitter should not create multiple items", 1, uniqueIds.size)
        assertEquals("Should have only 1 aggregated item", 1, aggregator.getAllItems().size)
        assertEquals("Should have merge count of 10", 10, aggregatedItems.first().mergeCount)
    }

    @Test
    fun `test items always appear - no empty result bug`() {
        // This test verifies we don't have the "no items appear" failure mode

        val detection = createTestDetection(
            id = "det_1",
            category = ItemCategory.FOOD,
            labelText = "Apple",
            boundingBox = RectF(0.2f, 0.2f, 0.4f, 0.4f),
            confidence = 0.7f
        )

        // Process detection
        val item = aggregator.processDetection(detection)

        // Item should always be created
        assertNotNull("Item should be created", item)
        assertEquals("Should have 1 aggregated item", 1, aggregator.getAllItems().size)
        assertEquals("Should have 1 ScannedItem for UI", 1, aggregator.getScannedItems().size)
    }

    @Test
    fun `test different categories remain separate even when spatially close`() {
        // Two items of different categories at similar positions
        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.ELECTRONICS,
            labelText = "Phone",
            boundingBox = RectF(0.32f, 0.32f, 0.52f, 0.52f), // Very close spatially
            confidence = 0.85f
        )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should remain separate due to category mismatch
        assertNotEquals("Different categories should remain separate", item1.aggregatedId, item2.aggregatedId)
        assertEquals("Should have 2 aggregated items", 2, aggregator.getAllItems().size)
    }

    @Test
    fun `test label similarity matching`() {
        // Test that similar labels match
        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "T-Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.FASHION,
            labelText = "T-shirt", // Case difference
            boundingBox = RectF(0.31f, 0.31f, 0.51f, 0.51f),
            confidence = 0.82f
        )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should merge despite case difference
        assertEquals("Should merge with case-insensitive label match", item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun `test size difference threshold`() {
        // Test that items with significantly different sizes remain separate
        val config = AggregationConfig(
            maxSizeDifferenceRatio = 0.3f // Only 30% size difference allowed
        )
        val aggregator = ItemAggregator(config)

        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f), // Area: 0.04
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.7f, 0.7f), // Area: 0.16 (4x larger)
            confidence = 0.82f
        )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should remain separate due to size difference
        assertNotEquals("Large size difference should keep items separate", item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun `test distance threshold`() {
        // Test that items far apart remain separate
        val config = AggregationConfig(
            maxCenterDistanceRatio = 0.1f // Only 10% of frame diagonal
        )
        val aggregator = ItemAggregator(config)

        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.1f, 0.1f, 0.3f, 0.3f),
            confidence = 0.8f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.6f, 0.6f, 0.8f, 0.8f), // Far away
            confidence = 0.82f
        )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Should remain separate due to distance
        assertNotEquals("Far distance should keep items separate", item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun `test confidence updates correctly`() {
        val detection1 = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.7f
        )

        val detection2 = createTestDetection(
            id = "det_2",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.31f, 0.31f, 0.51f, 0.51f),
            confidence = 0.9f // Higher confidence
        )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        // Max confidence should update
        assertEquals("Max confidence should update", 0.9f, item1.maxConfidence, 0.001f)

        // Average confidence should be calculated
        val expectedAvg = (0.7f + 0.9f) / 2f
        assertEquals("Average confidence should be correct", expectedAvg, item1.averageConfidence, 0.001f)
    }

    @Test
    fun `test stale item removal`() {
        val detection = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        val item = aggregator.processDetection(detection)

        // Should have 1 item
        assertEquals("Should have 1 item", 1, aggregator.getAllItems().size)

        // Wait briefly
        Thread.sleep(100)

        // Remove stale items (with 50ms threshold)
        val removed = aggregator.removeStaleItems(maxAgeMs = 50)

        // Should remove the item
        assertEquals("Should remove 1 stale item", 1, removed)
        assertEquals("Should have 0 items after removal", 0, aggregator.getAllItems().size)
    }

    @Test
    fun `test reset clears all items`() {
        // Add multiple items
        for (i in 0 until 5) {
            val detection = createTestDetection(
                id = "det_$i",
                category = ItemCategory.FASHION,
                labelText = "Item $i",
                boundingBox = RectF(0.1f * i, 0.1f, 0.2f * i + 0.2f, 0.3f),
                confidence = 0.8f
            )
            aggregator.processDetection(detection)
        }

        // Should have 5 items
        assertEquals("Should have 5 items", 5, aggregator.getAllItems().size)

        // Reset
        aggregator.reset()

        // Should have 0 items
        assertEquals("Should have 0 items after reset", 0, aggregator.getAllItems().size)
    }

    @Test
    fun `test batch processing`() {
        val detections = listOf(
            createTestDetection("det_1", ItemCategory.FASHION, "Shirt", RectF(0.1f, 0.1f, 0.3f, 0.3f)),
            createTestDetection("det_2", ItemCategory.FASHION, "Shirt", RectF(0.12f, 0.12f, 0.32f, 0.32f)),
            createTestDetection("det_3", ItemCategory.ELECTRONICS, "Phone", RectF(0.6f, 0.6f, 0.8f, 0.8f)),
            createTestDetection("det_4", ItemCategory.ELECTRONICS, "Phone", RectF(0.61f, 0.61f, 0.81f, 0.81f))
        )

        val aggregatedItems = aggregator.processDetections(detections)

        // Should have 4 items returned (but only 2 unique aggregated items)
        assertEquals("Should return 4 items", 4, aggregatedItems.size)

        // Should have 2 unique aggregated items (1 shirt, 1 phone)
        assertEquals("Should have 2 aggregated items", 2, aggregator.getAllItems().size)

        val stats = aggregator.getStats()
        assertEquals("Should have 2 total merges", 2, stats.totalMerges)
    }

    @Test
    fun `test statistics calculation`() {
        // Create items with different merge counts
        val detection1 = createTestDetection("det_1", ItemCategory.FASHION, "Shirt", RectF(0.1f, 0.1f, 0.3f, 0.3f))
        val detection2 = createTestDetection("det_2", ItemCategory.FASHION, "Shirt", RectF(0.11f, 0.11f, 0.31f, 0.31f))
        val detection3 = createTestDetection("det_3", ItemCategory.FASHION, "Shirt", RectF(0.12f, 0.12f, 0.32f, 0.32f))
        val detection4 = createTestDetection("det_4", ItemCategory.ELECTRONICS, "Phone", RectF(0.6f, 0.6f, 0.8f, 0.8f))

        aggregator.processDetection(detection1)
        aggregator.processDetection(detection2)
        aggregator.processDetection(detection3)
        aggregator.processDetection(detection4)

        val stats = aggregator.getStats()

        assertEquals("Should have 2 total items", 2, stats.totalItems)
        assertEquals("Should have 2 total merges", 2, stats.totalMerges)
        assertEquals("Average merges per item should be 1.0", 1.0f, stats.averageMergesPerItem, 0.001f)
    }

    @Test
    fun `test conversion to ScannedItem`() {
        val detection = createTestDetection(
            id = "det_1",
            category = ItemCategory.FASHION,
            labelText = "Shirt",
            boundingBox = RectF(0.3f, 0.3f, 0.5f, 0.5f),
            confidence = 0.8f
        )

        aggregator.processDetection(detection)

        val scannedItems = aggregator.getScannedItems()

        assertEquals("Should have 1 ScannedItem", 1, scannedItems.size)

        val scannedItem = scannedItems.first()
        assertTrue("ID should start with agg_", scannedItem.id.startsWith("agg_"))
        assertEquals("Category should match", ItemCategory.FASHION, scannedItem.category)
        assertEquals("Label should match", "Shirt", scannedItem.labelText)
        assertEquals("Confidence should match", 0.8f, scannedItem.confidence, 0.001f)
    }

    // Helper function to create test detections
    private fun createTestDetection(
        id: String,
        category: ItemCategory,
        labelText: String,
        boundingBox: RectF,
        confidence: Float = 0.8f
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null, // Not needed for logic tests
            category = category,
            priceRange = Pair(10.0, 50.0),
            confidence = confidence,
            boundingBox = boundingBox,
            labelText = labelText
        )
    }
}
