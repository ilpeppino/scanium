package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectTrackerGoldenVectorsTest {

    private val defaultConfig = TrackerConfig(
        minFramesToConfirm = 2,
        minConfidence = 0.5f,
        minBoxArea = 0.01f,
        expiryFrames = 5
    )

    @Test
    fun goldenVectors_confirmObjectsWithStableTrackingIds() {
        val tracker = ObjectTracker(config = defaultConfig)

        val frames = listOf(
            GoldenFrameInput(
                detections = listOf(
                    detection(
                        trackingId = "fashion-1",
                        box = NormalizedRect(0.10f, 0.10f, 0.30f, 0.40f),
                        confidence = 0.72f,
                        category = ItemCategory.FASHION,
                        label = "shoe"
                    )
                ),
                expectedConfirmedIds = emptyList(),
                expectedStats = TrackerStats(activeCandidates = 1, confirmedCandidates = 0, currentFrame = 1)
            ),
            GoldenFrameInput(
                detections = listOf(
                    detection(
                        trackingId = "fashion-1",
                        box = NormalizedRect(0.12f, 0.12f, 0.32f, 0.42f),
                        confidence = 0.81f,
                        category = ItemCategory.FASHION,
                        label = "shoe"
                    ),
                    detection(
                        trackingId = "home-1",
                        box = NormalizedRect(0.60f, 0.20f, 0.80f, 0.50f),
                        confidence = 0.78f,
                        category = ItemCategory.HOME_GOOD,
                        label = "lamp"
                    )
                ),
                expectedConfirmedIds = listOf("fashion-1"),
                expectedStats = TrackerStats(activeCandidates = 2, confirmedCandidates = 1, currentFrame = 2)
            ),
            GoldenFrameInput(
                detections = listOf(
                    detection(
                        trackingId = "home-1",
                        box = NormalizedRect(0.62f, 0.22f, 0.82f, 0.52f),
                        confidence = 0.83f,
                        category = ItemCategory.HOME_GOOD,
                        label = "lamp"
                    )
                ),
                expectedConfirmedIds = listOf("home-1"),
                expectedStats = TrackerStats(activeCandidates = 2, confirmedCandidates = 2, currentFrame = 3)
            )
        )

        frames.forEach { frame ->
            val confirmed = tracker.processFrame(frame.detections)

            assertEquals(
                frame.expectedConfirmedIds,
                confirmed.map { it.internalId },
                "Confirmed IDs for frame ${'$'}{frame.expectedStats.currentFrame}"
            )
            assertEquals(frame.expectedStats, tracker.getStats(), "Tracker stats for frame ${'$'}{frame.expectedStats.currentFrame}")
        }
    }

    @Test
    fun goldenVectors_resetClearsSessionState() {
        val tracker = ObjectTracker(config = defaultConfig)

        tracker.processFrame(
            listOf(
                detection(
                    trackingId = "session-target",
                    box = NormalizedRect(0.15f, 0.15f, 0.35f, 0.45f),
                    confidence = 0.70f,
                    category = ItemCategory.FASHION,
                    label = "bag"
                )
            )
        )

        tracker.processFrame(
            listOf(
                detection(
                    trackingId = "session-target",
                    box = NormalizedRect(0.16f, 0.16f, 0.36f, 0.46f),
                    confidence = 0.80f,
                    category = ItemCategory.FASHION,
                    label = "bag"
                )
            )
        )

        assertEquals(
            TrackerStats(activeCandidates = 1, confirmedCandidates = 1, currentFrame = 2),
            tracker.getStats(),
            "Stats before reset should reflect confirmed candidate"
        )

        tracker.reset()

        val postResetConfirmed = tracker.processFrame(
            listOf(
                detection(
                    trackingId = "session-target",
                    box = NormalizedRect(0.20f, 0.20f, 0.40f, 0.50f),
                    confidence = 0.75f,
                    category = ItemCategory.FASHION,
                    label = "bag"
                )
            )
        )

        assertEquals(emptyList<String>(), postResetConfirmed.map { it.internalId }, "No confirmations immediately after reset")
        assertEquals(
            TrackerStats(activeCandidates = 1, confirmedCandidates = 0, currentFrame = 1),
            tracker.getStats(),
            "Stats after reset should start a fresh session"
        )
    }

    private fun detection(
        trackingId: String,
        box: NormalizedRect,
        confidence: Float,
        category: ItemCategory,
        label: String,
    ): DetectionInfo {
        return DetectionInfo(
            trackingId = trackingId,
            boundingBox = box,
            confidence = confidence,
            category = category,
            labelText = label,
            thumbnail = null,
            normalizedBoxArea = box.area,
            boundingBoxNorm = box
        )
    }

    private data class GoldenFrameInput(
        val detections: List<DetectionInfo>,
        val expectedConfirmedIds: List<String>,
        val expectedStats: TrackerStats,
    )
}
