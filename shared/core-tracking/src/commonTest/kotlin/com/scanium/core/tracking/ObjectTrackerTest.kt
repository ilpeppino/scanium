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
}
