package com.scanium.core.tracking

import com.scanium.core.models.scanning.GuidanceState
import com.scanium.core.models.scanning.ScanRoiConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ScanGuidanceManager.
 *
 * Verifies the guidance state machine behavior:
 * - State transitions based on candidate properties
 * - Lock-on behavior for stable candidates
 * - Dynamic ROI sizing
 * - Hint generation and rate limiting
 */
class ScanGuidanceManagerTest {

    private lateinit var manager: ScanGuidanceManager
    private lateinit var config: ScanGuidanceConfig

    @BeforeTest
    fun setup() {
        config = ScanGuidanceConfig(
            roiConfig = ScanRoiConfig(
                tooCloseAreaThreshold = 0.35f,
                tooFarAreaThreshold = 0.04f
            ),
            maxMotionForStable = 0.3f,
            maxMotionForGood = 0.15f,
            maxMotionForLock = 0.1f,
            minSharpnessForFocus = 50f,
            minSharpnessForGood = 100f,
            minSharpnessForLock = 120f,
            maxCenterDistanceForGood = 0.2f,
            maxCenterDistanceForLock = 0.15f,
            minStableFramesForLock = 3,
            minStableTimeForLockMs = 400L
        )
        manager = ScanGuidanceManager(config)
    }

    // ===========================================
    // INITIAL STATE TESTS
    // ===========================================

    @Test
    fun `initial state is SEARCHING`() {
        val state = manager.processFrame(
            candidate = null,
            motionScore = 0f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.SEARCHING, state.state)
    }

    @Test
    fun `initial ROI is default size`() {
        val state = manager.processFrame(
            candidate = null,
            motionScore = 0f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        // Should be close to default initial size (0.65)
        assertTrue(state.scanRoi.widthNorm in 0.6f..0.7f)
    }

    // ===========================================
    // STATE TRANSITION TESTS: No Candidate
    // ===========================================

    @Test
    fun `transitions to SEARCHING when no candidate`() {
        // First give it a candidate
        manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        // Then remove candidate
        val state = manager.processFrame(
            candidate = null,
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1100L
        )

        assertEquals(GuidanceState.SEARCHING, state.state)
    }

    // ===========================================
    // STATE TRANSITION TESTS: Motion/Stability
    // ===========================================

    @Test
    fun `transitions to UNSTABLE when motion too high`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.5f,  // High motion
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.UNSTABLE, state.state)
    }

    @Test
    fun `hint is Hold steady when UNSTABLE`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.5f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.UNSTABLE, state.state)
        assertEquals("Hold steady", state.hintText)
    }

    // ===========================================
    // STATE TRANSITION TESTS: Focus/Sharpness
    // ===========================================

    @Test
    fun `transitions to FOCUSING when sharpness too low`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 30f,  // Below minSharpnessForFocus
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.FOCUSING, state.state)
    }

    @Test
    fun `hint is Focusing when FOCUSING`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 30f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.FOCUSING, state.state)
        assertEquals("Focusing...", state.hintText)
    }

    // ===========================================
    // STATE TRANSITION TESTS: Distance
    // ===========================================

    @Test
    fun `transitions to TOO_CLOSE when object area too large`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.5f, 150f),  // Large area - too close
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.TOO_CLOSE, state.state)
    }

    @Test
    fun `hint is Move phone away when TOO_CLOSE`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.5f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals("Move phone away", state.hintText)
    }

    @Test
    fun `transitions to TOO_FAR when object area too small`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.02f, 150f),  // Small area - too far
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.TOO_FAR, state.state)
    }

    @Test
    fun `hint is Move closer when TOO_FAR`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.02f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals("Move closer", state.hintText)
    }

    // ===========================================
    // STATE TRANSITION TESTS: Centering
    // ===========================================

    @Test
    fun `transitions to OFF_CENTER when candidate not centered`() {
        // Create off-center candidate
        val offCenterCandidate = CandidateInfo(
            trackingId = "off_center",
            boxCenterX = 0.1f,  // Far from center (0.5)
            boxCenterY = 0.1f,
            boxArea = 0.1f,
            confidence = 0.6f
        )

        val state = manager.processFrame(
            candidate = offCenterCandidate,
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.OFF_CENTER, state.state)
    }

    @Test
    fun `hint is Center the object when OFF_CENTER`() {
        val offCenterCandidate = CandidateInfo(
            trackingId = "off_center",
            boxCenterX = 0.1f,
            boxCenterY = 0.1f,
            boxArea = 0.1f,
            confidence = 0.6f
        )

        val state = manager.processFrame(
            candidate = offCenterCandidate,
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals("Center the object", state.hintText)
    }

    // ===========================================
    // STATE TRANSITION TESTS: GOOD state
    // ===========================================

    @Test
    fun `transitions to GOOD when all conditions met`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),  // Good area
            motionScore = 0.05f,  // Low motion
            sharpnessScore = 150f,  // Sharp
            currentTimeMs = 1000L
        )

        assertEquals(GuidanceState.GOOD, state.state)
    }

    @Test
    fun `hint is Hold still to scan when GOOD`() {
        val state = manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        assertEquals("Hold still to scan", state.hintText)
    }

    // ===========================================
    // LOCK-ON TESTS
    // ===========================================

    @Test
    fun `transitions to LOCKED after stability window`() {
        val candidate = createCandidate(0.1f, 150f)

        // Process multiple frames to build stability
        var state = manager.processFrame(candidate, 0.05f, 150f, 1000L)
        state = manager.processFrame(candidate, 0.05f, 150f, 1200L)
        state = manager.processFrame(candidate, 0.05f, 150f, 1400L)
        state = manager.processFrame(candidate, 0.05f, 150f, 1600L)  // 600ms total

        assertEquals(GuidanceState.LOCKED, state.state)
    }

    @Test
    fun `canAddItem is true only in LOCKED state`() {
        val candidate = createCandidate(0.1f, 150f)

        // Not locked yet
        var state = manager.processFrame(candidate, 0.05f, 150f, 1000L)
        assertFalse(state.canAddItem)

        // Build stability
        state = manager.processFrame(candidate, 0.05f, 150f, 1200L)
        state = manager.processFrame(candidate, 0.05f, 150f, 1400L)
        state = manager.processFrame(candidate, 0.05f, 150f, 1600L)

        assertTrue(state.canAddItem)
    }

    @Test
    fun `lockedCandidateId is set in LOCKED state`() {
        val candidate = CandidateInfo(
            trackingId = "locked_item",
            boxCenterX = 0.5f,
            boxCenterY = 0.5f,
            boxArea = 0.1f,
            confidence = 0.6f
        )

        // Build stability
        repeat(4) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        val state = manager.processFrame(candidate, 0.05f, 150f, 2000L)

        assertEquals(GuidanceState.LOCKED, state.state)
        assertNotNull(state.lockedCandidateId)
    }

    @Test
    fun `lock breaks on high motion`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build lock
        repeat(4) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        // Verify locked
        var state = manager.processFrame(candidate, 0.05f, 150f, 2000L)
        assertEquals(GuidanceState.LOCKED, state.state)

        // High motion should break lock
        state = manager.processFrame(candidate, 0.5f, 150f, 2100L)
        assertTrue(state.state != GuidanceState.LOCKED)
    }

    @Test
    fun `lock breaks when candidate lost`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build lock
        repeat(4) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        var state = manager.processFrame(candidate, 0.05f, 150f, 2000L)
        assertEquals(GuidanceState.LOCKED, state.state)

        // Lose candidate
        state = manager.processFrame(null, 0.05f, 150f, 2100L)
        assertEquals(GuidanceState.SEARCHING, state.state)
        assertFalse(state.canAddItem)
    }

    // ===========================================
    // shouldAllowAdd TESTS
    // ===========================================

    @Test
    fun `shouldAllowAdd returns false when not locked`() {
        manager.processFrame(createCandidate(0.1f, 150f), 0.05f, 150f, 1000L)

        assertFalse(manager.shouldAllowAdd("any_id"))
    }

    @Test
    fun `shouldAllowAdd returns true when locked without ID check`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build lock
        repeat(5) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        assertTrue(manager.shouldAllowAdd(null))
    }

    // ===========================================
    // onItemAdded TESTS
    // ===========================================

    @Test
    fun `onItemAdded resets to SEARCHING`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build lock
        repeat(5) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        assertTrue(manager.isLocked())

        manager.onItemAdded()

        assertFalse(manager.isLocked())

        val state = manager.processFrame(null, 0.05f, 150f, 2000L)
        assertEquals(GuidanceState.SEARCHING, state.state)
    }

    // ===========================================
    // DYNAMIC ROI SIZING TESTS
    // ===========================================

    @Test
    fun `ROI shrinks when object too close`() {
        val initialRoi = manager.getCurrentRoi()

        // Process with large object (too close)
        repeat(5) { i ->
            manager.processFrame(
                candidate = createCandidate(0.5f, 150f),  // Very large
                motionScore = 0.05f,
                sharpnessScore = 150f,
                currentTimeMs = 1000L + (i * 100L)
            )
        }

        val newRoi = manager.getCurrentRoi()
        assertTrue(newRoi.widthNorm < initialRoi.widthNorm)
    }

    @Test
    fun `ROI expands when object too far`() {
        // First shrink the ROI
        repeat(5) { i ->
            manager.processFrame(
                candidate = createCandidate(0.5f, 150f),  // Large
                motionScore = 0.05f,
                sharpnessScore = 150f,
                currentTimeMs = 1000L + (i * 100L)
            )
        }

        val shrunkRoi = manager.getCurrentRoi()

        // Then process with small object (too far)
        repeat(5) { i ->
            manager.processFrame(
                candidate = createCandidate(0.02f, 150f),  // Very small
                motionScore = 0.05f,
                sharpnessScore = 150f,
                currentTimeMs = 2000L + (i * 100L)
            )
        }

        val expandedRoi = manager.getCurrentRoi()
        assertTrue(expandedRoi.widthNorm > shrunkRoi.widthNorm)
    }

    @Test
    fun `ROI does not resize while locked`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build lock
        repeat(5) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        assertTrue(manager.isLocked())
        val lockedRoi = manager.getCurrentRoi()

        // Try to change with different sized candidates
        manager.processFrame(
            candidate = createCandidate(0.5f, 150f),
            motionScore = 0.05f,
            sharpnessScore = 150f,
            currentTimeMs = 3000L
        )

        val afterRoi = manager.getCurrentRoi()
        assertEquals(lockedRoi.widthNorm, afterRoi.widthNorm, 0.01f)
    }

    // ===========================================
    // RESET TESTS
    // ===========================================

    @Test
    fun `reset clears all state`() {
        val candidate = createCandidate(0.1f, 150f)

        // Build some state
        repeat(5) { i ->
            manager.processFrame(candidate, 0.05f, 150f, 1000L + (i * 200L))
        }

        assertTrue(manager.isLocked())

        manager.reset()

        assertFalse(manager.isLocked())

        val state = manager.processFrame(null, 0f, 150f, 5000L)
        assertEquals(GuidanceState.SEARCHING, state.state)
    }

    // ===========================================
    // DIAGNOSTICS TESTS
    // ===========================================

    @Test
    fun `getDiagnostics returns valid data`() {
        manager.processFrame(
            candidate = createCandidate(0.1f, 150f),
            motionScore = 0.1f,
            sharpnessScore = 150f,
            currentTimeMs = 1000L
        )

        val diagnostics = manager.getDiagnostics()

        assertTrue(diagnostics.roiSizePercent in 40..80)
        assertNotNull(diagnostics.sharpness)
        assertNotNull(diagnostics.motionScore)
        assertEquals("GOOD", diagnostics.guidanceState)
    }

    // ===========================================
    // HELPER FUNCTIONS
    // ===========================================

    /**
     * Create a candidate with sensible defaults for testing.
     * Centered at (0.5, 0.5) by default.
     */
    private fun createCandidate(
        boxArea: Float,
        sharpness: Float,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f
    ): CandidateInfo {
        return CandidateInfo(
            trackingId = "test_candidate_${boxArea}_${centerX}_${centerY}",
            boxCenterX = centerX,
            boxCenterY = centerY,
            boxArea = boxArea,
            confidence = 0.6f
        )
    }
}
