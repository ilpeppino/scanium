package com.scanium.app.camera

import android.graphics.Rect
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for DetectionGeometryMapper coordinate transformation logic.
 *
 * Tests verify:
 * - ML Kit bbox to sensor space conversion (inverse rotation)
 * - Sensor bbox to bitmap crop conversion
 * - Debug info creation
 * - Mapped rect validation
 */
@RunWith(RobolectricTestRunner::class)
class DetectionGeometryMapperTest {
    // ==========================================
    // ML Kit bbox to sensor space tests
    // ==========================================

    @Test
    fun `mlKitBboxToSensorSpace with 0 rotation returns unchanged bbox`() {
        val mlKitBbox = Rect(100, 200, 300, 400)
        val sensorWidth = 1280
        val sensorHeight = 720

        val result =
            DetectionGeometryMapper.mlKitBboxToSensorSpace(
                mlKitBbox = mlKitBbox,
                rotationDegrees = 0,
                mlKitImageWidth = sensorWidth,
                mlKitImageHeight = sensorHeight,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
            )

        assertThat(result.left).isEqualTo(mlKitBbox.left)
        assertThat(result.top).isEqualTo(mlKitBbox.top)
        assertThat(result.right).isEqualTo(mlKitBbox.right)
        assertThat(result.bottom).isEqualTo(mlKitBbox.bottom)
    }

    @Test
    fun `mlKitBboxToSensorSpace with 90 rotation inverts rotation correctly`() {
        // In portrait mode (rotation=90):
        // - ML Kit sees rotated image (720x1280)
        // - ML Kit bbox is in 720x1280 space
        // - Sensor/bitmap is 1280x720
        //
        // Object at top-left of portrait view (ML Kit coords):
        // bbox in 720x1280: (50, 50, 150, 200) = top-left area
        //
        // In sensor 1280x720 coords, this should be at bottom-left
        // because 90° clockwise rotation of sensor gives portrait view

        val mlKitBbox = Rect(50, 50, 150, 200)
        val mlKitImageWidth = 720 // post-rotation width
        val mlKitImageHeight = 1280 // post-rotation height
        val sensorWidth = 1280
        val sensorHeight = 720

        val result =
            DetectionGeometryMapper.mlKitBboxToSensorSpace(
                mlKitBbox = mlKitBbox,
                rotationDegrees = 90,
                mlKitImageWidth = mlKitImageWidth,
                mlKitImageHeight = mlKitImageHeight,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
            )

        // Verify the result is in sensor space (1280x720)
        assertThat(result.left).isAtLeast(0)
        assertThat(result.top).isAtLeast(0)
        assertThat(result.right).isAtMost(sensorWidth)
        assertThat(result.bottom).isAtMost(sensorHeight)

        // Verify area is preserved (approximately)
        val originalArea = mlKitBbox.width() * mlKitBbox.height()
        val resultArea = result.width() * result.height()
        val areaRatio = resultArea.toFloat() / originalArea.toFloat()
        // Allow some tolerance due to coordinate system differences
        assertThat(areaRatio).isWithin(0.3f).of(1.0f)
    }

    @Test
    fun `mlKitBboxToSensorSpace with 270 rotation inverts rotation correctly`() {
        val mlKitBbox = Rect(100, 100, 200, 300)
        val mlKitImageWidth = 720 // post-rotation width (portrait)
        val mlKitImageHeight = 1280 // post-rotation height
        val sensorWidth = 1280
        val sensorHeight = 720

        val result =
            DetectionGeometryMapper.mlKitBboxToSensorSpace(
                mlKitBbox = mlKitBbox,
                rotationDegrees = 270,
                mlKitImageWidth = mlKitImageWidth,
                mlKitImageHeight = mlKitImageHeight,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
            )

        // Verify the result is in sensor space
        assertThat(result.left).isAtLeast(0)
        assertThat(result.top).isAtLeast(0)
        assertThat(result.right).isAtMost(sensorWidth)
        assertThat(result.bottom).isAtMost(sensorHeight)
    }

    @Test
    fun `mlKitBboxToSensorSpace with 180 rotation flips both axes`() {
        val mlKitBbox = Rect(100, 100, 300, 200)
        val width = 1280
        val height = 720

        val result =
            DetectionGeometryMapper.mlKitBboxToSensorSpace(
                mlKitBbox = mlKitBbox,
                rotationDegrees = 180,
                mlKitImageWidth = width,
                mlKitImageHeight = height,
                sensorWidth = width,
                sensorHeight = height,
            )

        // For 180°: (x, y) -> (1-x, 1-y) in normalized space
        // left=100/1280=0.078 -> 1-0.078=0.922 -> 0.922*1280=1180
        // But this is for right, so we need left = 1 - right_norm
        assertThat(result.left).isGreaterThan(width / 2)
        assertThat(result.top).isGreaterThan(height / 2)
    }

    @Test
    fun `mlKitBboxToSensorSpace centered box stays centered`() {
        // A centered box should stay centered regardless of rotation
        val mlKitImageWidth = 720
        val mlKitImageHeight = 1280
        val sensorWidth = 1280
        val sensorHeight = 720

        // Centered box in ML Kit 720x1280 space
        val mlKitBbox =
            Rect(
                (mlKitImageWidth * 0.4f).toInt(),
                (mlKitImageHeight * 0.4f).toInt(),
                (mlKitImageWidth * 0.6f).toInt(),
                (mlKitImageHeight * 0.6f).toInt(),
            )

        val result =
            DetectionGeometryMapper.mlKitBboxToSensorSpace(
                mlKitBbox = mlKitBbox,
                rotationDegrees = 90,
                mlKitImageWidth = mlKitImageWidth,
                mlKitImageHeight = mlKitImageHeight,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
            )

        // Center of result should be near center of sensor space
        val resultCenterX = (result.left + result.right) / 2f
        val resultCenterY = (result.top + result.bottom) / 2f

        assertThat(resultCenterX).isWithin(sensorWidth * 0.15f).of(sensorWidth / 2f)
        assertThat(resultCenterY).isWithin(sensorHeight * 0.15f).of(sensorHeight / 2f)
    }

    // ==========================================
    // Sensor bbox to bitmap crop tests
    // ==========================================

    @Test
    fun `sensorBboxToBitmapCrop returns rect within bitmap bounds`() {
        val bboxNorm = NormalizedRect(0.2f, 0.3f, 0.8f, 0.7f)
        val bitmapWidth = 1280
        val bitmapHeight = 720

        val result =
            DetectionGeometryMapper.sensorBboxToBitmapCrop(
                sensorBboxNorm = bboxNorm,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                paddingRatio = 0.06f,
            )

        assertThat(result.left).isAtLeast(0)
        assertThat(result.top).isAtLeast(0)
        assertThat(result.right).isAtMost(bitmapWidth)
        assertThat(result.bottom).isAtMost(bitmapHeight)
        assertThat(result.width()).isGreaterThan(0)
        assertThat(result.height()).isGreaterThan(0)
    }

    @Test
    fun `sensorBboxToBitmapCrop adds padding correctly`() {
        val bboxNorm = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
        val bitmapWidth = 1000
        val bitmapHeight = 1000

        val withoutPadding =
            DetectionGeometryMapper.sensorBboxToBitmapCrop(
                sensorBboxNorm = bboxNorm,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                paddingRatio = 0f,
            )

        val withPadding =
            DetectionGeometryMapper.sensorBboxToBitmapCrop(
                sensorBboxNorm = bboxNorm,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                paddingRatio = 0.1f,
            )

        // With padding should be larger
        assertThat(withPadding.width()).isGreaterThan(withoutPadding.width())
        assertThat(withPadding.height()).isGreaterThan(withoutPadding.height())
    }

    @Test
    fun `sensorBboxToBitmapCrop clamps edge cases to bitmap bounds`() {
        // Bbox that extends beyond bounds
        val bboxNorm = NormalizedRect(-0.1f, -0.1f, 1.1f, 1.1f)
        val bitmapWidth = 640
        val bitmapHeight = 480

        val result =
            DetectionGeometryMapper.sensorBboxToBitmapCrop(
                sensorBboxNorm = bboxNorm,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                paddingRatio = 0f,
            )

        // Should be clamped to bitmap bounds
        assertThat(result.left).isEqualTo(0)
        assertThat(result.top).isEqualTo(0)
        assertThat(result.right).isEqualTo(bitmapWidth)
        assertThat(result.bottom).isEqualTo(bitmapHeight)
    }

    // ==========================================
    // GeometryContext tests
    // ==========================================

    @Test
    fun `GeometryContext isPortrait returns true for 90 rotation`() {
        val context =
            DetectionGeometryMapper.GeometryContext(
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 90,
                previewWidth = 1080f,
                previewHeight = 1920f,
            )

        assertThat(context.isPortrait).isTrue()
    }

    @Test
    fun `GeometryContext isPortrait returns true for 270 rotation`() {
        val context =
            DetectionGeometryMapper.GeometryContext(
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 270,
                previewWidth = 1080f,
                previewHeight = 1920f,
            )

        assertThat(context.isPortrait).isTrue()
    }

    @Test
    fun `GeometryContext isPortrait returns false for 0 rotation`() {
        val context =
            DetectionGeometryMapper.GeometryContext(
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 0,
                previewWidth = 1920f,
                previewHeight = 1080f,
            )

        assertThat(context.isPortrait).isFalse()
    }

    @Test
    fun `GeometryContext uprightWidth swaps for portrait`() {
        val context =
            DetectionGeometryMapper.GeometryContext(
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 90,
                previewWidth = 1080f,
                previewHeight = 1920f,
            )

        // For portrait (90°), upright dimensions should be swapped
        assertThat(context.uprightWidth).isEqualTo(720)
        assertThat(context.uprightHeight).isEqualTo(1280)
    }

    @Test
    fun `GeometryContext uprightWidth unchanged for landscape`() {
        val context =
            DetectionGeometryMapper.GeometryContext(
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 0,
                previewWidth = 1920f,
                previewHeight = 1080f,
            )

        // For landscape (0°), upright dimensions match sensor
        assertThat(context.uprightWidth).isEqualTo(1280)
        assertThat(context.uprightHeight).isEqualTo(720)
    }

    // ==========================================
    // Mapped rect validation tests
    // ==========================================

    @Test
    fun `validateMappedRect returns true for rect within bounds`() {
        val rect = RectF(100f, 200f, 500f, 800f)
        val previewWidth = 1080f
        val previewHeight = 1920f

        val result =
            DetectionGeometryMapper.validateMappedRect(
                rect = rect,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                tolerance = 10f,
            )

        assertThat(result).isTrue()
    }

    @Test
    fun `validateMappedRect returns false for rect outside bounds`() {
        val rect = RectF(-200f, -200f, 100f, 100f)
        val previewWidth = 1080f
        val previewHeight = 1920f

        val result =
            DetectionGeometryMapper.validateMappedRect(
                rect = rect,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                tolerance = 10f,
            )

        assertThat(result).isFalse()
    }

    @Test
    fun `validateMappedRect allows rect within tolerance of bounds`() {
        // Rect slightly outside bounds but within tolerance
        val rect = RectF(-5f, -5f, 1085f, 1925f)
        val previewWidth = 1080f
        val previewHeight = 1920f

        val result =
            DetectionGeometryMapper.validateMappedRect(
                rect = rect,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                tolerance = 50f,
            )

        assertThat(result).isTrue()
    }

    @Test
    fun `validateMappedRect returns false for zero-size rect`() {
        val rect = RectF(100f, 100f, 100f, 100f) // Zero width and height
        val previewWidth = 1080f
        val previewHeight = 1920f

        val result =
            DetectionGeometryMapper.validateMappedRect(
                rect = rect,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                tolerance = 50f,
            )

        assertThat(result).isFalse()
    }
}
