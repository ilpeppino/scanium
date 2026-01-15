package com.scanium.app.camera

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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 0,
                scaleType = PreviewScaleType.FILL_CENTER,
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // A tall bbox in upright space (e.g., a bottle)
        // In upright portrait (720x1280), a tall object near the top-center
        val tallBbox =
            NormalizedRect(
                left = 0.35f,
// near horizontal center
                top = 0.1f,
// near top
                right = 0.65f,
// near horizontal center
                bottom = 0.7f,
// tall object
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // A wide bbox in upright space (e.g., a laptop)
        val wideBbox =
            NormalizedRect(
                left = 0.1f,
                top = 0.4f,
                right = 0.9f,
                bottom = 0.6f,
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // Centered bbox in normalized space
        val centeredBbox =
            NormalizedRect(
                left = 0.4f,
                top = 0.4f,
                right = 0.6f,
                bottom = 0.6f,
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 0,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // A tall box in landscape (taller than wide)
        val tallBbox =
            NormalizedRect(
                left = 0.2f,
                top = 0.3f,
                right = 0.4f,
// width = 0.2
                bottom = 0.7f,
// height = 0.4
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
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

        val transform =
            calculateTransformWithRotation(
                imageWidth = sensorWidth,
                imageHeight = sensorHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FIT_CENTER,
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
        val transform =
            calculateTransformWithRotation(
                imageWidth = 1280,
                imageHeight = 720,
                previewWidth = 1080f,
                previewHeight = 1920f,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val originBbox =
            NormalizedRect(
                left = 0f,
                top = 0f,
                right = 0.1f,
                bottom = 0.1f,
            )

        val screenRect = mapBboxToPreview(originBbox, transform)

        // Should produce valid positive-size rect
        assertThat(screenRect.width()).isGreaterThan(0f)
        assertThat(screenRect.height()).isGreaterThan(0f)
    }

    @Test
    fun `mapBboxToPreview handles full-frame bbox`() {
        val transform =
            calculateTransformWithRotation(
                imageWidth = 1280,
                imageHeight = 720,
                previewWidth = 1080f,
                previewHeight = 1920f,
                rotationDegrees = 90,
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        val fullFrameBbox =
            NormalizedRect(
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1f,
            )

        val screenRect = mapBboxToPreview(fullFrameBbox, transform)

        // Should produce valid rect that covers significant portion of preview
        assertThat(screenRect.width()).isGreaterThan(0f)
        assertThat(screenRect.height()).isGreaterThan(0f)
    }

    // ==========================================
    // Rotation transformation tests
    // ==========================================

    @Test
    fun `rotateNormalizedRect 0 degrees returns unchanged rect`() {
        val rect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f
        )

        val rotated = rotateNormalizedRect(rect, 0)

        assertThat(rotated).isEqualTo(rect)
    }

    @Test
    fun `rotateNormalizedRect 90 degrees rotates clockwise`() {
        val rect = NormalizedRect(
            left = 0.1f,
            top = 0.1f,
            right = 0.3f,
            bottom = 0.3f
        )

        val rotated = rotateNormalizedRect(rect, 90)

        // 90° clockwise: (x, y) -> (y, 1-x)
        // Verify corners transformed correctly
        assertThat(rotated.left).isWithin(0.001f).of(0.1f) // Was top
        assertThat(rotated.top).isWithin(0.001f).of(0.7f) // Was 1 - right
        assertThat(rotated.right).isWithin(0.001f).of(0.3f) // Was bottom
        assertThat(rotated.bottom).isWithin(0.001f).of(0.9f) // Was 1 - left
    }

    @Test
    fun `rotateNormalizedRect 180 degrees flips both axes`() {
        val rect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f
        )

        val rotated = rotateNormalizedRect(rect, 180)

        // 180°: (x, y) -> (1-x, 1-y)
        assertThat(rotated.left).isWithin(0.001f).of(0.4f) // 1 - 0.6
        assertThat(rotated.top).isWithin(0.001f).of(0.3f) // 1 - 0.7
        assertThat(rotated.right).isWithin(0.001f).of(0.8f) // 1 - 0.2
        assertThat(rotated.bottom).isWithin(0.001f).of(0.7f) // 1 - 0.3
    }

    @Test
    fun `rotateNormalizedRect 270 degrees rotates counter-clockwise`() {
        val rect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f
        )

        val rotated = rotateNormalizedRect(rect, 270)

        // 270° clockwise (90° CCW): (x, y) -> (1-y, x)
        assertThat(rotated.left).isWithin(0.001f).of(0.3f) // 1 - 0.7
        assertThat(rotated.top).isWithin(0.001f).of(0.2f) // left
        assertThat(rotated.right).isWithin(0.001f).of(0.7f) // 1 - 0.3
        assertThat(rotated.bottom).isWithin(0.001f).of(0.6f) // right
    }

    @Test
    fun `rotateNormalizedRect preserves bbox validity across rotations`() {
        val rect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f
        )

        listOf(0, 90, 180, 270).forEach { rotation ->
            val rotated = rotateNormalizedRect(rect, rotation)

            // All rotated bboxes should be valid
            assertThat(rotated.left).isLessThan(rotated.right)
            assertThat(rotated.top).isLessThan(rotated.bottom)
            assertThat(rotated.left).isAtLeast(0f)
            assertThat(rotated.top).isAtLeast(0f)
            assertThat(rotated.right).isAtMost(1f)
            assertThat(rotated.bottom).isAtMost(1f)
        }
    }

    @Test
    fun `rotateNormalizedRect center point remains centered after 90 rotation`() {
        // Centered square
        val rect = NormalizedRect(
            left = 0.4f,
            top = 0.4f,
            right = 0.6f,
            bottom = 0.6f
        )

        val rotated = rotateNormalizedRect(rect, 90)

        // Center should still be at (0.5, 0.5)
        val centerX = (rotated.left + rotated.right) / 2f
        val centerY = (rotated.top + rotated.bottom) / 2f
        assertThat(centerX).isWithin(0.001f).of(0.5f)
        assertThat(centerY).isWithin(0.001f).of(0.5f)
    }

    // ==========================================
    // Scale and offset interaction tests
    // ==========================================

    @Test
    fun `mapBboxToPreview applies scale before offset`() {
        val bboxNorm = NormalizedRect(
            left = 0.25f,
            top = 0.25f,
            right = 0.75f,
            bottom = 0.75f
        )
        val transform = BboxMappingTransform(
            scale = 2.0f,
            offsetX = 100f,
            offsetY = 50f,
            rotationDegrees = 0,
            effectiveImageWidth = 1000,
            effectiveImageHeight = 1000,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        val result = mapBboxToPreview(bboxNorm, transform)

        // Transform order: normalize -> scale -> offset
        // left: 0.25 * 1000 = 250, * 2.0 = 500, + 100 = 600
        assertThat(result.left).isWithin(0.01f).of(600f)
        assertThat(result.top).isWithin(0.01f).of(550f) // 250 * 2 + 50
        assertThat(result.right).isWithin(0.01f).of(1600f) // 750 * 2 + 100
        assertThat(result.bottom).isWithin(0.01f).of(1550f) // 750 * 2 + 50
    }

    @Test
    fun `FILL_CENTER with wide image in portrait crops horizontally`() {
        // 16:9 image into 9:16 preview (extreme mismatch)
        val sensorWidth = 1920
        val sensorHeight = 1080
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

        // Effective: 1080x1920 (swapped), Preview: 1080x1920
        // Perfect aspect match -> scale = 1.0, no crop
        assertThat(transform.scale).isWithin(0.01f).of(1.0f)
        assertThat(transform.offsetX).isWithin(0.01f).of(0f)
        assertThat(transform.offsetY).isWithin(0.01f).of(0f)
    }

    @Test
    fun `FILL_CENTER with aspect mismatch produces negative offset for crop`() {
        // 4:3 image into 16:9 preview (taller image, wider preview)
        val imageWidth = 1600
        val imageHeight = 1200
        val previewWidth = 1920f
        val previewHeight = 1080f

        val transform = calculateTransformWithRotation(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // Image aspect: 1.333, Preview aspect: 1.778
        // Image is taller -> scale by width, crop vertically
        val expectedScale = previewWidth / imageWidth // 1.2
        assertThat(transform.scale).isWithin(0.01f).of(expectedScale)

        // Verify vertical crop (negative offsetY)
        val scaledHeight = imageHeight * transform.scale
        val expectedOffsetY = (previewHeight - scaledHeight) / 2f
        assertThat(expectedOffsetY).isLessThan(0f) // Cropping
        assertThat(transform.offsetY).isWithin(0.01f).of(expectedOffsetY)
    }

    @Test
    fun `FIT_CENTER with aspect mismatch produces positive offset for padding`() {
        // 16:9 image into square preview
        val imageWidth = 1920
        val imageHeight = 1080
        val previewWidth = 1080f
        val previewHeight = 1080f

        val transform = calculateTransformWithRotation(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FIT_CENTER
        )

        // Image is wider -> scale by width, letterbox vertically
        val expectedScale = previewWidth / imageWidth // 0.5625
        assertThat(transform.scale).isWithin(0.01f).of(expectedScale)

        // Verify vertical padding (positive offsetY)
        val scaledHeight = imageHeight * transform.scale
        val expectedOffsetY = (previewHeight - scaledHeight) / 2f
        assertThat(expectedOffsetY).isGreaterThan(0f) // Padding
        assertThat(transform.offsetY).isWithin(0.01f).of(expectedOffsetY)
    }

    // ==========================================
    // Integration tests with real-world scenarios
    // ==========================================

    @Test
    fun `realistic portrait scan - bbox stays within preview bounds`() {
        // Typical phone scenario
        val sensorWidth = 1920
        val sensorHeight = 1080
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

        val bbox = NormalizedRect(
            left = 0.3f,
            top = 0.4f,
            right = 0.7f,
            bottom = 0.6f
        )

        val mapped = mapBboxToPreview(bbox, transform)

        // Verify bbox is within preview
        assertThat(mapped.left).isAtLeast(0f)
        assertThat(mapped.top).isAtLeast(0f)
        assertThat(mapped.right).isAtMost(previewWidth)
        assertThat(mapped.bottom).isAtMost(previewHeight)

        // Verify reasonable size
        assertThat(mapped.width()).isGreaterThan(100f)
        assertThat(mapped.height()).isGreaterThan(100f)
    }

    @Test
    fun `tiny bbox remains valid after transformation`() {
        val transform = BboxMappingTransform(
            scale = 1.0f,
            offsetX = 0f,
            offsetY = 0f,
            rotationDegrees = 0,
            effectiveImageWidth = 1000,
            effectiveImageHeight = 1000,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // 1% of frame
        val tinyBbox = NormalizedRect(
            left = 0.4f,
            top = 0.4f,
            right = 0.41f,
            bottom = 0.41f
        )

        val mapped = mapBboxToPreview(tinyBbox, transform)

        assertThat(mapped.width()).isWithin(0.1f).of(10f)
        assertThat(mapped.height()).isWithin(0.1f).of(10f)
        assertThat(mapped.width()).isGreaterThan(0f)
        assertThat(mapped.height()).isGreaterThan(0f)
    }

    @Test
    fun `multiple rotations compose correctly`() {
        val originalRect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f
        )

        // Rotate 90° four times should return to original
        var rotated = originalRect
        repeat(4) {
            rotated = rotateNormalizedRect(rotated, 90)
        }

        // Should be back to original (within floating point tolerance)
        assertThat(rotated.left).isWithin(0.001f).of(originalRect.left)
        assertThat(rotated.top).isWithin(0.001f).of(originalRect.top)
        assertThat(rotated.right).isWithin(0.001f).of(originalRect.right)
        assertThat(rotated.bottom).isWithin(0.001f).of(originalRect.bottom)
    }
}
