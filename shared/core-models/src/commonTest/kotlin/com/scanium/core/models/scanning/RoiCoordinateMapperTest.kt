package com.scanium.core.models.scanning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RoiCoordinateMapperTest {

    // =========================================================================
    // toPreviewPixels tests
    // =========================================================================

    @Test
    fun `toPreviewPixels maps centered ROI correctly`() {
        val roi = ScanRoi.DEFAULT // centered at 0.5, 0.5 with 65% size
        val result = RoiCoordinateMapper.toPreviewPixels(roi, 1000, 1000)

        // Expected: center at 500, 500 with 650 width/height
        assertEquals(175, result.left)
        assertEquals(175, result.top)
        assertEquals(825, result.right)
        assertEquals(825, result.bottom)
        assertEquals(650, result.width)
        assertEquals(650, result.height)
    }

    @Test
    fun `toPreviewPixels handles landscape preview`() {
        val roi = ScanRoi.centered(0.5f)
        val result = RoiCoordinateMapper.toPreviewPixels(roi, 1920, 1080)

        // 50% of 1920 = 960 width, 50% of 1080 = 540 height
        assertEquals(480, result.left)
        assertEquals(270, result.top)
        assertEquals(1440, result.right)
        assertEquals(810, result.bottom)
    }

    @Test
    fun `toPreviewPixels handles portrait preview`() {
        val roi = ScanRoi.centered(0.6f)
        val result = RoiCoordinateMapper.toPreviewPixels(roi, 1080, 1920)

        // 60% of 1080 = 648 width, 60% of 1920 = 1152 height
        val expectedWidth = (1080 * 0.6f).toInt()
        val expectedHeight = (1920 * 0.6f).toInt()
        assertEquals(expectedWidth, result.width)
        assertEquals(expectedHeight, result.height)
    }

    // =========================================================================
    // toPreviewFloats tests
    // =========================================================================

    @Test
    fun `toPreviewFloats returns float coordinates for compose`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.toPreviewFloats(roi, 1000f, 1000f)

        assertEquals(175f, result.left, 0.1f)
        assertEquals(175f, result.top, 0.1f)
        assertEquals(825f, result.right, 0.1f)
        assertEquals(825f, result.bottom, 0.1f)
    }

    @Test
    fun `toPreviewFloats center is correctly calculated`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.toPreviewFloats(roi, 1000f, 1000f)

        assertEquals(500f, result.centerX, 0.1f)
        assertEquals(500f, result.centerY, 0.1f)
    }

    // =========================================================================
    // toAnalyzerPixels tests
    // =========================================================================

    @Test
    fun `toAnalyzerPixels maps directly without aspect ratio adjustment`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.toAnalyzerPixels(roi, 1280, 720, null)

        // Direct mapping: 65% of each dimension
        val expectedWidth = (1280 * 0.65f).toInt()
        val expectedHeight = (720 * 0.65f).toInt()
        assertEquals(expectedWidth, result.width)
        assertEquals(expectedHeight, result.height)
    }

    @Test
    fun `toAnalyzerPixels adjusts for wider analyzer aspect ratio`() {
        val roi = ScanRoi.centered(0.5f)
        // Analyzer is 16:9, preview is 3:4 (wider analyzer than preview)
        val result = RoiCoordinateMapper.toAnalyzerPixels(
            roi = roi,
            analyzerWidth = 1920,
            analyzerHeight = 1080,
            previewAspectRatio = 0.75f // 3:4 portrait
        )

        // With wider analyzer, horizontal cropping applies
        // Visible width = 1080 * 0.75 = 810
        // ROI left = (1920 - 810) / 2 + 810 * 0.25 = 555 + 202.5 = ~757
        assertTrue(result.left > 0)
        assertTrue(result.width > 0)
    }

    @Test
    fun `toAnalyzerPixels adjusts for taller analyzer aspect ratio`() {
        val roi = ScanRoi.centered(0.5f)
        // Analyzer is 3:4, preview is 16:9 (taller analyzer than preview)
        val result = RoiCoordinateMapper.toAnalyzerPixels(
            roi = roi,
            analyzerWidth = 1080,
            analyzerHeight = 1440,
            previewAspectRatio = 16f/9f // 16:9 landscape
        )

        // With taller analyzer, vertical cropping applies
        assertTrue(result.top > 0)
        assertTrue(result.height > 0)
    }

    // =========================================================================
    // detectionToNormalized tests
    // =========================================================================

    @Test
    fun `detectionToNormalized converts pixel box to normalized`() {
        val result = RoiCoordinateMapper.detectionToNormalized(
            boxLeft = 100,
            boxTop = 100,
            boxRight = 300,
            boxBottom = 300,
            analyzerWidth = 1000,
            analyzerHeight = 1000,
            previewAspectRatio = null
        )

        assertEquals(0.1f, result.left, 0.01f)
        assertEquals(0.1f, result.top, 0.01f)
        assertEquals(0.3f, result.right, 0.01f)
        assertEquals(0.3f, result.bottom, 0.01f)
        assertEquals(0.2f, result.centerX, 0.01f)
        assertEquals(0.2f, result.centerY, 0.01f)
    }

    @Test
    fun `detectionToNormalized handles zero dimensions`() {
        val result = RoiCoordinateMapper.detectionToNormalized(
            boxLeft = 100,
            boxTop = 100,
            boxRight = 300,
            boxBottom = 300,
            analyzerWidth = 0,
            analyzerHeight = 0,
            previewAspectRatio = null
        )

        assertEquals(0f, result.left)
        assertEquals(0f, result.top)
        assertEquals(0f, result.right)
        assertEquals(0f, result.bottom)
    }

    @Test
    fun `detectionToNormalized clamps values to 0-1 range`() {
        val result = RoiCoordinateMapper.detectionToNormalized(
            boxLeft = -100,
            boxTop = -100,
            boxRight = 1200,
            boxBottom = 1200,
            analyzerWidth = 1000,
            analyzerHeight = 1000,
            previewAspectRatio = 1.0f
        )

        // With aspect ratio adjustment and clamping
        assertTrue(result.left >= 0f)
        assertTrue(result.top >= 0f)
        assertTrue(result.right <= 1f)
        assertTrue(result.bottom <= 1f)
    }

    // =========================================================================
    // isDetectionInsideRoi tests
    // =========================================================================

    @Test
    fun `isDetectionInsideRoi returns true for centered detection`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.isDetectionInsideRoi(
            detectionCenterX = 500,
            detectionCenterY = 500,
            roi = roi,
            analyzerWidth = 1000,
            analyzerHeight = 1000
        )

        assertTrue(result)
    }

    @Test
    fun `isDetectionInsideRoi returns false for corner detection`() {
        val roi = ScanRoi.centered(0.5f) // 50% size centered
        val result = RoiCoordinateMapper.isDetectionInsideRoi(
            detectionCenterX = 50,
            detectionCenterY = 50,
            roi = roi,
            analyzerWidth = 1000,
            analyzerHeight = 1000
        )

        assertFalse(result)
    }

    @Test
    fun `isDetectionInsideRoi returns false for zero dimensions`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.isDetectionInsideRoi(
            detectionCenterX = 500,
            detectionCenterY = 500,
            roi = roi,
            analyzerWidth = 0,
            analyzerHeight = 0
        )

        assertFalse(result)
    }

    // =========================================================================
    // calculateCenterScore tests
    // =========================================================================

    @Test
    fun `calculateCenterScore returns 1 for perfectly centered detection`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.calculateCenterScore(
            detectionCenterX = 500,
            detectionCenterY = 500,
            roi = roi,
            analyzerWidth = 1000,
            analyzerHeight = 1000
        )

        assertEquals(1f, result, 0.01f)
    }

    @Test
    fun `calculateCenterScore returns lower score for off-center detection`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.calculateCenterScore(
            detectionCenterX = 200,
            detectionCenterY = 200,
            roi = roi,
            analyzerWidth = 1000,
            analyzerHeight = 1000
        )

        assertTrue(result < 0.8f)
        assertTrue(result >= 0f)
    }

    @Test
    fun `calculateCenterScore returns 0 for zero dimensions`() {
        val roi = ScanRoi.DEFAULT
        val result = RoiCoordinateMapper.calculateCenterScore(
            detectionCenterX = 500,
            detectionCenterY = 500,
            roi = roi,
            analyzerWidth = 0,
            analyzerHeight = 0
        )

        assertEquals(0f, result)
    }

    // =========================================================================
    // calculateVisibleViewport tests
    // =========================================================================

    @Test
    fun `calculateVisibleViewport returns full frame when aspects match`() {
        val result = RoiCoordinateMapper.calculateVisibleViewport(
            analyzerWidth = 1920,
            analyzerHeight = 1080,
            previewWidth = 1920,
            previewHeight = 1080
        )

        assertEquals(0, result.left)
        assertEquals(0, result.top)
        assertEquals(1920, result.right)
        assertEquals(1080, result.bottom)
    }

    @Test
    fun `calculateVisibleViewport crops horizontally for wider analyzer`() {
        // Analyzer 16:9, Preview 4:3
        val result = RoiCoordinateMapper.calculateVisibleViewport(
            analyzerWidth = 1920,
            analyzerHeight = 1080,
            previewWidth = 400,
            previewHeight = 300
        )

        // Preview aspect = 4/3 ≈ 1.33
        // Analyzer aspect = 16/9 ≈ 1.78
        // Analyzer is wider, so horizontal crop
        assertTrue(result.left > 0)
        assertEquals(0, result.top)
        assertTrue(result.right < 1920)
        assertEquals(1080, result.bottom)
    }

    @Test
    fun `calculateVisibleViewport crops vertically for taller analyzer`() {
        // Analyzer 3:4, Preview 16:9
        val result = RoiCoordinateMapper.calculateVisibleViewport(
            analyzerWidth = 1080,
            analyzerHeight = 1440,
            previewWidth = 1920,
            previewHeight = 1080
        )

        // Preview aspect = 16/9 ≈ 1.78
        // Analyzer aspect = 3/4 = 0.75
        // Analyzer is taller, so vertical crop
        assertEquals(0, result.left)
        assertTrue(result.top > 0)
        assertEquals(1080, result.right)
        assertTrue(result.bottom < 1440)
    }

    @Test
    fun `calculateVisibleViewport handles zero preview dimensions`() {
        val result = RoiCoordinateMapper.calculateVisibleViewport(
            analyzerWidth = 1920,
            analyzerHeight = 1080,
            previewWidth = 0,
            previewHeight = 0
        )

        // Should return full analyzer frame as fallback
        assertEquals(0, result.left)
        assertEquals(0, result.top)
        assertEquals(1920, result.right)
        assertEquals(1080, result.bottom)
    }

    // =========================================================================
    // RoiPixelRect tests
    // =========================================================================

    @Test
    fun `RoiPixelRect contains correctly detects inside points`() {
        val rect = RoiPixelRect(100, 100, 300, 300)

        assertTrue(rect.contains(200, 200))
        assertTrue(rect.contains(100, 100))
        assertTrue(rect.contains(300, 300))
    }

    @Test
    fun `RoiPixelRect contains correctly detects outside points`() {
        val rect = RoiPixelRect(100, 100, 300, 300)

        assertFalse(rect.contains(50, 50))
        assertFalse(rect.contains(350, 350))
    }

    // =========================================================================
    // NormalizedBox tests
    // =========================================================================

    @Test
    fun `NormalizedBox calculates area correctly`() {
        val box = NormalizedBox(0.2f, 0.3f, 0.6f, 0.7f)

        assertEquals(0.4f, box.width, 0.01f)
        assertEquals(0.4f, box.height, 0.01f)
        assertEquals(0.16f, box.area, 0.01f)
    }

    @Test
    fun `NormalizedBox isCenterInsideRoi works correctly`() {
        val roi = ScanRoi.DEFAULT // centered, 65% size
        val centeredBox = NormalizedBox(0.4f, 0.4f, 0.6f, 0.6f) // center at 0.5, 0.5
        val cornerBox = NormalizedBox(0f, 0f, 0.1f, 0.1f) // center at 0.05, 0.05

        assertTrue(centeredBox.isCenterInsideRoi(roi))
        assertFalse(cornerBox.isCenterInsideRoi(roi))
    }

    @Test
    fun `NormalizedBox centerScoreInRoi returns correct scores`() {
        val roi = ScanRoi.DEFAULT
        val centeredBox = NormalizedBox(0.4f, 0.4f, 0.6f, 0.6f)

        val score = centeredBox.centerScoreInRoi(roi)
        assertEquals(1f, score, 0.01f) // Perfectly centered
    }
}
