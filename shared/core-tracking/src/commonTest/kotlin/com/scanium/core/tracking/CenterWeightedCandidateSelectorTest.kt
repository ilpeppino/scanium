package com.scanium.core.tracking

import com.scanium.test.testDetectionInfo
import com.scanium.test.testNormalizedRect
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CenterWeightedCandidateSelector.
 *
 * Verifies the center-weighted scoring, gating rules, and stability tracking
 * that fix the issue where distant background objects are added while
 * centered near objects are missed.
 */
class CenterWeightedCandidateSelectorTest {
    private lateinit var selector: CenterWeightedCandidateSelector
    private lateinit var config: CenterWeightedConfig

    @BeforeTest
    fun setup() {
        config =
            CenterWeightedConfig(
                maxCenterDistance = 0.35f,
                minArea = 0.03f,
                minSharpness = 100f,
                sharpnessAreaThreshold = 0.10f,
                highConfidenceOverride = 0.8f,
                minStabilityFrames = 3,
                minStabilityTimeMs = 400L,
            )
        selector = CenterWeightedCandidateSelector(config)
    }

    // ===========================================
    // SCORING TESTS
    // ===========================================

    @Test
    fun centeredCandidateScoresHigherThanEdgeCandidate() {
        // Centered candidate (center = 0.5, 0.5)
        val centeredBox = testNormalizedRect(0.35f, 0.35f, 0.65f, 0.65f)
        val centeredDetection =
            testDetectionInfo(
                trackingId = "centered",
                boundingBox = centeredBox,
                confidence = 0.5f,
                normalizedBoxArea = 0.09f,
// 9% area
            )

        // Edge candidate (center near top-right corner)
        val edgeBox = testNormalizedRect(0.75f, 0.05f, 0.95f, 0.25f)
        val edgeDetection =
            testDetectionInfo(
                trackingId = "edge",
                boundingBox = edgeBox,
                confidence = 0.5f,
                normalizedBoxArea = 0.04f,
// 4% area
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(edgeDetection, centeredDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Centered candidate should be selected
        assertNotNull(result.selectedCandidate)
        assertEquals("centered", result.selectedCandidate?.detection?.trackingId)
    }

    @Test
    fun largerAreaScoresHigher_sameCenter() {
        // Small box
        val smallBox = testNormalizedRect(0.45f, 0.45f, 0.55f, 0.55f) // 1% area
        val smallDetection =
            testDetectionInfo(
                trackingId = "small",
                boundingBox = smallBox,
                confidence = 0.5f,
                normalizedBoxArea = 0.01f,
            )

        // Large box
        val largeBox = testNormalizedRect(0.30f, 0.30f, 0.70f, 0.70f) // 16% area
        val largeDetection =
            testDetectionInfo(
                trackingId = "large",
                boundingBox = largeBox,
                confidence = 0.5f,
                normalizedBoxArea = 0.16f,
            )

        // Use config that allows small boxes for this test
        val permissiveConfig = config.copy(minArea = 0.005f)
        val permissiveSelector = CenterWeightedCandidateSelector(permissiveConfig)

        val result =
            permissiveSelector.selectBestCandidate(
                detections = listOf(smallDetection, largeDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Large candidate should be selected
        assertNotNull(result.selectedCandidate)
        assertEquals("large", result.selectedCandidate?.detection?.trackingId)
    }

    @Test
    fun higherConfidenceScoresHigher_sameCenterAndArea() {
        val box = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f)

        val lowConfDetection =
            testDetectionInfo(
                trackingId = "low_conf",
                boundingBox = box,
                confidence = 0.3f,
                normalizedBoxArea = 0.04f,
            )

        val highConfDetection =
            testDetectionInfo(
                trackingId = "high_conf",
                boundingBox = box,
                confidence = 0.8f,
                normalizedBoxArea = 0.04f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(lowConfDetection, highConfDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // High confidence should win
        assertNotNull(result.selectedCandidate)
        assertEquals("high_conf", result.selectedCandidate?.detection?.trackingId)
    }

    // ===========================================
    // GATING TESTS
    // ===========================================

    @Test
    fun rejectsTooFarFromCenter() {
        // Candidate in corner (center distance ~0.5)
        val cornerBox = testNormalizedRect(0.80f, 0.80f, 0.95f, 0.95f)
        val cornerDetection =
            testDetectionInfo(
                trackingId = "corner",
                boundingBox = cornerBox,
                confidence = 0.5f,
// Not high enough to override
                normalizedBoxArea = 0.04f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(cornerDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Should be rejected
        assertNull(result.selectedCandidate)
        assertEquals(1, result.rejectedCandidates.size)
        assertEquals("center_distance", result.rejectedCandidates.first().reason)
    }

    @Test
    fun highConfidenceOverridesCenterDistanceGate() {
        // Candidate in corner but with very high confidence
        val cornerBox = testNormalizedRect(0.80f, 0.80f, 0.95f, 0.95f)
        val cornerDetection =
            testDetectionInfo(
                trackingId = "corner_high_conf",
                boundingBox = cornerBox,
                confidence = 0.85f,
// Above highConfidenceOverride (0.8)
                normalizedBoxArea = 0.04f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(cornerDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Should be selected despite center distance
        assertNotNull(result.selectedCandidate)
        assertEquals("corner_high_conf", result.selectedCandidate?.detection?.trackingId)
    }

    @Test
    fun rejectsTooSmallArea() {
        // Tiny candidate (distant background object)
        val tinyBox = testNormalizedRect(0.49f, 0.49f, 0.51f, 0.51f)
        val tinyDetection =
            testDetectionInfo(
                trackingId = "tiny",
                boundingBox = tinyBox,
                confidence = 0.6f,
                normalizedBoxArea = 0.0004f,
// 0.04% area - way below 3% threshold
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(tinyDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Should be rejected
        assertNull(result.selectedCandidate)
        assertEquals(1, result.rejectedCandidates.size)
        assertEquals("min_area", result.rejectedCandidates.first().reason)
    }

    @Test
    fun rejectsSmallObjectWhenBlurry() {
        // Small but not tiny object
        val smallBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f)
        val smallDetection =
            testDetectionInfo(
                trackingId = "small",
                boundingBox = smallBox,
                confidence = 0.6f,
                normalizedBoxArea = 0.05f,
// 5% area - above minArea but below sharpnessAreaThreshold
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(smallDetection),
                frameSharpness = 50f,
// Below minSharpness threshold
                currentTimeMs = 1000L,
            )

        // Should be rejected due to blur + small size
        assertNull(result.selectedCandidate)
        assertEquals(1, result.rejectedCandidates.size)
        assertEquals("sharpness_small_object", result.rejectedCandidates.first().reason)
    }

    @Test
    fun allowsLargeObjectWhenBlurry() {
        // Large object - should not require sharpness check
        val largeBox = testNormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
        val largeDetection =
            testDetectionInfo(
                trackingId = "large",
                boundingBox = largeBox,
                confidence = 0.6f,
                normalizedBoxArea = 0.25f,
// 25% area - above sharpnessAreaThreshold
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(largeDetection),
                frameSharpness = 50f,
// Blurry, but object is large
                currentTimeMs = 1000L,
            )

        // Should be selected despite blur
        assertNotNull(result.selectedCandidate)
        assertEquals("large", result.selectedCandidate?.detection?.trackingId)
    }

    // ===========================================
    // STABILITY TESTS
    // ===========================================

    @Test
    fun newCandidateIsNotStable() {
        val detection =
            testDetectionInfo(
                trackingId = "new_candidate",
                boundingBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f),
                confidence = 0.6f,
                normalizedBoxArea = 0.04f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(detection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        assertNotNull(result.selectedCandidate)
        assertFalse(result.selectedCandidate!!.isStable)
        assertEquals(1, result.selectedCandidate!!.consecutiveFrames)
    }

    @Test
    fun candidateBecomesStableAfterMinFrames() {
        val detection =
            testDetectionInfo(
                trackingId = "stable_candidate",
                boundingBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f),
                confidence = 0.6f,
                normalizedBoxArea = 0.04f,
            )

        // Process same detection multiple times to build stability
        var result: SelectionResult? = null
        for (i in 1..3) {
            result =
                selector.selectBestCandidate(
                    detections = listOf(detection),
                    frameSharpness = 200f,
                    currentTimeMs = 1000L + (i * 100),
                )
        }

        assertNotNull(result?.selectedCandidate)
        assertTrue(result!!.selectedCandidate!!.isStable)
        assertEquals(3, result.selectedCandidate!!.consecutiveFrames)
    }

    @Test
    fun candidateBecomesStableAfterMinTime() {
        // Use config with longer frame requirement but short time requirement
        val timeBasedConfig =
            config.copy(
                minStabilityFrames = 10,
// High frame count
                minStabilityTimeMs = 200L,
// Short time
            )
        val timeBasedSelector = CenterWeightedCandidateSelector(timeBasedConfig)

        val detection =
            testDetectionInfo(
                trackingId = "time_stable",
                boundingBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f),
                confidence = 0.6f,
                normalizedBoxArea = 0.04f,
            )

        // First frame
        timeBasedSelector.selectBestCandidate(
            detections = listOf(detection),
            frameSharpness = 200f,
            currentTimeMs = 1000L,
        )

        // Second frame after time threshold
        val result =
            timeBasedSelector.selectBestCandidate(
                detections = listOf(detection),
                frameSharpness = 200f,
                currentTimeMs = 1250L,
// 250ms later, > minStabilityTimeMs
            )

        assertNotNull(result.selectedCandidate)
        assertTrue(result.selectedCandidate!!.isStable) // Stable via time, not frames
    }

    @Test
    fun stabilityResetsOnCandidateChange() {
        val detection1 =
            testDetectionInfo(
                trackingId = "candidate_1",
                boundingBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f),
                confidence = 0.6f,
                normalizedBoxArea = 0.04f,
            )

        val detection2 =
            testDetectionInfo(
                trackingId = "candidate_2",
                boundingBox = testNormalizedRect(0.35f, 0.35f, 0.55f, 0.55f),
                confidence = 0.65f,
// Slightly higher confidence
                normalizedBoxArea = 0.04f,
            )

        // Build up stability for candidate 1
        repeat(2) {
            selector.selectBestCandidate(
                detections = listOf(detection1),
                frameSharpness = 200f,
                currentTimeMs = 1000L + (it * 100),
            )
        }

        // Switch to candidate 2
        val result =
            selector.selectBestCandidate(
                detections = listOf(detection2),
                frameSharpness = 200f,
                currentTimeMs = 1300L,
            )

        // Candidate 2 should be selected but not stable (reset)
        assertNotNull(result.selectedCandidate)
        assertEquals("candidate_2", result.selectedCandidate!!.detection.trackingId)
        assertFalse(result.selectedCandidate!!.isStable)
        assertEquals(1, result.selectedCandidate!!.consecutiveFrames)
    }

    @Test
    fun resetClearsStability() {
        val detection =
            testDetectionInfo(
                trackingId = "candidate",
                boundingBox = testNormalizedRect(0.40f, 0.40f, 0.60f, 0.60f),
                confidence = 0.6f,
                normalizedBoxArea = 0.04f,
            )

        // Build up stability
        repeat(3) {
            selector.selectBestCandidate(
                detections = listOf(detection),
                frameSharpness = 200f,
                currentTimeMs = 1000L + (it * 100),
            )
        }

        // Reset selector
        selector.reset()

        // Same detection should start fresh
        val result =
            selector.selectBestCandidate(
                detections = listOf(detection),
                frameSharpness = 200f,
                currentTimeMs = 2000L,
            )

        assertNotNull(result.selectedCandidate)
        assertFalse(result.selectedCandidate!!.isStable)
        assertEquals(1, result.selectedCandidate!!.consecutiveFrames)
    }

    // ===========================================
    // EDGE CASE TESTS
    // ===========================================

    @Test
    fun emptyDetectionsReturnsNoSelection() {
        val result =
            selector.selectBestCandidate(
                detections = emptyList(),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        assertNull(result.selectedCandidate)
        assertEquals("no_detections", result.selectionReason)
        assertTrue(result.rejectedCandidates.isEmpty())
    }

    @Test
    fun allCandidatesRejectedReturnsAllGated() {
        // All candidates fail gating
        val tinyDetection =
            testDetectionInfo(
                trackingId = "tiny",
                boundingBox = testNormalizedRect(0.49f, 0.49f, 0.51f, 0.51f),
                confidence = 0.5f,
                normalizedBoxArea = 0.001f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(tinyDetection),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        assertNull(result.selectedCandidate)
        assertEquals("all_gated", result.selectionReason)
        assertEquals(1, result.rejectedCandidates.size)
    }

    @Test
    fun selectsFromMultipleCandidates() {
        // Mix of good and bad candidates
        val goodCentered =
            testDetectionInfo(
                trackingId = "good_centered",
                boundingBox = testNormalizedRect(0.35f, 0.35f, 0.65f, 0.65f),
                confidence = 0.6f,
                normalizedBoxArea = 0.09f,
            )

        val tinyBackground =
            testDetectionInfo(
                trackingId = "tiny_bg",
                boundingBox = testNormalizedRect(0.49f, 0.49f, 0.51f, 0.51f),
                confidence = 0.3f,
                normalizedBoxArea = 0.001f,
            )

        val farEdge =
            testDetectionInfo(
                trackingId = "far_edge",
                boundingBox = testNormalizedRect(0.85f, 0.85f, 0.99f, 0.99f),
                confidence = 0.4f,
                normalizedBoxArea = 0.04f,
            )

        val result =
            selector.selectBestCandidate(
                detections = listOf(tinyBackground, farEdge, goodCentered),
                frameSharpness = 200f,
                currentTimeMs = 1000L,
            )

        // Good centered candidate should be selected
        assertNotNull(result.selectedCandidate)
        assertEquals("good_centered", result.selectedCandidate!!.detection.trackingId)

        // Others should be in rejected list
        assertEquals(2, result.rejectedCandidates.size)
    }
}
