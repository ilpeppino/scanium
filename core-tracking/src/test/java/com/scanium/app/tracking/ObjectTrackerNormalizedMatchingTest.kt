package com.scanium.app.tracking

import com.scanium.app.ml.ItemCategory
import com.scanium.app.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectTrackerNormalizedMatchingTest {

    @Test
    fun iou_identical_normalized_boxes_is_one() {
        val tracker = ObjectTracker(
            config = TrackerConfig(
                minFramesToConfirm = 2,
                minConfidence = 0f
            )
        )
        val box = NormalizedRect(0.1f, 0.1f, 0.4f, 0.4f)

        val firstFrameConfirmed = tracker.processFrame(listOf(detection(null, box)))
        assertEquals(0, firstFrameConfirmed.size)

        val secondFrameConfirmed = tracker.processFrame(listOf(detection(null, box)))
        assertEquals(1, secondFrameConfirmed.size)
        assertEquals(1, tracker.getStats().activeCandidates)
    }

    @Test
    fun iou_disjoint_boxes_results_in_no_match_or_low_score() {
        val tracker = ObjectTracker(
            config = TrackerConfig(
                minFramesToConfirm = 1,
                minConfidence = 0f
            )
        )
        val candidateBox = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f)
        val farDetectionBox = NormalizedRect(0.8f, 0.8f, 0.9f, 0.9f)

        tracker.processFrame(listOf(detection(null, candidateBox)))
        val secondFrameConfirmed = tracker.processFrame(listOf(detection(null, farDetectionBox)))

        assertEquals(1, secondFrameConfirmed.size) // A new candidate should be created and confirmed
        assertEquals(2, tracker.getStats().activeCandidates)
    }

    @Test
    fun selects_best_candidate_when_two_candidates_exist() {
        val tracker = ObjectTracker(
            config = TrackerConfig(
                minFramesToConfirm = 2,
                minConfidence = 0f
            )
        )
        val nearBox = NormalizedRect(0.2f, 0.2f, 0.5f, 0.5f)
        val farBox = NormalizedRect(0.7f, 0.7f, 0.9f, 0.9f)

        tracker.processFrame(
            listOf(
                detection(trackingId = "near", box = nearBox),
                detection(trackingId = "far", box = farBox)
            )
        )

        val detectionNearCandidate1 = NormalizedRect(0.21f, 0.21f, 0.51f, 0.51f)
        val confirmed = tracker.processFrame(listOf(detection(trackingId = null, box = detectionNearCandidate1)))

        assertEquals(1, confirmed.size)
        assertEquals("near", confirmed.first().internalId)
    }

    private fun detection(
        trackingId: String?,
        box: NormalizedRect,
        confidence: Float = 0.9f
    ): DetectionInfo {
        return DetectionInfo(
            trackingId = trackingId,
            boundingBox = box,
            confidence = confidence,
            category = ItemCategory.UNKNOWN,
            labelText = "label",
            thumbnail = null,
            normalizedBoxArea = box.area
        )
    }
}
