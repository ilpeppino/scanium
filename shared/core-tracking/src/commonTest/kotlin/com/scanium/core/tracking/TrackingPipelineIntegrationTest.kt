package com.scanium.core.tracking

import com.scanium.core.models.ml.ItemCategory
import com.scanium.test.TestFixtures
import com.scanium.test.testDetectionInfo
import com.scanium.test.testScannedItem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingPipelineIntegrationTest {

    private lateinit var tracker: ObjectTracker
    private lateinit var aggregator: ItemAggregator

    @BeforeTest
    fun setup() {
        tracker = ObjectTracker(TestFixtures.TrackerConfigs.default)
        aggregator = ItemAggregator(AggregationPresets.REALTIME)
    }

    @Test
    fun singleObjectFlowsThroughTrackerAndAggregator() {
        val frames = listOf(
            listOf(
                testDetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = TestFixtures.BoundingBoxes.center,
                    confidence = 0.6f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    normalizedBoxArea = 0.01f
                )
            ),
            listOf(
                testDetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.01f),
                    confidence = 0.7f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    normalizedBoxArea = 0.012f
                )
            ),
            listOf(
                testDetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = TestFixtures.BoundingBoxes.slightlyShifted(TestFixtures.BoundingBoxes.center, 0.02f),
                    confidence = 0.8f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    normalizedBoxArea = 0.013f
                )
            )
        )

        frames.forEach { detections ->
            val confirmed = tracker.processFrame(detections)
            confirmed.forEach { aggregator.processDetection(it.toScannedItem()) }
        }

        val stats = aggregator.getStats()
        assertEquals(1, stats.totalItems)
        assertEquals(2, stats.totalMerges) // After three confirmations, two merges occurred
    }

    @Test
    fun multipleObjectsRemainDistinctThroughPipeline() {
        val frame = listOf(
            testDetectionInfo(
                trackingId = "obj_1",
                boundingBox = TestFixtures.BoundingBoxes.topLeft,
                confidence = 0.7f,
                category = ItemCategory.FASHION,
                labelText = "Hat"
            ),
            testDetectionInfo(
                trackingId = "obj_2",
                boundingBox = TestFixtures.BoundingBoxes.bottomRight,
                confidence = 0.75f,
                category = ItemCategory.ELECTRONICS,
                labelText = "Phone"
            )
        )

        repeat(3) {
            val confirmed = tracker.processFrame(frame)
            confirmed.forEach { aggregator.processDetection(it.toScannedItem()) }
        }

        val stats = aggregator.getStats()
        assertEquals(2, stats.totalItems)
        assertEquals(2, aggregator.getScannedItems().size)
    }

    private fun ObjectCandidate.toScannedItem(): com.scanium.core.models.items.ScannedItem {
        return testScannedItem(
            id = internalId,
            category = category,
            labelText = labelText,
            confidence = maxConfidence,
            boundingBox = boundingBox
        )
    }
}
