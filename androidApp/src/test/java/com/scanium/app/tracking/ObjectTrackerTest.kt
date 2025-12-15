package com.scanium.app.tracking

import android.graphics.RectF
import com.scanium.app.ml.ItemCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ObjectTracker.
 *
 * Tests the tracking logic including:
 * - Candidate creation and matching
 * - Confirmation thresholds
 * - Expiry logic
 * - Spatial matching fallback
 * - Multi-frame tracking
 */
@RunWith(RobolectricTestRunner::class)
class ObjectTrackerTest {

    private lateinit var tracker: ObjectTracker
    private lateinit var config: TrackerConfig

    @Before
    fun setup() {
        config = TrackerConfig(
            minFramesToConfirm = 3,
            minConfidence = 0.4f,
            minBoxArea = 0.001f,
            maxFrameGap = 5,
            minMatchScore = 0.3f,
            expiryFrames = 10
        )
        tracker = ObjectTracker(config)
    }

    @Test
    fun `first detection creates new candidate but does not confirm`() {
        val detections = listOf(
            createDetectionInfo(
                trackingId = "track_1",
                boundingBox = RectF(100f, 100f, 200f, 200f),
                confidence = 0.5f
            )
        )

        val confirmed = tracker.processFrame(detections)

        // Not confirmed on first frame (needs minFramesToConfirm = 3)
        assertEquals(0, confirmed.size)

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(0, stats.confirmedCandidates)
    }

    @Test
    fun `candidate confirmed after minimum frames threshold`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1
        var confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )
        assertEquals(0, confirmed.size)

        // Frame 2
        confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )
        assertEquals(0, confirmed.size)

        // Frame 3 - should be confirmed now
        confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )
        assertEquals(1, confirmed.size)
        assertEquals(trackingId, confirmed[0].internalId)
        assertEquals(3, confirmed[0].seenCount)

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(1, stats.confirmedCandidates)
    }

    @Test
    fun `candidate not confirmed if confidence too low`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Process 5 frames with low confidence (< minConfidence = 0.4)
        repeat(5) {
            val confirmed = tracker.processFrame(
                listOf(createDetectionInfo(trackingId, boundingBox, confidence = 0.3f))
            )
            assertEquals(0, confirmed.size)
        }

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(0, stats.confirmedCandidates) // Not confirmed
    }

    @Test
    fun `candidate not confirmed if box area too small`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 102f, 102f) // Very small box

        // Process 5 frames
        repeat(5) {
            val confirmed = tracker.processFrame(
                listOf(
                    createDetectionInfo(
                        trackingId,
                        boundingBox,
                        confidence = 0.5f,
                        boxArea = 0.0001f // < minBoxArea = 0.001
                    )
                )
            )
            assertEquals(0, confirmed.size)
        }

        val stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates) // Filtered out immediately
        assertEquals(0, stats.confirmedCandidates)
    }

    @Test
    fun `tracking with trackingId maintains same candidate`() {
        val trackingId = "track_1"
        val boundingBox1 = RectF(100f, 100f, 200f, 200f)
        val boundingBox2 = RectF(150f, 150f, 250f, 250f) // Moved significantly

        // Frame 1
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox1, 0.5f))
        )

        // Frame 2 - same trackingId, different position
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox2, 0.6f))
        )

        // Frame 3
        val confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox2, 0.7f))
        )

        // Should still be confirmed as same object (same trackingId)
        assertEquals(1, confirmed.size)
        assertEquals(trackingId, confirmed[0].internalId)
        assertEquals(3, confirmed[0].seenCount)
        assertEquals(0.7f, confirmed[0].maxConfidence, 0.001f)
    }

    @Test
    fun `spatial matching without trackingId works`() {
        val boundingBox1 = RectF(100f, 100f, 200f, 200f)
        val boundingBox2 = RectF(105f, 105f, 205f, 205f) // Slightly moved (high IoU)

        // Frame 1 - no trackingId
        tracker.processFrame(
            listOf(createDetectionInfo(null, boundingBox1, 0.5f))
        )

        // Frame 2 - no trackingId, similar position (should match spatially)
        tracker.processFrame(
            listOf(createDetectionInfo(null, boundingBox2, 0.6f))
        )

        // Frame 3
        val confirmed = tracker.processFrame(
            listOf(createDetectionInfo(null, boundingBox2, 0.7f))
        )

        // Should be confirmed as same object via spatial matching
        assertEquals(1, confirmed.size)
        assertEquals(3, confirmed[0].seenCount)

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        assertEquals(1, stats.confirmedCandidates)
    }

    @Test
    fun `multiple different objects tracked independently`() {
        val detection1 = createDetectionInfo("track_1", RectF(100f, 100f, 200f, 200f), 0.5f)
        val detection2 = createDetectionInfo("track_2", RectF(300f, 300f, 400f, 400f), 0.6f)

        // Frame 1
        var confirmed = tracker.processFrame(listOf(detection1, detection2))
        assertEquals(0, confirmed.size)

        // Frame 2
        confirmed = tracker.processFrame(listOf(detection1, detection2))
        assertEquals(0, confirmed.size)

        // Frame 3 - both should be confirmed
        confirmed = tracker.processFrame(listOf(detection1, detection2))
        assertEquals(2, confirmed.size)

        val stats = tracker.getStats()
        assertEquals(2, stats.activeCandidates)
        assertEquals(2, stats.confirmedCandidates)
    }

    @Test
    fun `candidate expires after not being seen for threshold frames`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1-3: Create and track candidate
        repeat(3) {
            tracker.processFrame(
                listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
            )
        }

        var stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)

        // Process 11 frames without detecting the object (> expiryFrames = 10)
        repeat(11) {
            tracker.processFrame(emptyList())
        }

        stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates) // Should be expired
    }

    @Test
    fun `candidate not expired if seen within expiry window`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        // Process 5 frames without detection
        repeat(5) {
            tracker.processFrame(emptyList())
        }

        // See it again
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        // Process another 5 frames without detection
        repeat(5) {
            tracker.processFrame(emptyList())
        }

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates) // Should still exist
    }

    @Test
    fun `reset clears all candidates`() {
        val detection1 = createDetectionInfo("track_1", RectF(100f, 100f, 200f, 200f), 0.5f)
        val detection2 = createDetectionInfo("track_2", RectF(300f, 300f, 400f, 400f), 0.6f)

        // Create multiple candidates
        repeat(3) {
            tracker.processFrame(listOf(detection1, detection2))
        }

        var stats = tracker.getStats()
        assertEquals(2, stats.activeCandidates)
        assertEquals(2, stats.confirmedCandidates)

        // Reset tracker
        tracker.reset()

        stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates)
        assertEquals(0, stats.confirmedCandidates)
        assertEquals(0, stats.currentFrame)
    }

    @Test
    fun `confirmed candidate not confirmed again in subsequent frames`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frames 1-3: Confirm candidate
        repeat(3) {
            tracker.processFrame(
                listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
            )
        }

        // Frame 4: Should not return in confirmed list again
        val confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        assertEquals(0, confirmed.size) // Already confirmed, not returned again

        val stats = tracker.getStats()
        assertEquals(1, stats.confirmedCandidates) // Still tracked
    }

    @Test
    fun `tracker handles empty detections gracefully`() {
        val confirmed = tracker.processFrame(emptyList())

        assertEquals(0, confirmed.size)

        val stats = tracker.getStats()
        assertEquals(0, stats.activeCandidates)
        assertEquals(1, stats.currentFrame)
    }

    @Test
    fun `tracker tracks maximum confidence across frames`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1: confidence 0.4
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.4f))
        )

        // Frame 2: confidence 0.7 (higher)
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.7f))
        )

        // Frame 3: confidence 0.5 (lower than max)
        val confirmed = tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        assertEquals(1, confirmed.size)
        assertEquals(0.7f, confirmed[0].maxConfidence, 0.001f) // Should keep max
    }

    @Test
    fun `category updated when higher confidence is observed`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1: FASHION with 0.4 confidence
        tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    boundingBox,
                    0.4f,
                    ItemCategory.FASHION,
                    "Shirt"
                )
            )
        )

        // Frame 2: HOME_GOOD with 0.7 confidence (higher)
        tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    boundingBox,
                    0.7f,
                    ItemCategory.HOME_GOOD,
                    "Vase"
                )
            )
        )

        // Frame 3: Confirm
        val confirmed = tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    boundingBox,
                    0.5f,
                    ItemCategory.HOME_GOOD,
                    "Vase"
                )
            )
        )

        assertEquals(1, confirmed.size)
        assertEquals(ItemCategory.HOME_GOOD, confirmed[0].category)
        assertEquals("Vase", confirmed[0].labelText)
    }

    @Test
    fun `spatial matching fails for distant bounding boxes`() {
        val boundingBox1 = RectF(100f, 100f, 200f, 200f)
        val boundingBox2 = RectF(500f, 500f, 600f, 600f) // Far away, no overlap

        // Frame 1
        tracker.processFrame(
            listOf(createDetectionInfo(null, boundingBox1, 0.5f))
        )

        // Frame 2 - different location, should create new candidate
        tracker.processFrame(
            listOf(createDetectionInfo(null, boundingBox2, 0.5f))
        )

        val stats = tracker.getStats()
        assertEquals(2, stats.activeCandidates) // Two different candidates
    }

    @Test
    fun `frame counter increments correctly`() {
        assertEquals(0, tracker.getStats().currentFrame)

        tracker.processFrame(emptyList())
        assertEquals(1, tracker.getStats().currentFrame)

        tracker.processFrame(emptyList())
        assertEquals(2, tracker.getStats().currentFrame)

        tracker.processFrame(emptyList())
        assertEquals(3, tracker.getStats().currentFrame)
    }

    @Test
    fun `intermittent detection confirmed if within frame gap threshold`() {
        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 200f, 200f)

        // Frame 1
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        // Frames 2-4: Not detected (gap of 3)
        repeat(3) {
            tracker.processFrame(emptyList())
        }

        // Frame 5: Detected again (within maxFrameGap = 5)
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        // Frame 6: Detected
        tracker.processFrame(
            listOf(createDetectionInfo(trackingId, boundingBox, 0.5f))
        )

        val stats = tracker.getStats()
        assertEquals(1, stats.activeCandidates)
        // seenCount should be 3 (frames 1, 5, 6)
    }

    @Test
    fun `custom config affects confirmation behavior`() {
        // Create tracker with stricter config
        val strictConfig = TrackerConfig(
            minFramesToConfirm = 5, // Requires 5 frames
            minConfidence = 0.7f,    // Higher confidence
            minBoxArea = 0.01f,      // Larger box
            maxFrameGap = 2,
            minMatchScore = 0.5f,
            expiryFrames = 5
        )
        val strictTracker = ObjectTracker(strictConfig)

        val trackingId = "track_1"
        val boundingBox = RectF(100f, 100f, 300f, 300f) // Large box

        // Process 4 frames - should not confirm (needs 5)
        repeat(4) {
            val confirmed = strictTracker.processFrame(
                listOf(
                    createDetectionInfo(
                        trackingId,
                        boundingBox,
                        0.8f,
                        boxArea = 0.02f
                    )
                )
            )
            assertEquals(0, confirmed.size)
        }

        // Frame 5 - should confirm now
        val confirmed = strictTracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    boundingBox,
                    0.8f,
                    boxArea = 0.02f
                )
            )
        )

        assertEquals(1, confirmed.size)
        assertEquals(5, confirmed[0].seenCount)
    }

    @Test
    fun `average box area calculated correctly across frames`() {
        val trackingId = "track_1"

        // Frame 1: area 0.01
        tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    RectF(100f, 100f, 200f, 200f),
                    0.5f,
                    boxArea = 0.01f
                )
            )
        )

        // Frame 2: area 0.02
        tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    RectF(100f, 100f, 200f, 200f),
                    0.5f,
                    boxArea = 0.02f
                )
            )
        )

        // Frame 3: area 0.03
        val confirmed = tracker.processFrame(
            listOf(
                createDetectionInfo(
                    trackingId,
                    RectF(100f, 100f, 200f, 200f),
                    0.5f,
                    boxArea = 0.03f
                )
            )
        )

        assertEquals(1, confirmed.size)
        // Average: (0.01 + 0.02 + 0.03) / 3 = 0.02
        assertEquals(0.02f, confirmed[0].averageBoxArea, 0.001f)
    }

    // Helper function
    private fun createDetectionInfo(
        trackingId: String?,
        boundingBox: RectF,
        confidence: Float,
        category: ItemCategory = ItemCategory.FASHION,
        labelText: String = "Test",
        boxArea: Float = 0.01f
    ): DetectionInfo {
        return DetectionInfo(
            trackingId = trackingId,
            boundingBox = boundingBox,
            confidence = confidence,
            category = category,
            labelText = labelText,
            thumbnail = null,
            normalizedBoxArea = boxArea
        )
    }
}
