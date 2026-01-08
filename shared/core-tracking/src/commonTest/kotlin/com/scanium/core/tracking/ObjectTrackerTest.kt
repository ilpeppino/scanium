package com.scanium.core.tracking

import com.scanium.core.models.ml.ItemCategory
import com.scanium.test.TestFixtures
import com.scanium.test.assertTrackerStats
import com.scanium.test.testDetectionInfo
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectTrackerTest {
    private lateinit var tracker: ObjectTracker
    private lateinit var config: TrackerConfig

    @BeforeTest
    fun setup() {
        config = TestFixtures.TrackerConfigs.default
        tracker = ObjectTracker(config)
    }

    @Test
    fun firstDetectionCreatesCandidateButDoesNotConfirm() {
        val detections =
            listOf(
                testDetectionInfo(
                    trackingId = "track_1",
                    boundingBox = TestFixtures.BoundingBoxes.center,
                    confidence = 0.5f,
                ),
            )

        val confirmed = tracker.processFrame(detections)

        assertEquals(0, confirmed.size)
        assertTrackerStats(tracker.getStats(), expectedActiveCandidates = 1, expectedConfirmedCandidates = 0)
    }

    @Test
    fun candidateConfirmedAfterMinimumFrames() {
        val trackingId = "track_1"
        val box = TestFixtures.BoundingBoxes.center

        repeat(2) {
            tracker.processFrame(listOf(testDetectionInfo(trackingId = trackingId, boundingBox = box, confidence = 0.5f)))
        }

        val confirmed =
            tracker.processFrame(
                listOf(testDetectionInfo(trackingId = trackingId, boundingBox = box, confidence = 0.5f)),
            )

        assertEquals(1, confirmed.size)
        assertEquals(trackingId, confirmed.first().internalId)
        assertEquals(3, confirmed.first().seenCount)
    }

    @Test
    fun trackingIdMaintainsSameCandidate() {
        val trackingId = "track_1"
        tracker.processFrame(
            listOf(testDetectionInfo(trackingId = trackingId, boundingBox = TestFixtures.BoundingBoxes.topLeft, confidence = 0.4f)),
        )

        tracker.processFrame(
            listOf(testDetectionInfo(trackingId = trackingId, boundingBox = TestFixtures.BoundingBoxes.center, confidence = 0.6f)),
        )

        val confirmed =
            tracker.processFrame(
                listOf(testDetectionInfo(trackingId = trackingId, boundingBox = TestFixtures.BoundingBoxes.center, confidence = 0.7f)),
            )

        assertEquals(1, confirmed.size)
        assertEquals(3, confirmed.first().seenCount)
        assertEquals(0.7f, confirmed.first().maxConfidence)
    }

    @Test
    fun spatialMatchingWithoutTrackingIdWorks() {
        val baseBox = TestFixtures.BoundingBoxes.center
        val shiftedBox = TestFixtures.BoundingBoxes.slightlyShifted(baseBox, delta = 0.015f)

        tracker.processFrame(listOf(testDetectionInfo(trackingId = null, boundingBox = baseBox, confidence = 0.5f)))
        tracker.processFrame(listOf(testDetectionInfo(trackingId = null, boundingBox = shiftedBox, confidence = 0.6f)))

        val confirmed =
            tracker.processFrame(
                listOf(testDetectionInfo(trackingId = null, boundingBox = shiftedBox, confidence = 0.7f)),
            )

        assertEquals(1, confirmed.size)
        assertTrackerStats(tracker.getStats(), expectedActiveCandidates = 1, expectedConfirmedCandidates = 1)
    }

    @Test
    fun candidateExpiresAfterFrameGap() {
        val detection = testDetectionInfo(trackingId = "expired", boundingBox = TestFixtures.BoundingBoxes.center, confidence = 0.6f)
        repeat(3) { tracker.processFrame(listOf(detection)) }

        repeat(config.expiryFrames + 1) { tracker.processFrame(emptyList()) }

        assertTrackerStats(tracker.getStats(), expectedActiveCandidates = 0, expectedConfirmedCandidates = 0)
    }

    @Test
    fun resetClearsSessionState() {
        val detection = testDetectionInfo(trackingId = "reset", boundingBox = TestFixtures.BoundingBoxes.center, confidence = 0.6f)
        repeat(3) { tracker.processFrame(listOf(detection)) }

        tracker.reset()

        assertTrackerStats(tracker.getStats(), expectedActiveCandidates = 0, expectedConfirmedCandidates = 0, expectedCurrentFrame = 0)
    }

    @Test
    fun categoryUpdatesWhenHigherConfidenceArrives() {
        val trackingId = "track_1"
        val box = TestFixtures.BoundingBoxes.center

        tracker.processFrame(
            listOf(
                testDetectionInfo(
                    trackingId = trackingId,
                    boundingBox = box,
                    confidence = 0.4f,
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                ),
            ),
        )

        tracker.processFrame(
            listOf(
                testDetectionInfo(
                    trackingId = trackingId,
                    boundingBox = box,
                    confidence = 0.7f,
                    category = ItemCategory.HOME_GOOD,
                    labelText = "Vase",
                ),
            ),
        )

        val confirmed =
            tracker.processFrame(
                listOf(
                    testDetectionInfo(
                        trackingId = trackingId,
                        boundingBox = box,
                        confidence = 0.5f,
                        category = ItemCategory.HOME_GOOD,
                        labelText = "Vase",
                    ),
                ),
            )

        assertEquals(ItemCategory.HOME_GOOD, confirmed.first().category)
        assertEquals("Vase", confirmed.first().labelText)
    }

    @Test
    fun averageBoxAreaTracksAcrossFrames() {
        val trackingId = "track_area"
        val box = TestFixtures.BoundingBoxes.center

        tracker.processFrame(
            listOf(testDetectionInfo(trackingId = trackingId, boundingBox = box, confidence = 0.5f, normalizedBoxArea = 0.01f)),
        )
        tracker.processFrame(
            listOf(testDetectionInfo(trackingId = trackingId, boundingBox = box, confidence = 0.5f, normalizedBoxArea = 0.02f)),
        )

        val confirmed =
            tracker.processFrame(
                listOf(testDetectionInfo(trackingId = trackingId, boundingBox = box, confidence = 0.5f, normalizedBoxArea = 0.03f)),
            )

        assertEquals(0.02f, confirmed.first().averageBoxArea, 0.001f)
    }

    @Test
    fun frameCounterIncrementsEvenWithEmptyDetections() {
        assertEquals(0, tracker.getStats().currentFrame)
        tracker.processFrame(emptyList())
        tracker.processFrame(emptyList())
        assertEquals(2, tracker.getStats().currentFrame)
    }

    @Test
    fun customConfigChangesConfirmationBehavior() {
        val strictTracker =
            ObjectTracker(
                TrackerConfig(
                    minFramesToConfirm = 5,
                    minConfidence = 0.7f,
                    minBoxArea = 0.01f,
                    maxFrameGap = 2,
                    minMatchScore = 0.5f,
                    expiryFrames = 5,
                ),
            )
        val detection =
            testDetectionInfo(
                trackingId = "strict",
                boundingBox = TestFixtures.BoundingBoxes.center,
                confidence = 0.8f,
                normalizedBoxArea = 0.02f,
            )

        repeat(4) { strictTracker.processFrame(listOf(detection)) }

        val confirmed = strictTracker.processFrame(listOf(detection))
        assertEquals(1, confirmed.size)
        assertEquals(5, confirmed.first().seenCount)
    }

    // =========================================================================
    // Regression tests for scanning mode item addition fix
    // These tests verify the fix for the race condition where candidates were
    // confirmed before guidance reached LOCKED state, causing items to never
    // be added to the item list.
    // =========================================================================

    @Test
    fun getConfirmedCandidatesReturnsAllConfirmed() {
        // Confirm a candidate through the normal process
        val detection = testDetectionInfo(
            trackingId = "confirmed_1",
            boundingBox = TestFixtures.BoundingBoxes.center,
            confidence = 0.5f,
        )
        repeat(config.minFramesToConfirm) { tracker.processFrame(listOf(detection)) }

        // getConfirmedCandidates should return the confirmed candidate
        val allConfirmed = tracker.getConfirmedCandidates()
        assertEquals(1, allConfirmed.size)
        assertEquals("confirmed_1", allConfirmed.first().internalId)
    }

    @Test
    fun getConfirmedCandidatesReturnsEmptyBeforeConfirmation() {
        // Add a detection but don't reach confirmation threshold
        val detection = testDetectionInfo(
            trackingId = "not_confirmed",
            boundingBox = TestFixtures.BoundingBoxes.center,
            confidence = 0.5f,
        )
        tracker.processFrame(listOf(detection))

        // getConfirmedCandidates should return empty
        val allConfirmed = tracker.getConfirmedCandidates()
        assertEquals(0, allConfirmed.size)
    }

    @Test
    fun markCandidateConsumedRemovesFromConfirmed() {
        // Confirm a candidate
        val detection = testDetectionInfo(
            trackingId = "to_consume",
            boundingBox = TestFixtures.BoundingBoxes.center,
            confidence = 0.5f,
        )
        repeat(config.minFramesToConfirm) { tracker.processFrame(listOf(detection)) }

        // Verify it's confirmed
        assertEquals(1, tracker.getConfirmedCandidates().size)

        // Mark as consumed
        tracker.markCandidateConsumed("to_consume")

        // Should no longer appear in getConfirmedCandidates
        assertEquals(0, tracker.getConfirmedCandidates().size)

        // But candidate should still exist for tracking
        assertEquals(1, tracker.getStats().activeCandidates)
    }

    @Test
    fun confirmedCandidatesAvailableAcrossMultipleFrames() {
        // This tests the fix for the race condition:
        // 1. Candidate is confirmed on frame 3
        // 2. processFrame returns the newly confirmed candidate
        // 3. But if guidance isn't LOCKED, we don't add items
        // 4. Later frames should still be able to get the confirmed candidate

        val detection = testDetectionInfo(
            trackingId = "race_condition_test",
            boundingBox = TestFixtures.BoundingBoxes.center,
            confidence = 0.5f,
        )

        // Confirm the candidate
        repeat(config.minFramesToConfirm) { tracker.processFrame(listOf(detection)) }

        // Simulate frames passing (guidance not LOCKED yet)
        // processFrame returns empty for newly confirmed (already confirmed)
        val emptyNewlyConfirmed = tracker.processFrame(listOf(detection))
        assertEquals(0, emptyNewlyConfirmed.size, "processFrame should return empty - candidate already confirmed")

        // But getConfirmedCandidates should still return it
        val allConfirmed = tracker.getConfirmedCandidates()
        assertEquals(1, allConfirmed.size, "getConfirmedCandidates should return the candidate")
        assertEquals("race_condition_test", allConfirmed.first().internalId)

        // More frames pass
        repeat(5) { tracker.processFrame(listOf(detection)) }

        // Still available via getConfirmedCandidates
        assertEquals(1, tracker.getConfirmedCandidates().size, "Candidate should persist until consumed")
    }

    @Test
    fun consumedCandidateNotReturnedAgain() {
        val detection = testDetectionInfo(
            trackingId = "consume_once",
            boundingBox = TestFixtures.BoundingBoxes.center,
            confidence = 0.5f,
        )

        // Confirm the candidate
        repeat(config.minFramesToConfirm) { tracker.processFrame(listOf(detection)) }
        assertEquals(1, tracker.getConfirmedCandidates().size)

        // Consume it (simulating item creation when LOCKED)
        tracker.markCandidateConsumed("consume_once")
        assertEquals(0, tracker.getConfirmedCandidates().size)

        // Continue tracking the same object - it should not be re-confirmed
        repeat(5) { tracker.processFrame(listOf(detection)) }

        // Should still be empty - once consumed, not re-confirmed
        assertEquals(0, tracker.getConfirmedCandidates().size, "Consumed candidate should not be re-confirmed")
    }

    @Test
    fun multipleConfirmedCandidatesAllRetrievable() {
        // Confirm multiple candidates
        val detection1 = testDetectionInfo(
            trackingId = "multi_1",
            boundingBox = TestFixtures.BoundingBoxes.topLeft,
            confidence = 0.5f,
        )
        val detection2 = testDetectionInfo(
            trackingId = "multi_2",
            boundingBox = TestFixtures.BoundingBoxes.bottomRight,
            confidence = 0.6f,
        )

        // Process both detections together
        repeat(config.minFramesToConfirm) {
            tracker.processFrame(listOf(detection1, detection2))
        }

        // Both should be retrievable
        val allConfirmed = tracker.getConfirmedCandidates()
        assertEquals(2, allConfirmed.size)
        assertEquals(setOf("multi_1", "multi_2"), allConfirmed.map { it.internalId }.toSet())
    }
}
