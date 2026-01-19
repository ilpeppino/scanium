package com.scanium.core.models.scanning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanRoiTest {
    // =========================================================================
    // Basic properties tests
    // =========================================================================

    @Test
    fun `DEFAULT is centered with 65 percent size`() {
        val roi = ScanRoi.DEFAULT

        assertEquals(0.5f, roi.centerXNorm)
        assertEquals(0.5f, roi.centerYNorm)
        assertEquals(0.65f, roi.widthNorm)
        assertEquals(0.65f, roi.heightNorm)
    }

    @Test
    fun `edges are calculated correctly from center and size`() {
        val roi =
            ScanRoi(
                centerXNorm = 0.5f,
                centerYNorm = 0.5f,
                widthNorm = 0.4f,
                heightNorm = 0.4f,
            )

        assertEquals(0.3f, roi.left, 0.001f)
        assertEquals(0.3f, roi.top, 0.001f)
        assertEquals(0.7f, roi.right, 0.001f)
        assertEquals(0.7f, roi.bottom, 0.001f)
    }

    @Test
    fun `edges are clamped to valid range`() {
        val roi =
            ScanRoi(
                centerXNorm = 0.1f,
// Near left edge
                centerYNorm = 0.1f,
// Near top edge
                widthNorm = 0.5f,
                heightNorm = 0.5f,
            )

        // Left would be 0.1 - 0.25 = -0.15, should clamp to 0
        assertEquals(0f, roi.left)
        assertEquals(0f, roi.top)
    }

    @Test
    fun `clampedWidth and clampedHeight reflect actual dimensions`() {
        val roi =
            ScanRoi(
                centerXNorm = 0.1f,
                centerYNorm = 0.5f,
                widthNorm = 0.5f,
                heightNorm = 0.4f,
            )

        // Left is clamped to 0, right is 0.1 + 0.25 = 0.35
        assertEquals(0.35f, roi.clampedWidth, 0.001f)
        assertEquals(0.4f, roi.clampedHeight, 0.001f)
    }

    @Test
    fun `area is calculated correctly`() {
        val roi = ScanRoi.centered(0.5f)

        assertEquals(0.25f, roi.area, 0.001f) // 0.5 * 0.5 = 0.25
    }

    // =========================================================================
    // containsPoint tests
    // =========================================================================

    @Test
    fun `containsPoint returns true for center point`() {
        val roi = ScanRoi.DEFAULT

        assertTrue(roi.containsPoint(0.5f, 0.5f))
    }

    @Test
    fun `containsPoint returns true for point inside ROI`() {
        val roi = ScanRoi.DEFAULT // Roughly 0.175 to 0.825

        assertTrue(roi.containsPoint(0.3f, 0.3f))
        assertTrue(roi.containsPoint(0.7f, 0.7f))
    }

    @Test
    fun `containsPoint returns false for corner points`() {
        val roi = ScanRoi.centered(0.5f) // 0.25 to 0.75

        assertFalse(roi.containsPoint(0.1f, 0.1f))
        assertFalse(roi.containsPoint(0.9f, 0.9f))
        assertFalse(roi.containsPoint(0.1f, 0.9f))
        assertFalse(roi.containsPoint(0.9f, 0.1f))
    }

    @Test
    fun `containsPoint returns true for edge points`() {
        val roi = ScanRoi.centered(0.5f) // 0.25 to 0.75

        assertTrue(roi.containsPoint(0.25f, 0.5f)) // Left edge
        assertTrue(roi.containsPoint(0.75f, 0.5f)) // Right edge
        assertTrue(roi.containsPoint(0.5f, 0.25f)) // Top edge
        assertTrue(roi.containsPoint(0.5f, 0.75f)) // Bottom edge
    }

    // =========================================================================
    // containsBoxCenter tests
    // =========================================================================

    @Test
    fun `containsBoxCenter delegates to containsPoint`() {
        val roi = ScanRoi.DEFAULT

        assertTrue(roi.containsBoxCenter(0.5f, 0.5f))
        assertFalse(roi.containsBoxCenter(0.1f, 0.1f))
    }

    // =========================================================================
    // distanceFromCenter tests
    // =========================================================================

    @Test
    fun `distanceFromCenter is zero at center`() {
        val roi = ScanRoi.DEFAULT

        assertEquals(0f, roi.distanceFromCenter(0.5f, 0.5f), 0.001f)
    }

    @Test
    fun `distanceFromCenter increases with offset`() {
        val roi = ScanRoi.DEFAULT

        val distanceSmall = roi.distanceFromCenter(0.4f, 0.5f)
        val distanceLarge = roi.distanceFromCenter(0.2f, 0.5f)

        assertTrue(distanceLarge > distanceSmall)
    }

    @Test
    fun `distanceFromCenter is symmetric`() {
        val roi = ScanRoi.DEFAULT

        val d1 = roi.distanceFromCenter(0.3f, 0.5f)
        val d2 = roi.distanceFromCenter(0.7f, 0.5f)

        assertEquals(d1, d2, 0.001f)
    }

    // =========================================================================
    // centerScore tests
    // =========================================================================

    @Test
    fun `centerScore is 1 at center`() {
        val roi = ScanRoi.DEFAULT

        assertEquals(1f, roi.centerScore(0.5f, 0.5f), 0.001f)
    }

    @Test
    fun `centerScore decreases with distance`() {
        val roi = ScanRoi.DEFAULT

        val scoreCenter = roi.centerScore(0.5f, 0.5f)
        val scoreOffset = roi.centerScore(0.3f, 0.3f)

        assertTrue(scoreCenter > scoreOffset)
    }

    @Test
    fun `centerScore is in valid range`() {
        val roi = ScanRoi.DEFAULT

        val scores =
            listOf(
                roi.centerScore(0f, 0f),
                roi.centerScore(1f, 1f),
                roi.centerScore(0f, 1f),
                roi.centerScore(1f, 0f),
            )

        scores.forEach { score ->
            assertTrue(score in 0f..1f, "Score $score should be in 0..1")
        }
    }

    // =========================================================================
    // iouWith tests
    // =========================================================================

    @Test
    fun `iouWith returns 1 for identical ROI`() {
        val roi = ScanRoi.centered(0.5f)

        val iou = roi.iouWith(roi.left, roi.top, roi.right, roi.bottom)

        assertEquals(1f, iou, 0.001f)
    }

    @Test
    fun `iouWith returns 0 for non-overlapping box`() {
        val roi = ScanRoi.centered(0.5f) // 0.25 to 0.75

        val iou = roi.iouWith(0f, 0f, 0.1f, 0.1f)

        assertEquals(0f, iou, 0.001f)
    }

    @Test
    fun `iouWith returns value between 0 and 1 for partial overlap`() {
        val roi = ScanRoi.centered(0.5f) // 0.25 to 0.75

        val iou = roi.iouWith(0.3f, 0.3f, 0.6f, 0.6f)

        assertTrue(iou > 0f)
        assertTrue(iou < 1f)
    }

    // =========================================================================
    // overlapRatio tests
    // =========================================================================

    @Test
    fun `overlapRatio returns 1 when box is fully inside ROI`() {
        val roi = ScanRoi.centered(0.8f) // Large ROI

        val ratio = roi.overlapRatio(0.4f, 0.4f, 0.6f, 0.6f)

        assertEquals(1f, ratio, 0.001f)
    }

    @Test
    fun `overlapRatio returns 0 for non-overlapping box`() {
        val roi = ScanRoi.centered(0.5f)

        val ratio = roi.overlapRatio(0f, 0f, 0.1f, 0.1f)

        assertEquals(0f, ratio, 0.001f)
    }

    @Test
    fun `overlapRatio returns partial value for partial overlap`() {
        val roi = ScanRoi.centered(0.5f) // 0.25 to 0.75

        // Box from 0 to 0.5 - half inside ROI
        val ratio = roi.overlapRatio(0f, 0.25f, 0.5f, 0.75f)

        assertTrue(ratio > 0f)
        assertTrue(ratio < 1f)
    }

    // =========================================================================
    // Companion object tests
    // =========================================================================

    @Test
    fun `centered creates ROI with specified size`() {
        val roi = ScanRoi.centered(0.6f)

        assertEquals(0.5f, roi.centerXNorm)
        assertEquals(0.5f, roi.centerYNorm)
        assertEquals(0.6f, roi.widthNorm)
        assertEquals(0.6f, roi.heightNorm)
    }

    @Test
    fun `centered clamps size to valid range`() {
        val tooSmall = ScanRoi.centered(0.2f) // Below MIN_SIZE_NORM
        val tooLarge = ScanRoi.centered(0.9f) // Above MAX_SIZE_NORM

        assertEquals(ScanRoi.MIN_SIZE_NORM, tooSmall.widthNorm)
        assertEquals(ScanRoi.MAX_SIZE_NORM, tooLarge.widthNorm)
    }

    @Test
    fun `forAspectRatio creates landscape ROI for landscape preview`() {
        val roi = ScanRoi.forAspectRatio(16f / 9f, 0.6f)

        assertTrue(roi.widthNorm > roi.heightNorm)
    }

    @Test
    fun `forAspectRatio creates portrait ROI for portrait preview`() {
        val roi = ScanRoi.forAspectRatio(9f / 16f, 0.6f)

        assertTrue(roi.heightNorm > roi.widthNorm)
    }

    @Test
    fun `forAspectRatio creates square ROI for square preview`() {
        val roi = ScanRoi.forAspectRatio(1f, 0.6f)

        assertEquals(roi.widthNorm, roi.heightNorm, 0.001f)
    }

    // =========================================================================
    // ScanRoiConfig tests
    // =========================================================================

    @Test
    fun `ScanRoiConfig has sensible defaults`() {
        val config = ScanRoiConfig()

        assertEquals(0.65f, config.initialSize)
        assertEquals(ScanRoi.MIN_SIZE_NORM, config.minSize)
        assertEquals(ScanRoi.MAX_SIZE_NORM, config.maxSize)
        assertTrue(config.tooCloseAreaThreshold > config.tooFarAreaThreshold)
    }

    @Test
    fun `Constants have sensible values`() {
        assertTrue(ScanRoi.MIN_SIZE_NORM < ScanRoi.MAX_SIZE_NORM)
        assertTrue(ScanRoi.MIN_FAR_AREA < ScanRoi.MAX_CLOSE_AREA)
        assertTrue(ScanRoi.MIN_SIZE_NORM in 0.3f..0.6f)
        assertTrue(ScanRoi.MAX_SIZE_NORM in 0.6f..0.9f)
    }
}
