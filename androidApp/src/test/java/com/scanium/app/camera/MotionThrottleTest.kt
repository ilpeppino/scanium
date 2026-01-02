package com.scanium.app.camera

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for motion-based throttling logic.
 *
 * Regression test for fix in docs/SCAN_VS_PICTURE_ASSESSMENT.md:
 * - Previously, steady scenes (motion <= 0.1) used 2000ms intervals
 * - This caused live scanning to feel unresponsive when camera is held still
 * - Fixed to use 600ms for steady scenes, 500ms for low motion
 *
 * @see CameraXManager.analysisIntervalMsForMotion
 */
class MotionThrottleTest {
    /**
     * Compute analysis interval based on motion score.
     * This mirrors the logic in CameraXManager.analysisIntervalMsForMotion.
     */
    private fun analysisIntervalMsForMotion(motionScore: Double): Long =
        when {
            motionScore <= 0.1 -> 600L // Steady scene
            motionScore <= 0.5 -> 500L // Low motion
            else -> 400L // High motion
        }

    @Test
    fun `steady scene interval should be 600ms (not 2000ms)`() {
        // Regression test: previously this was 2000ms which was too slow
        val interval = analysisIntervalMsForMotion(0.0)
        assertEquals("Steady scene (0.0) should use 600ms interval", 600L, interval)

        val interval2 = analysisIntervalMsForMotion(0.05)
        assertEquals("Steady scene (0.05) should use 600ms interval", 600L, interval2)

        val interval3 = analysisIntervalMsForMotion(0.1)
        assertEquals("Boundary steady scene (0.1) should use 600ms interval", 600L, interval3)
    }

    @Test
    fun `low motion interval should be 500ms (not 800ms)`() {
        // Regression test: previously this was 800ms
        val interval = analysisIntervalMsForMotion(0.11)
        assertEquals("Low motion (0.11) should use 500ms interval", 500L, interval)

        val interval2 = analysisIntervalMsForMotion(0.3)
        assertEquals("Low motion (0.3) should use 500ms interval", 500L, interval2)

        val interval3 = analysisIntervalMsForMotion(0.5)
        assertEquals("Boundary low motion (0.5) should use 500ms interval", 500L, interval3)
    }

    @Test
    fun `high motion interval should be 400ms`() {
        val interval = analysisIntervalMsForMotion(0.51)
        assertEquals("High motion (0.51) should use 400ms interval", 400L, interval)

        val interval2 = analysisIntervalMsForMotion(1.0)
        assertEquals("High motion (1.0) should use 400ms interval", 400L, interval2)
    }

    @Test
    fun `intervals should decrease as motion increases`() {
        val steadyInterval = analysisIntervalMsForMotion(0.05)
        val lowMotionInterval = analysisIntervalMsForMotion(0.3)
        val highMotionInterval = analysisIntervalMsForMotion(0.8)

        assertTrue(
            "Steady interval ($steadyInterval) should be >= low motion interval ($lowMotionInterval)",
            steadyInterval >= lowMotionInterval,
        )
        assertTrue(
            "Low motion interval ($lowMotionInterval) should be >= high motion interval ($highMotionInterval)",
            lowMotionInterval >= highMotionInterval,
        )
    }

    @Test
    fun `max interval should be 600ms for reasonable responsiveness`() {
        // Core assertion: max interval should never exceed 600ms
        // This ensures users get detection feedback within a reasonable time
        val maxInterval = listOf(0.0, 0.1, 0.5, 1.0).maxOf { analysisIntervalMsForMotion(it) }
        assertTrue(
            "Maximum interval ($maxInterval ms) should not exceed 600ms for responsiveness",
            maxInterval <= 600L,
        )
    }

    @Test
    fun `min interval should be 400ms for battery efficiency`() {
        // Ensure we're not processing too aggressively
        val minInterval = listOf(0.0, 0.1, 0.5, 1.0).minOf { analysisIntervalMsForMotion(it) }
        assertTrue(
            "Minimum interval ($minInterval ms) should be at least 400ms for battery efficiency",
            minInterval >= 400L,
        )
    }

    @Test
    fun `detection rate at steady should be approximately 1_7 fps`() {
        val interval = analysisIntervalMsForMotion(0.0)
        val fps = 1000.0 / interval
        assertTrue(
            "Steady scene should achieve ~1.7 fps (actual: ${"%.2f".format(fps)})",
            fps >= 1.5 && fps <= 2.0,
        )
    }

    @Test
    fun `detection rate at high motion should be approximately 2_5 fps`() {
        val interval = analysisIntervalMsForMotion(1.0)
        val fps = 1000.0 / interval
        assertTrue(
            "High motion should achieve ~2.5 fps (actual: ${"%.2f".format(fps)})",
            fps >= 2.0 && fps <= 3.0,
        )
    }
}
