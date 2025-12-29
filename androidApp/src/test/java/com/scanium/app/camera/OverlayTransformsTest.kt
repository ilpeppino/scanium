package com.scanium.app.camera

import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for OverlayTransforms coordinate transformation logic.
 *
 * IMPORTANT: These tests verify the FIXED coordinate system where:
 * - ML Kit returns bboxes in InputImage (upright) coordinate space
 * - Bboxes are stored in upright normalized coordinates
 * - mapBboxToPreview does NOT rotate (bbox is already upright)
 */
@RunWith(RobolectricTestRunner::class)
class OverlayTransformsTest {

    // ==========================================
    // calculateTransformWithRotation tests
    // ==========================================

    @Test
    fun `calculateTransformWithRotation portrait mode swaps effective dimensions`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // In portrait (90°), effective dimensions should be swapped
        assertThat(transform.effectiveImageWidth).isEqualTo(sensorHeight) // 720
        assertThat(transform.effectiveImageHeight).isEqualTo(sensorWidth) // 1280
    }

    @Test
    fun `calculateTransformWithRotation landscape mode keeps dimensions unchanged`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1920f
        val previewHeight = 1080f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // In landscape (0°), effective dimensions match sensor
        assertThat(transform.effectiveImageWidth).isEqualTo(sensorWidth)
        assertThat(transform.effectiveImageHeight).isEqualTo(sensorHeight)
    }

    // ==========================================
    // mapBboxToPreview tests - UPRIGHT COORDINATES
    // ==========================================

    @Test
    fun `mapBboxToPreview does not rotate - bbox already in upright space`() {
        // Setup: Portrait mode with 90° rotation
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // A tall bbox in upright space (e.g., a bottle)
        // In upright portrait (720x1280), a tall object near the top-center
        val tallBbox = NormalizedRect(
            left = 0.35f,   // near horizontal center
            top = 0.1f,     // near top
            right = 0.65f,  // near horizontal center
            bottom = 0.7f   // tall object
        )

        val screenRect = mapBboxToPreview(tallBbox, transform)

        // The resulting screen rect should also be tall (height > width)
        // This is the KEY assertion - if we were double-rotating, a tall bbox would become wide
        assertThat(screenRect.height()).isGreaterThan(screenRect.width())

        // Both input and output should be tall (aspect > 1)
        val inputAspect = (tallBbox.bottom - tallBbox.top) / (tallBbox.right - tallBbox.left)
        val outputAspect = screenRect.height() / screenRect.width()
        assertThat(inputAspect).isGreaterThan(1f)
        assertThat(outputAspect).isGreaterThan(1f)
    }

    @Test
    fun `mapBboxToPreview wide bbox stays wide in portrait mode`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // A wide bbox in upright space (e.g., a laptop)
        val wideBbox = NormalizedRect(
            left = 0.1f,
            top = 0.4f,
            right = 0.9f,
            bottom = 0.6f
        )

        val screenRect = mapBboxToPreview(wideBbox, transform)

        // The resulting screen rect should also be wide (width > height)
        assertThat(screenRect.width()).isGreaterThan(screenRect.height())
    }

    @Test
    fun `mapBboxToPreview centered bbox maps to center of preview`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // Centered bbox in normalized space
        val centeredBbox = NormalizedRect(
            left = 0.4f,
            top = 0.4f,
            right = 0.6f,
            bottom = 0.6f
        )

        val screenRect = mapBboxToPreview(centeredBbox, transform)

        // Center of screen rect should be near center of preview
        val screenCenterX = (screenRect.left + screenRect.right) / 2
        val screenCenterY = (screenRect.top + screenRect.bottom) / 2

        // Allow some tolerance due to FILL_CENTER scaling
        assertThat(screenCenterX).isWithin(previewWidth * 0.15f).of(previewWidth / 2)
        assertThat(screenCenterY).isWithin(previewHeight * 0.15f).of(previewHeight / 2)
    }

    @Test
    fun `mapBboxToPreview works correctly in landscape mode`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1920f
        val previewHeight = 1080f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // A tall box in landscape (taller than wide)
        val tallBbox = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.4f,  // width = 0.2
            bottom = 0.7f  // height = 0.4
        )

        val screenRect = mapBboxToPreview(tallBbox, transform)

        // Verify the mapping produces valid results
        assertThat(screenRect.width()).isGreaterThan(0f)
        assertThat(screenRect.height()).isGreaterThan(0f)

        // Input is tall (height > width), output should also be tall
        val inputIsTall = (tallBbox.bottom - tallBbox.top) > (tallBbox.right - tallBbox.left)
        val outputIsTall = screenRect.height() > screenRect.width()
        assertThat(inputIsTall).isTrue()
        assertThat(outputIsTall).isTrue()
    }

    // ==========================================
    // FILL_CENTER vs FIT_CENTER tests
    // ==========================================

    @Test
    fun `FILL_CENTER scales to fill preview`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // FILL_CENTER should scale so image fills preview (scale >= 1 usually)
        // With effective 720x1280 image and 1080x1920 preview, scale = 1.5
        assertThat(transform.scale).isGreaterThan(1f)

        // Offset should be centered (may be negative for cropping)
        // The scaled effective width should cover the preview width
        val scaledWidth = transform.effectiveImageWidth * transform.scale
        assertThat(scaledWidth).isAtLeast(previewWidth)
    }

    @Test
    fun `FIT_CENTER scales to fit within preview`() {
        val sensorWidth = 1280
        val sensorHeight = 720
        val previewWidth = 1080f
        val previewHeight = 1920f

        val transform = calculateTransformWithRotation(
            imageWidth = sensorWidth,
            imageHeight = sensorHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FIT_CENTER
        )

        // FIT_CENTER should scale so image fits within preview
        val scaledWidth = transform.effectiveImageWidth * transform.scale
        val scaledHeight = transform.effectiveImageHeight * transform.scale

        // Scaled image should fit within preview bounds
        assertThat(scaledWidth).isAtMost(previewWidth + 0.01f)
        assertThat(scaledHeight).isAtMost(previewHeight + 0.01f)
    }

    // ==========================================
    // Edge case tests
    // ==========================================

    @Test
    fun `mapBboxToPreview handles bbox at origin`() {
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        val originBbox = NormalizedRect(
            left = 0f,
            top = 0f,
            right = 0.1f,
            bottom = 0.1f
        )

        val screenRect = mapBboxToPreview(originBbox, transform)

        // Should produce valid positive-size rect
        assertThat(screenRect.width()).isGreaterThan(0f)
        assertThat(screenRect.height()).isGreaterThan(0f)
    }

    @Test
    fun `mapBboxToPreview handles full-frame bbox`() {
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        val fullFrameBbox = NormalizedRect(
            left = 0f,
            top = 0f,
            right = 1f,
            bottom = 1f
        )

        val screenRect = mapBboxToPreview(fullFrameBbox, transform)

        // Should produce valid rect that covers significant portion of preview
        assertThat(screenRect.width()).isGreaterThan(0f)
        assertThat(screenRect.height()).isGreaterThan(0f)
    }
}
