package com.scanium.app.camera

import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for portrait bounding box offset bug reported after v1.1.0.
 *
 * Issue: In portrait mode (90° rotation), bounding boxes have correct shape but are
 * offset (bottom-left) relative to the detected object.
 *
 * This test verifies that the coordinate transformation from ML Kit bbox coordinates
 * to preview overlay coordinates works correctly in portrait mode.
 */
@RunWith(RobolectricTestRunner::class)
class PortraitTransformRegressionTest {
    @Test
    fun `portrait 90deg - centered box maps to preview center`() {
        // ARRANGE: Simulate portrait mode on a phone
        // Sensor dimensions (landscape): 1920x1080
        // After 90° rotation, effective dimensions: 1080x1920 (portrait)
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 90

        // Preview view dimensions (portrait phone screen): 1080x1920
        val previewWidth = 1080f
        val previewHeight = 1920f

        // ML Kit returns a bbox centered in the upright (portrait) space
        // Normalized coordinates in 1080x1920 space
        val centeredBox =
            NormalizedRect(
                // 40% from left = 432px
                left = 0.4f,
                // 40% from top = 768px
                top = 0.4f,
                // 60% from left = 648px
                right = 0.6f,
                // 60% from top = 1152px
                bottom = 0.6f,
            )

        // ACT: Transform bbox to preview coordinates
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val previewBox = mapBboxToPreview(centeredBox, transform)

        // DEBUG: Print transform and result
        println("=== Portrait 90deg Centered Box Test ===")
        println(
            "Transform: scale=${transform.scale}, offset=(${transform.offsetX}, ${transform.offsetY})",
        )
        println("Effective dims: ${transform.effectiveImageWidth}x${transform.effectiveImageHeight}")
        println("Input bbox (normalized): $centeredBox")
        println(
            "Output bbox (preview px): " +
                "left=${previewBox.left}, top=${previewBox.top}, " +
                "right=${previewBox.right}, bottom=${previewBox.bottom}",
        )
        println("Preview dims: ${previewWidth}x$previewHeight")

        // ASSERT: Box should be centered in preview
        // Expected center in preview: (540, 960)
        val expectedCenterX = previewWidth / 2f
        val expectedCenterY = previewHeight / 2f

        val actualCenterX = (previewBox.left + previewBox.right) / 2f
        val actualCenterY = (previewBox.top + previewBox.bottom) / 2f

        println("Expected center: ($expectedCenterX, $expectedCenterY)")
        println("Actual center: ($actualCenterX, $actualCenterY)")

        // Allow 5% tolerance for centering
        val centerTolerance = 50f
        assertThat(actualCenterX).isWithin(centerTolerance).of(expectedCenterX)
        assertThat(actualCenterY).isWithin(centerTolerance).of(expectedCenterY)

        // Box aspect ratio should match the image aspect ratio
        // Normalized rect is 0.2x0.2 (square in normalized space)
        // Image effective dims: 1080x1920 (9:16 aspect ratio)
        // So preview box should have 9:16 aspect ratio too
        val boxWidth = previewBox.right - previewBox.left
        val boxHeight = previewBox.bottom - previewBox.top
        val aspectRatio = boxHeight / boxWidth
        val expectedAspectRatio = 1920f / 1080f // 16:9 = 1.778
        assertThat(aspectRatio).isWithin(0.1f).of(expectedAspectRatio)
    }

    @Test
    fun `portrait 90deg - top-left box maps correctly`() {
        // ARRANGE
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 90
        val previewWidth = 1080f
        val previewHeight = 1920f

        // Box in top-left corner of upright space
        val topLeftBox =
            NormalizedRect(
                left = 0.1f,
                top = 0.1f,
                right = 0.3f,
                bottom = 0.3f,
            )

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val previewBox = mapBboxToPreview(topLeftBox, transform)

        // ASSERT: Box should be in top-left of preview
        // Not bottom-left (which would indicate an offset bug)
        assertThat(previewBox.left).isLessThan(previewWidth * 0.5f)
        assertThat(previewBox.top).isLessThan(previewHeight * 0.5f)
        assertThat(previewBox.top).isGreaterThan(0f)
        assertThat(previewBox.left).isGreaterThan(0f)
    }

    @Test
    fun `portrait 90deg - effective dimensions are swapped`() {
        // ARRANGE
        val sensorWidth = 1920 // Landscape sensor
        val sensorHeight = 1080
        val rotationDegrees = 90

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = 1080f,
                previewHeight = 1920f,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // ASSERT: Effective dimensions should be portrait (height > width)
        assertThat(transform.effectiveImageWidth).isEqualTo(sensorHeight) // 1080
        assertThat(transform.effectiveImageHeight).isEqualTo(sensorWidth) // 1920
        assertThat(transform.effectiveImageHeight).isGreaterThan(transform.effectiveImageWidth)
    }

    @Test
    fun `portrait 90deg with different aspect ratios - scale is correct`() {
        // ARRANGE: Sensor has different aspect ratio than preview
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 90

        // Preview is narrower (common for phones with notches/rounded corners)
        val previewWidth = 1000f
        val previewHeight = 2000f

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // ASSERT: Scale should be calculated based on effective dimensions
        // Effective: 1080x1920, Preview: 1000x2000
        // FILL_CENTER uses the larger scale to fill the preview
        val scaleByWidth = previewWidth / 1080f // ~0.926
        val scaleByHeight = previewHeight / 1920f // ~1.042
        val expectedScale = maxOf(scaleByWidth, scaleByHeight) // 1.042

        assertThat(transform.scale).isWithin(0.01f).of(expectedScale)
    }

    @Test
    fun `portrait 270deg - centered box maps to preview center`() {
        // ARRANGE: Test reverse portrait orientation
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 270
        val previewWidth = 1080f
        val previewHeight = 1920f

        val centeredBox =
            NormalizedRect(
                left = 0.4f,
                top = 0.4f,
                right = 0.6f,
                bottom = 0.6f,
            )

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val previewBox = mapBboxToPreview(centeredBox, transform)

        // ASSERT: Box should still be centered
        val actualCenterX = (previewBox.left + previewBox.right) / 2f
        val actualCenterY = (previewBox.top + previewBox.bottom) / 2f
        val expectedCenterX = previewWidth / 2f
        val expectedCenterY = previewHeight / 2f

        assertThat(actualCenterX).isWithin(50f).of(expectedCenterX)
        assertThat(actualCenterY).isWithin(50f).of(expectedCenterY)
    }

    @Test
    fun `landscape 0deg - centered box maps to preview center`() {
        // ARRANGE: Test landscape mode (no rotation)
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 0
        val previewWidth = 1920f
        val previewHeight = 1080f

        val centeredBox =
            NormalizedRect(
                left = 0.4f,
                top = 0.4f,
                right = 0.6f,
                bottom = 0.6f,
            )

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val previewBox = mapBboxToPreview(centeredBox, transform)

        // ASSERT: Box should be centered
        val actualCenterX = (previewBox.left + previewBox.right) / 2f
        val actualCenterY = (previewBox.top + previewBox.bottom) / 2f
        val expectedCenterX = previewWidth / 2f
        val expectedCenterY = previewHeight / 2f

        assertThat(actualCenterX).isWithin(50f).of(expectedCenterX)
        assertThat(actualCenterY).isWithin(50f).of(expectedCenterY)
    }

    @Test
    fun `portrait 90deg - top-left bbox does NOT appear at bottom-left (regression guard)`() {
        // ARRANGE: Specific test for the "bottom-left offset" regression
        val sensorWidth = 1280
        val sensorHeight = 720
        val rotationDegrees = 90
        val previewWidth = 1080f
        val previewHeight = 2400f

        // ACT
        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // Box clearly in top-left quadrant of upright portrait space
        val topLeftBox =
            NormalizedRect(
                left = 0.15f,
                top = 0.1f,
                right = 0.35f,
                bottom = 0.25f,
            )

        val previewBox = mapBboxToPreview(topLeftBox, transform)

        // Calculate quadrant thresholds
        val quadrantMidX = previewWidth / 2f
        val quadrantMidY = previewHeight / 2f

        println("=== REGRESSION GUARD: Top-left bbox offset test ===")
        println("Input bbox (norm): left=${topLeftBox.left}, top=${topLeftBox.top}")
        println(
            "Transform: scale=${transform.scale}, offset=(${transform.offsetX}, ${transform.offsetY})",
        )
        println("Preview box: left=${previewBox.left}, top=${previewBox.top}, right=${previewBox.right}, bottom=${previewBox.bottom}")
        println("Preview center: ($quadrantMidX, $quadrantMidY)")
        println("Box center: (${(previewBox.left + previewBox.right) / 2}, ${(previewBox.top + previewBox.bottom) / 2})")

        // CRITICAL ASSERTIONS:
        // 1. Box should NOT be in bottom half (center Y should be less than midY)
        val boxCenterY = (previewBox.top + previewBox.bottom) / 2f
        assertThat(boxCenterY).isLessThan(quadrantMidY)

        // 2. Box top should be in upper portion of screen
        assertThat(previewBox.top).isLessThan(quadrantMidY)

        // 3. Box should be in upper half of preview (sanity check)
        assertThat(previewBox.bottom).isLessThan(quadrantMidY * 1.5f)
    }

    @Test
    fun `portrait 90deg - realistic device dimensions produce correct mapping`() {
        // Test with common real-world device dimensions
        // Example: Pixel 6 Pro - 1440x3120 display, 1920x1080 camera
        val sensorWidth = 1920
        val sensorHeight = 1080
        val rotationDegrees = 90
        val previewWidth = 1440f
        val previewHeight = 3120f

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // Effective dimensions after rotation: 1080x1920
        assertThat(transform.effectiveImageWidth).isEqualTo(1080)
        assertThat(transform.effectiveImageHeight).isEqualTo(1920)

        // Test centered object
        val centeredBox = NormalizedRect(left = 0.4f, top = 0.4f, right = 0.6f, bottom = 0.6f)
        val screenBox = mapBboxToPreview(centeredBox, transform)

        val boxCenterX = (screenBox.left + screenBox.right) / 2f
        val boxCenterY = (screenBox.top + screenBox.bottom) / 2f

        // Box should be reasonably centered (within 10% of preview)
        assertThat(boxCenterX).isWithin(previewWidth * 0.1f).of(previewWidth / 2f)
        assertThat(boxCenterY).isWithin(previewHeight * 0.1f).of(previewHeight / 2f)
    }
}
