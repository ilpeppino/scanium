package com.scanium.app.camera.detection

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ThrottleHelper.
 *
 * Tests time-based throttling logic for detector invocations.
 */
class ThrottleHelperTest {

    private lateinit var helper: ThrottleHelper

    @Before
    fun setUp() {
        helper = ThrottleHelper()
    }

    @Test
    fun `canInvoke returns true for first invocation`() {
        // First invocation should always be allowed
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 0L))
        assertTrue(helper.canInvoke(DetectorType.BARCODE, currentTimeMs = 0L))
        assertTrue(helper.canInvoke(DetectorType.DOCUMENT, currentTimeMs = 0L))
    }

    @Test
    fun `canInvoke returns false within interval after invocation`() {
        // Record an invocation
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)

        // Check immediately after - should be throttled
        assertFalse(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 100L))

        // Check just before interval expires (default is 400ms for OBJECT)
        assertFalse(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 399L))
    }

    @Test
    fun `canInvoke returns true after interval expires`() {
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)

        // Check exactly at interval - should be allowed
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 400L))

        // Check after interval - should be allowed
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 500L))
    }

    @Test
    fun `tryInvoke combines check and record atomically`() {
        // First invocation should succeed
        assertTrue(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 0L))

        // Immediate second invocation should fail
        assertFalse(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 100L))

        // After interval, should succeed again
        assertTrue(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 500L))
    }

    @Test
    fun `setMinInterval updates throttle interval`() {
        // Set custom interval
        helper.setMinInterval(DetectorType.OBJECT, 100L)

        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)

        // Should be throttled at 50ms
        assertFalse(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 50L))

        // Should be allowed at 100ms
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 100L))
    }

    @Test
    fun `getMinInterval returns correct values`() {
        assertEquals(400L, helper.getMinInterval(DetectorType.OBJECT))
        assertEquals(100L, helper.getMinInterval(DetectorType.BARCODE))
        assertEquals(500L, helper.getMinInterval(DetectorType.DOCUMENT))
    }

    @Test
    fun `different detectors are throttled independently`() {
        // Record invocation for OBJECT
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)

        // BARCODE should still be allowed (not throttled)
        assertTrue(helper.canInvoke(DetectorType.BARCODE, currentTimeMs = 50L))

        // OBJECT should be throttled
        assertFalse(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 50L))
    }

    @Test
    fun `timeUntilAllowed returns correct remaining time`() {
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)

        // At time 100, should have 300ms remaining (400 - 100)
        assertEquals(300L, helper.timeUntilAllowed(DetectorType.OBJECT, currentTimeMs = 100L))

        // At time 400, should have 0ms remaining
        assertEquals(0L, helper.timeUntilAllowed(DetectorType.OBJECT, currentTimeMs = 400L))

        // At time 500, should still return 0
        assertEquals(0L, helper.timeUntilAllowed(DetectorType.OBJECT, currentTimeMs = 500L))
    }

    @Test
    fun `reset clears throttle state for specific detector`() {
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)
        helper.recordInvocation(DetectorType.BARCODE, currentTimeMs = 0L)

        // Reset only OBJECT
        helper.reset(DetectorType.OBJECT)

        // OBJECT should be allowed immediately
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 50L))

        // BARCODE should still be throttled
        assertFalse(helper.canInvoke(DetectorType.BARCODE, currentTimeMs = 50L))
    }

    @Test
    fun `resetAll clears all throttle state`() {
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 0L)
        helper.recordInvocation(DetectorType.BARCODE, currentTimeMs = 0L)
        helper.recordInvocation(DetectorType.DOCUMENT, currentTimeMs = 0L)

        helper.resetAll()

        // All should be allowed
        assertTrue(helper.canInvoke(DetectorType.OBJECT, currentTimeMs = 50L))
        assertTrue(helper.canInvoke(DetectorType.BARCODE, currentTimeMs = 50L))
        assertTrue(helper.canInvoke(DetectorType.DOCUMENT, currentTimeMs = 50L))
    }

    @Test
    fun `getStats returns correct statistics`() {
        helper.recordInvocation(DetectorType.OBJECT, currentTimeMs = 100L)

        val stats = helper.getStats(currentTimeMs = 200L)

        assertEquals(400L, stats[DetectorType.OBJECT]?.minIntervalMs)
        assertEquals(100L, stats[DetectorType.OBJECT]?.lastInvocationMs)
        assertEquals(100L, stats[DetectorType.OBJECT]?.timeSinceLastMs)
    }

    @Test
    fun `setMinInterval rejects negative values`() {
        assertThrows(IllegalArgumentException::class.java) {
            helper.setMinInterval(DetectorType.OBJECT, -1L)
        }
    }

    @Test
    fun `zero interval allows all invocations`() {
        helper.setMinInterval(DetectorType.OBJECT, 0L)

        assertTrue(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 0L))
        assertTrue(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 0L))
        assertTrue(helper.tryInvoke(DetectorType.OBJECT, currentTimeMs = 0L))
    }
}
