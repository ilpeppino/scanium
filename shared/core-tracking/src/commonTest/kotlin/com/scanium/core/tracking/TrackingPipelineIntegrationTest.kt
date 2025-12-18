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
        // Use lenient confirmation so each frame emits a detection into the aggregator.
        tracker = ObjectTracker(TestFixtures.TrackerConfigs.lenient)
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
        // ObjectTracker only emits newly confirmed candidates; subsequent frames don't re-emit.
        assertEquals(0, stats.totalMerges)
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
