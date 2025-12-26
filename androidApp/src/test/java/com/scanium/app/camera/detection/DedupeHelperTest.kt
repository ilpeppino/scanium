package com.scanium.app.camera.detection

import com.scanium.core.models.geometry.NormalizedRect
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DedupeHelper.
 *
 * Tests spatial and temporal deduplication logic.
 */
class DedupeHelperTest {

    private lateinit var helper: DedupeHelper

    @Before
    fun setUp() {
        helper = DedupeHelper()
    }

    // Helper to create a NormalizedRect
    private fun rect(left: Float, top: Float, right: Float, bottom: Float): NormalizedRect {
        return NormalizedRect(left, top, right, bottom)
    }

    @Test
    fun `isDuplicate returns false for first detection`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 0L
            )
        )
    }

    @Test
    fun `isDuplicate returns true for same detection within expiry window`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        // Record first detection
        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box,
            currentTimeMs = 0L
        )

        // Same detection should be duplicate
        assertTrue(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `isDuplicate returns false after expiry window`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        // Record first detection
        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box,
            currentTimeMs = 0L
        )

        // After default 3000ms expiry for OBJECT, should not be duplicate
        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 4000L
            )
        )
    }

    @Test
    fun `isDuplicate returns false for different category`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box,
            currentTimeMs = 0L
        )

        // Different category should not be duplicate
        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "bag",
                boundingBox = box,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `isDuplicate returns false for different detector type`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "item",
            boundingBox = box,
            currentTimeMs = 0L
        )

        // Different detector type should not be duplicate
        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.BARCODE,
                category = "item",
                boundingBox = box,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `isDuplicate returns true for overlapping boxes with high IoU`() {
        val box1 = rect(0.1f, 0.1f, 0.3f, 0.3f)
        val box2 = rect(0.12f, 0.12f, 0.32f, 0.32f) // Slightly shifted, high overlap

        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box1,
            currentTimeMs = 0L
        )

        assertTrue(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box2,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `isDuplicate returns false for non-overlapping boxes`() {
        val box1 = rect(0.1f, 0.1f, 0.2f, 0.2f)
        val box2 = rect(0.5f, 0.5f, 0.6f, 0.6f) // Completely different position

        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box1,
            currentTimeMs = 0L
        )

        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box2,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `checkAndRecord returns true for new item and records it`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        // First call should return true (not duplicate)
        assertTrue(
            helper.checkAndRecord(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 0L
            )
        )

        // Second call should return false (duplicate)
        assertFalse(
            helper.checkAndRecord(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 1000L
            )
        )
    }

    @Test
    fun `reset clears state for specific detector`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        helper.recordSeen(
            detectorType = DetectorType.OBJECT,
            category = "shoe",
            boundingBox = box,
            currentTimeMs = 0L
        )

        helper.reset(DetectorType.OBJECT)

        // After reset, should not be duplicate
        assertFalse(
            helper.isDuplicate(
                detectorType = DetectorType.OBJECT,
                category = "shoe",
                boundingBox = box,
                currentTimeMs = 100L
            )
        )
    }

    @Test
    fun `resetAll clears all state`() {
        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        helper.recordSeen(DetectorType.OBJECT, "shoe", box, 0L)
        helper.recordSeen(DetectorType.BARCODE, "code", box, 0L)

        helper.resetAll()

        // All should be new after reset
        assertFalse(helper.isDuplicate(DetectorType.OBJECT, "shoe", box, 100L))
        assertFalse(helper.isDuplicate(DetectorType.BARCODE, "code", box, 100L))
    }

    @Test
    fun `getStats returns correct count`() {
        val box1 = rect(0.1f, 0.1f, 0.2f, 0.2f)
        val box2 = rect(0.5f, 0.5f, 0.6f, 0.6f)

        helper.recordSeen(DetectorType.OBJECT, "shoe", box1, 0L)
        helper.recordSeen(DetectorType.OBJECT, "bag", box2, 0L)
        helper.recordSeen(DetectorType.BARCODE, "code", box1, 0L)

        val stats = helper.getStats()

        assertEquals(3, stats.totalTracked)
        assertEquals(2, stats.trackedByType[DetectorType.OBJECT])
        assertEquals(1, stats.trackedByType[DetectorType.BARCODE])
        assertEquals(0, stats.trackedByType[DetectorType.DOCUMENT])
    }

    @Test
    fun `custom expiry window is respected`() {
        val config = DedupeConfig(
            expiryWindowMs = mapOf(DetectorType.OBJECT to 500L),
            defaultExpiryWindowMs = 1000L
        )
        val helper = DedupeHelper(config)

        val box = rect(0.1f, 0.1f, 0.3f, 0.3f)

        helper.recordSeen(DetectorType.OBJECT, "shoe", box, 0L)

        // At 400ms, should still be duplicate
        assertTrue(helper.isDuplicate(DetectorType.OBJECT, "shoe", box, 400L))

        // At 600ms, should no longer be duplicate
        assertFalse(helper.isDuplicate(DetectorType.OBJECT, "shoe", box, 600L))
    }

    @Test
    fun `custom IoU threshold is respected`() {
        val config = DedupeConfig(iouThreshold = 0.8f) // High threshold
        val helper = DedupeHelper(config)

        val box1 = rect(0.1f, 0.1f, 0.3f, 0.3f)
        val box2 = rect(0.15f, 0.15f, 0.35f, 0.35f) // Moderate overlap

        helper.recordSeen(DetectorType.OBJECT, "shoe", box1, 0L)

        // With high IoU threshold, moderate overlap should NOT be duplicate
        assertFalse(helper.isDuplicate(DetectorType.OBJECT, "shoe", box2, 1000L))
    }
}
