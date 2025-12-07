package com.example.objecta.tracking

import android.graphics.RectF
import com.example.objecta.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the complete object tracking pipeline.
 *
 * Tests realistic scenarios of multi-frame object detection and tracking,
 * including de-duplication, confirmation, and expiry behaviors.
 */
class TrackingPipelineIntegrationTest {

    private lateinit var tracker: ObjectTracker

    @Before
    fun setup() {
        tracker = ObjectTracker(
            TrackerConfig(
                minFramesToConfirm = 3,
                minConfidence = 0.4f,
                minBoxArea = 0.001f,
                maxFrameGap = 5,
                minMatchScore = 0.3f,
                expiryFrames = 10
            )
        )
    }

    @Test
    fun `realistic scanning scenario - single object confirmed after movement`() {
        // Simulate scanning a single object that moves slightly across frames

        // Frame 1: Initial detection
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.6f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )
        assertEquals(0, confirmed.size) // Not yet confirmed

        // Frame 2: Object moved slightly
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(105f, 105f, 205f, 205f),
                    confidence = 0.7f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    thumbnail = null,
                    normalizedBoxArea = 0.012f
                )
            )
        )
        assertEquals(0, confirmed.size) // Still not confirmed

        // Frame 3: Object stabilized
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(108f, 108f, 208f, 208f),
                    confidence = 0.8f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    thumbnail = null,
                    normalizedBoxArea = 0.013f
                )
            )
        )

        // Should be confirmed after 3 frames
        assertEquals(1, confirmed.size)
        assertEquals("obj_1", confirmed[0].internalId)
        assertEquals(ItemCategory.FASHION, confirmed[0].category)
        assertEquals(0.8f, confirmed[0].maxConfidence, 0.001f)
        assertEquals(3, confirmed[0].seenCount)

        // Verify average box area
        val expectedAverage = (0.01f + 0.012f + 0.013f) / 3
        assertEquals(expectedAverage, confirmed[0].averageBoxArea, 0.0001f)
    }

    @Test
    fun `realistic scanning scenario - multiple objects confirmed independently`() {
        // Simulate scanning multiple objects in the same frame

        // Frame 1: Detect 3 objects
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.6f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                ),
                DetectionInfo(
                    trackingId = "obj_2",
                    boundingBox = RectF(300f, 100f, 400f, 200f),
                    confidence = 0.5f,
                    category = ItemCategory.ELECTRONICS,
                    labelText = "Phone",
                    thumbnail = null,
                    normalizedBoxArea = 0.008f
                ),
                DetectionInfo(
                    trackingId = "obj_3",
                    boundingBox = RectF(500f, 100f, 600f, 200f),
                    confidence = 0.7f,
                    category = ItemCategory.FOOD,
                    labelText = "Apple",
                    thumbnail = null,
                    normalizedBoxArea = 0.012f
                )
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 2: All objects still present
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo("obj_1", RectF(102f, 102f, 202f, 202f), 0.6f, ItemCategory.FASHION, "Shirt", null, 0.01f),
                DetectionInfo("obj_2", RectF(302f, 102f, 402f, 202f), 0.5f, ItemCategory.ELECTRONICS, "Phone", null, 0.008f),
                DetectionInfo("obj_3", RectF(502f, 102f, 602f, 202f), 0.7f, ItemCategory.FOOD, "Apple", null, 0.012f)
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 3: All objects confirmed
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo("obj_1", RectF(104f, 104f, 204f, 204f), 0.6f, ItemCategory.FASHION, "Shirt", null, 0.01f),
                DetectionInfo("obj_2", RectF(304f, 104f, 404f, 204f), 0.5f, ItemCategory.ELECTRONICS, "Phone", null, 0.008f),
                DetectionInfo("obj_3", RectF(504f, 104f, 604f, 204f), 0.7f, ItemCategory.FOOD, "Apple", null, 0.012f)
            )
        )

        assertEquals(3, confirmed.size)

        val stats = tracker.getStats()
        assertEquals(3, stats.activeCandidates)
        assertEquals(3, stats.confirmedCandidates)
    }

    @Test
    fun `realistic scanning scenario - object lost and found`() {
        // Simulate object temporarily leaving frame and returning

        // Frames 1-2: Object detected
        repeat(2) {
            tracker.processFrame(
                listOf(
                    DetectionInfo(
                        trackingId = "obj_1",
                        boundingBox = RectF(100f, 100f, 200f, 200f),
                        confidence = 0.6f,
                        category = ItemCategory.HOME_GOOD,
                        labelText = "Vase",
                        thumbnail = null,
                        normalizedBoxArea = 0.01f
                    )
                )
            )
        }

        // Frames 3-6: Object not detected (gap of 4 frames, within maxFrameGap = 5)
        repeat(4) {
            tracker.processFrame(emptyList())
        }

        var stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates) // Still tracked

        // Frame 7: Object returns
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.7f,
                    category = ItemCategory.HOME_GOOD,
                    labelText = "Vase",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )

        // Should be confirmed now (3 detections: frames 1, 2, 7)
        assertEquals(1, confirmed.size)
        assertEquals(3, confirmed[0].seenCount)
        assertEquals(0.7f, confirmed[0].maxConfidence, 0.001f)
    }

    @Test
    fun `realistic scanning scenario - object exits and expires`() {
        // Simulate object leaving frame permanently

        // Frames 1-2: Object detected but not confirmed
        repeat(2) {
            tracker.processFrame(
                listOf(
                    DetectionInfo(
                        trackingId = "obj_1",
                        boundingBox = RectF(100f, 100f, 200f, 200f),
                        confidence = 0.6f,
                        category = ItemCategory.PLANT,
                        labelText = "Flower",
                        thumbnail = null,
                        normalizedBoxArea = 0.01f
                    )
                )
            )
        }

        var stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)

        // Frames 3-13: Object not detected (> expiryFrames = 10)
        repeat(11) {
            tracker.processFrame(emptyList())
        }

        stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates) // Expired
        assertEquals(0, stats.confirmedCandidates)
    }

    @Test
    fun `realistic scanning scenario - without trackingId relies on spatial matching`() {
        // Simulate detections without ML Kit trackingId (fallback to spatial matching)

        val boundingBox1 = RectF(100f, 100f, 200f, 200f)
        val boundingBox2 = RectF(105f, 105f, 205f, 205f) // High IoU
        val boundingBox3 = RectF(108f, 108f, 208f, 208f) // High IoU

        // Frame 1: No trackingId
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = null,
                    boundingBox = boundingBox1,
                    confidence = 0.6f,
                    category = ItemCategory.FASHION,
                    labelText = "Dress",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 2: No trackingId, similar position
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = null,
                    boundingBox = boundingBox2,
                    confidence = 0.7f,
                    category = ItemCategory.FASHION,
                    labelText = "Dress",
                    thumbnail = null,
                    normalizedBoxArea = 0.012f
                )
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 3: No trackingId, similar position
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = null,
                    boundingBox = boundingBox3,
                    confidence = 0.8f,
                    category = ItemCategory.FASHION,
                    labelText = "Dress",
                    thumbnail = null,
                    normalizedBoxArea = 0.013f
                )
            )
        )

        // Should be confirmed via spatial matching
        assertEquals(1, confirmed.size)
        assertEquals(3, confirmed[0].seenCount)
        assertEquals(0.8f, confirmed[0].maxConfidence, 0.001f)
        assertEquals(ItemCategory.FASHION, confirmed[0].category)
    }

    @Test
    fun `realistic scanning scenario - category refinement over time`() {
        // Simulate ML Kit refining category classification over frames

        // Frame 1: Low confidence, UNKNOWN category
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.4f,
                    category = ItemCategory.UNKNOWN,
                    labelText = "Unknown",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 2: Higher confidence, category identified as ELECTRONICS
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.6f,
                    category = ItemCategory.ELECTRONICS,
                    labelText = "Laptop",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )
        assertEquals(0, confirmed.size)

        // Frame 3: Highest confidence, category confirmed
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_1",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.8f,
                    category = ItemCategory.ELECTRONICS,
                    labelText = "Laptop",
                    thumbnail = null,
                    normalizedBoxArea = 0.01f
                )
            )
        )

        // Should be confirmed with ELECTRONICS category (highest confidence)
        assertEquals(1, confirmed.size)
        assertEquals(ItemCategory.ELECTRONICS, confirmed[0].category)
        assertEquals("Laptop", confirmed[0].labelText)
        assertEquals(0.8f, confirmed[0].maxConfidence, 0.001f)
    }

    @Test
    fun `realistic scanning scenario - noise filtering`() {
        // Simulate filtering out low-quality detections

        // Frame 1: Good detection
        var confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_good",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.7f,
                    category = ItemCategory.FOOD,
                    labelText = "Banana",
                    thumbnail = null,
                    normalizedBoxArea = 0.015f
                ),
                DetectionInfo(
                    trackingId = "obj_noise1",
                    boundingBox = RectF(300f, 300f, 310f, 310f),
                    confidence = 0.3f, // Too low
                    category = ItemCategory.UNKNOWN,
                    labelText = "Unknown",
                    thumbnail = null,
                    normalizedBoxArea = 0.0001f // Too small
                ),
                DetectionInfo(
                    trackingId = "obj_noise2",
                    boundingBox = RectF(500f, 500f, 502f, 502f),
                    confidence = 0.5f,
                    category = ItemCategory.UNKNOWN,
                    labelText = "Unknown",
                    thumbnail = null,
                    normalizedBoxArea = 0.00001f // Too small
                )
            )
        )

        val stats1 = tracker.getStats()
        assertEquals(1, stats1.activeCandidates) // Only good detection tracked

        // Frames 2-3: Continue good detection
        repeat(2) {
            tracker.processFrame(
                listOf(
                    DetectionInfo(
                        trackingId = "obj_good",
                        boundingBox = RectF(100f, 100f, 200f, 200f),
                        confidence = 0.7f,
                        category = ItemCategory.FOOD,
                        labelText = "Banana",
                        thumbnail = null,
                        normalizedBoxArea = 0.015f
                    )
                )
            )
        }

        // Only the good object should be confirmed
        confirmed = tracker.processFrame(
            listOf(
                DetectionInfo(
                    trackingId = "obj_good",
                    boundingBox = RectF(100f, 100f, 200f, 200f),
                    confidence = 0.7f,
                    category = ItemCategory.FOOD,
                    labelText = "Banana",
                    thumbnail = null,
                    normalizedBoxArea = 0.015f
                )
            )
        )

        // Noise filtered, only 1 confirmed
        val stats = tracker.getStats()
        assertEquals(1, stats.confirmedCandidates)
    }

    @Test
    fun `realistic scanning scenario - reset between scan sessions`() {
        // Simulate user starting a new scan session

        // Session 1: Scan and confirm object
        repeat(3) {
            tracker.processFrame(
                listOf(
                    DetectionInfo(
                        trackingId = "obj_1",
                        boundingBox = RectF(100f, 100f, 200f, 200f),
                        confidence = 0.6f,
                        category = ItemCategory.FASHION,
                        labelText = "Hat",
                        thumbnail = null,
                        normalizedBoxArea = 0.01f
                    )
                )
            )
        }

        var stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(1, stats.confirmedCandidates)
        assertEquals(3, stats.currentFrame)

        // User stops scanning and resets
        tracker.reset()

        stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates)
        assertEquals(0, stats.confirmedCandidates)
        assertEquals(0, stats.currentFrame)

        // Session 2: Same object scanned again should create new confirmation
        repeat(3) {
            tracker.processFrame(
                listOf(
                    DetectionInfo(
                        trackingId = "obj_1", // Same trackingId
                        boundingBox = RectF(100f, 100f, 200f, 200f),
                        confidence = 0.6f,
                        category = ItemCategory.FASHION,
                        labelText = "Hat",
                        thumbnail = null,
                        normalizedBoxArea = 0.01f
                    )
                )
            )
        }

        stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(1, stats.confirmedCandidates) // Confirmed again in new session
        assertEquals(3, stats.currentFrame)
    }
}
