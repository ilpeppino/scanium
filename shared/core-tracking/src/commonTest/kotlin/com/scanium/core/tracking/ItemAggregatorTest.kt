package com.scanium.core.tracking

import com.scanium.core.models.ml.ItemCategory
import com.scanium.test.TestFixtures
import com.scanium.test.assertItemMatches
import com.scanium.test.testCenteredRect
import com.scanium.test.testScannedItem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ItemAggregatorTest {
    private lateinit var aggregator: ItemAggregator

    @BeforeTest
    fun setup() {
        aggregator = ItemAggregator(AggregationPresets.BALANCED)
    }

    @Test
    fun similarDetectionsMergeIntoSingleItem() {
        val detection1 =
            createDetection(
                id = "det_1",
                category = ItemCategory.FASHION,
                labelText = "Shirt",
                confidence = 0.8f,
                boundingBox = TestFixtures.BoundingBoxes.center,
            )

        val detection2 =
            createDetection(
                id = "det_2",
                category = ItemCategory.FASHION,
                labelText = "Shirt",
                confidence = 0.85f,
                boundingBox = TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertEquals(item1.aggregatedId, item2.aggregatedId)
        assertEquals(2, item1.mergeCount)
        assertEquals(1, aggregator.getAggregatedItems().size)
    }

    @Test
    fun distinctDetectionsRemainSeparate() {
        val detection1 =
            createDetection(
                id = "det_1",
                category = ItemCategory.FASHION,
                labelText = "Shirt",
                confidence = 0.8f,
                boundingBox = TestFixtures.BoundingBoxes.topLeft,
            )
        val detection2 =
            createDetection(
                id = "det_2",
                category = ItemCategory.HOME_GOOD,
                labelText = "Chair",
                confidence = 0.85f,
                boundingBox = TestFixtures.BoundingBoxes.bottomRight,
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertNotEquals(item1.aggregatedId, item2.aggregatedId)
        assertEquals(2, aggregator.getAggregatedItems().size)
    }

    @Test
    fun trackingIdChangesStillMerge() {
        val detections =
            listOf(
                createDetection("mlkit_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.center),
                createDetection(
                    "mlkit_2",
                    ItemCategory.FASHION,
                    "Shirt",
                    0.82f,
                    TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
                ),
                createDetection(
                    "mlkit_3",
                    ItemCategory.FASHION,
                    "Shirt",
                    0.85f,
                    TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, -0.01f),
                ),
            )

        val aggregatedItems = detections.map { aggregator.processDetection(it) }
        val uniqueIds = aggregatedItems.map { it.aggregatedId }.toSet()

        assertEquals(1, uniqueIds.size)
        assertEquals(3, aggregatedItems.first().mergeCount)
    }

    @Test
    fun boundingBoxJitterDoesNotCreateDuplicates() {
        val detections =
            (0 until 8).map { index ->
                val jitter = (index % 3 - 1) * 0.01f
                createDetection(
                    id = "det_$index",
                    category = ItemCategory.ELECTRONICS,
                    labelText = "Phone",
                    confidence = 0.75f + index * 0.01f,
                    boundingBox = TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, jitter),
                )
            }

        val aggregated = detections.map { aggregator.processDetection(it) }
        assertEquals(1, aggregated.map { it.aggregatedId }.toSet().size)
        assertEquals(1, aggregator.getAggregatedItems().size)
        assertEquals(8, aggregated.first().mergeCount)
    }

    @Test
    fun differentCategoriesRemainSeparateEvenIfClose() {
        val detection1 =
            createDetection(
                id = "det_1",
                category = ItemCategory.FASHION,
                labelText = "Shirt",
                confidence = 0.8f,
                boundingBox = TestFixtures.BoundingBoxes.center,
            )
        val detection2 =
            createDetection(
                id = "det_2",
                category = ItemCategory.ELECTRONICS,
                labelText = "Phone",
                confidence = 0.85f,
                boundingBox = TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertNotEquals(item1.aggregatedId, item2.aggregatedId)
        assertEquals(2, aggregator.getAggregatedItems().size)
    }

    @Test
    fun labelSimilarityMatchesRegardlessOfCase() {
        val detection1 = createDetection("det_1", ItemCategory.FASHION, "T-Shirt", 0.8f, TestFixtures.BoundingBoxes.center)
        val detection2 =
            createDetection(
                "det_2",
                ItemCategory.FASHION,
                "t-shirt",
                0.82f,
                TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertEquals(item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun sizeDifferenceThresholdPreventsMerge() {
        val config = AggregationConfig(maxSizeDifferenceRatio = 0.2f)
        val strictAggregator = ItemAggregator(config)

        val detection1 = createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.centerSmall)
        val detection2 = createDetection("det_2", ItemCategory.FASHION, "Shirt", 0.82f, TestFixtures.BoundingBoxes.centerLarge)

        val item1 = strictAggregator.processDetection(detection1)
        val item2 = strictAggregator.processDetection(detection2)

        assertNotEquals(item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun distanceThresholdPreventsMerge() {
        val config = AggregationConfig(maxCenterDistanceRatio = 0.1f)
        val strictAggregator = ItemAggregator(config)

        val detection1 = createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.topLeft)
        val detection2 = createDetection("det_2", ItemCategory.FASHION, "Shirt", 0.82f, TestFixtures.BoundingBoxes.bottomRight)

        val item1 = strictAggregator.processDetection(detection1)
        val item2 = strictAggregator.processDetection(detection2)

        assertNotEquals(item1.aggregatedId, item2.aggregatedId)
    }

    @Test
    fun similarityEqualToThresholdStillMerges() {
        val config =
            AggregationConfig(
                similarityThreshold = 0.25f,
                maxCenterDistanceRatio = 1f,
                maxSizeDifferenceRatio = 0.75f,
                categoryMatchRequired = true,
                labelMatchRequired = false,
                weights =
                    SimilarityWeights(
                        categoryWeight = 0f,
                        labelWeight = 0f,
                        sizeWeight = 1f,
                        distanceWeight = 0f,
                    ),
            )
        val aggregator = ItemAggregator(config, mergePolicy = strictMergePolicy())

        val detection1 =
            createDetection(
                id = "det_1",
                category = ItemCategory.FASHION,
                labelText = "Lamp",
                confidence = 0.8f,
                boundingBox = testCenteredRect(size = 0.2f), // area 0.04
            )
        val detection2 =
            createDetection(
                id = "det_2",
                category = ItemCategory.FASHION,
                labelText = "Lamp",
                confidence = 0.85f,
                boundingBox = testCenteredRect(size = 0.1f), // area 0.01 -> sizeScore 0.25
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertEquals(item1.aggregatedId, item2.aggregatedId)
        assertEquals(1, aggregator.getAggregatedItems().size)
    }

    @Test
    fun similarityJustBelowThresholdDoesNotMerge() {
        val config =
            AggregationConfig(
                similarityThreshold = 0.25f,
                maxCenterDistanceRatio = 1f,
                maxSizeDifferenceRatio = 0.9f,
                categoryMatchRequired = true,
                labelMatchRequired = false,
                weights =
                    SimilarityWeights(
                        categoryWeight = 0f,
                        labelWeight = 0f,
                        sizeWeight = 1f,
                        distanceWeight = 0f,
                    ),
            )
        val aggregator = ItemAggregator(config, mergePolicy = strictMergePolicy())

        val detection1 =
            createDetection(
                id = "det_1",
                category = ItemCategory.FASHION,
                labelText = "Lamp",
                confidence = 0.8f,
                boundingBox = testCenteredRect(size = 0.2f), // area 0.04
            )
        val detection2 =
            createDetection(
                id = "det_2",
                category = ItemCategory.FASHION,
                labelText = "Lamp",
                confidence = 0.85f,
                boundingBox = testCenteredRect(size = 0.08f), // area 0.0064 -> sizeScore 0.16
            )

        val item1 = aggregator.processDetection(detection1)
        val item2 = aggregator.processDetection(detection2)

        assertNotEquals(item1.aggregatedId, item2.aggregatedId)
        assertEquals(2, aggregator.getAggregatedItems().size)
    }

    @Test
    fun confidenceUpdatesAcrossMerges() {
        val detection1 = createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.7f, TestFixtures.BoundingBoxes.center)
        val detection2 =
            createDetection(
                "det_2",
                ItemCategory.FASHION,
                "Shirt",
                0.9f,
                TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
            )

        val item = aggregator.processDetection(detection1)
        aggregator.processDetection(detection2)

        assertEquals(0.9f, item.maxConfidence, 0.0001f)
        assertTrue(item.averageConfidence >= 0.7f)
    }

    @Test
    fun staleItemsAreRemoved() {
        val detection = createDetection("det_stale", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.center)
        aggregator.processDetection(detection)

        val removed = aggregator.removeStaleItems(maxAgeMs = 0)
        assertEquals(1, removed)
        assertTrue(aggregator.getAggregatedItems().isEmpty())
    }

    @Test
    fun resetClearsAllItems() {
        val detections =
            listOf(
                createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.topLeft),
                createDetection("det_2", ItemCategory.HOME_GOOD, "Chair", 0.85f, TestFixtures.BoundingBoxes.bottomRight),
            )
        detections.forEach { aggregator.processDetection(it) }

        aggregator.reset()
        assertTrue(aggregator.getAggregatedItems().isEmpty())
    }

    @Test
    fun batchProcessingReturnsExpectedCounts() {
        val detections =
            listOf(
                createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.topLeft),
                createDetection(
                    "det_2",
                    ItemCategory.FASHION,
                    "Shirt",
                    0.82f,
                    TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.topLeft, 0.01f),
                ),
                createDetection("det_3", ItemCategory.ELECTRONICS, "Phone", 0.9f, TestFixtures.BoundingBoxes.bottomRight),
            )

        val results = aggregator.processDetections(detections)
        assertEquals(3, results.size)
        assertEquals(2, aggregator.getAggregatedItems().size)
    }

    @Test
    fun statisticsCalculatedCorrectly() {
        val detections =
            listOf(
                createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.center),
                createDetection(
                    "det_2",
                    ItemCategory.FASHION,
                    "Shirt",
                    0.82f,
                    TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
                ),
                createDetection("det_3", ItemCategory.ELECTRONICS, "Phone", 0.9f, TestFixtures.BoundingBoxes.bottomRight),
            )
        detections.forEach { aggregator.processDetection(it) }

        val stats = aggregator.getStats()
        assertEquals(2, stats.totalItems)
        assertEquals(1, stats.totalMerges) // One merge between det_1 and det_2
    }

    @Test
    fun conversionToScannedItemPreservesData() {
        val detection = createDetection("det_1", ItemCategory.FASHION, "Shirt", 0.8f, TestFixtures.BoundingBoxes.center)
        aggregator.processDetection(detection)

        val scannedItems = aggregator.getScannedItems()
        assertEquals(1, scannedItems.size)
        assertTrue(scannedItems.first().aggregatedId.startsWith("agg_"))
        assertItemMatches(
            scannedItems.first(),
            expectedCategory = ItemCategory.FASHION,
            expectedLabelText = "Shirt",
            expectedMergeCount = 1,
        )
    }

    private fun createDetection(
        id: String,
        category: ItemCategory,
        labelText: String,
        confidence: Float,
        boundingBox: com.scanium.core.models.geometry.NormalizedRect,
    ) = testScannedItem(
        id = id,
        category = category,
        labelText = labelText,
        confidence = confidence,
        boundingBox = boundingBox,
    )

    private fun strictMergePolicy(): SpatialTemporalMergePolicy {
        return SpatialTemporalMergePolicy(
            SpatialTemporalMergePolicy.MergeConfig(
                minIoU = 0.99f,
                timeWindowMs = 1000L,
                requireCategoryMatch = true,
                useIoU = true,
            ),
        )
    }
}
