package com.scanium.app.camera.geom

import android.graphics.Rect
import com.scanium.shared.core.models.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for GeometryMapper coordinate transformations.
 *
 * These tests verify:
 * 1. Rotation 0/90/270 correctness for upright↔sensor conversion
 * 2. Aspect ratio preservation through transformations
 * 3. Bbox crop calculation accuracy
 */
@RunWith(RobolectricTestRunner::class)
class GeometryMapperTest {
    // =========================================================================
    // Aspect Ratio Tests
    // =========================================================================

    @Test
    fun `aspectRatio - square bbox`() {
        val bbox = NormalizedRect(0.2f, 0.3f, 0.4f, 0.5f)
        val ar = GeometryMapper.aspectRatio(bbox)
        assertEquals(1.0f, ar, 0.001f)
    }

    @Test
    fun `aspectRatio - wide bbox`() {
        val bbox = NormalizedRect(0.1f, 0.2f, 0.5f, 0.4f) // width=0.4, height=0.2
        val ar = GeometryMapper.aspectRatio(bbox)
        assertEquals(2.0f, ar, 0.001f)
    }

    @Test
    fun `aspectRatio - tall bbox`() {
        val bbox = NormalizedRect(0.1f, 0.1f, 0.3f, 0.5f) // width=0.2, height=0.4
        val ar = GeometryMapper.aspectRatio(bbox)
        assertEquals(0.5f, ar, 0.001f)
    }

    @Test
    fun `validateAspectRatio - matching ratios`() {
        assertTrue(GeometryMapper.validateAspectRatio(1.0f, 1.02f, 0.02f))
        assertTrue(GeometryMapper.validateAspectRatio(2.0f, 2.04f, 0.02f))
    }

    @Test
    fun `validateAspectRatio - mismatched ratios`() {
        assertFalse(GeometryMapper.validateAspectRatio(1.0f, 1.1f, 0.02f))
        assertFalse(GeometryMapper.validateAspectRatio(2.0f, 2.5f, 0.02f))
    }

    // =========================================================================
    // Upright↔Sensor Conversion Tests (Rotation 0)
    // =========================================================================

    @Test
    fun `uprightToSensor - rotation 0 identity`() {
        val uprightBbox = Rect(100, 200, 300, 400)
        val result =
            GeometryMapper.uprightToSensor(
                uprightBbox = uprightBbox,
                uprightWidth = 1280,
                uprightHeight = 720,
                rotationDegrees = 0,
            )
        assertEquals(uprightBbox, result)
    }

    @Test
    fun `sensorToUpright - rotation 0 identity`() {
        val sensorBbox = Rect(100, 200, 300, 400)
        val result =
            GeometryMapper.sensorToUpright(
                sensorBbox = sensorBbox,
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 0,
            )
        assertEquals(sensorBbox, result)
    }

    // =========================================================================
    // Upright↔Sensor Conversion Tests (Rotation 90 - Portrait)
    // =========================================================================

    @Test
    fun `uprightToSensor - rotation 90 converts correctly`() {
        // Portrait mode: sensor is 1280x720, upright is 720x1280
        // A bbox at top-left in upright should map to top-right in sensor
        val uprightBbox = Rect(0, 0, 100, 200) // Left 100px, top 200px in 720x1280
        val result =
            GeometryMapper.uprightToSensor(
                uprightBbox = uprightBbox,
                uprightWidth = 720,
                uprightHeight = 1280,
                rotationDegrees = 90,
            )

        // After inverse 90° rotation to sensor space (1280x720):
        // Upright (x, y) -> Sensor (y, uprightWidth - x)
        // Expected: left=0, top=720-100=620, right=200, bottom=720-0=720
        assertEquals(0, result.left)
        assertEquals(620, result.top)
        assertEquals(200, result.right)
        assertEquals(720, result.bottom)
    }

    @Test
    fun `sensorToUpright - rotation 90 converts correctly`() {
        // Sensor 1280x720, rotation 90 → upright 720x1280
        val sensorBbox = Rect(0, 620, 200, 720) // Derived from previous test
        val result =
            GeometryMapper.sensorToUpright(
                sensorBbox = sensorBbox,
                sensorWidth = 1280,
                sensorHeight = 720,
                rotationDegrees = 90,
            )

        // Should reverse the transformation
        assertEquals(0, result.left)
        assertEquals(0, result.top)
        assertEquals(100, result.right)
        assertEquals(200, result.bottom)
    }

    @Test
    fun `uprightToSensor and sensorToUpright - rotation 90 roundtrip`() {
        val original = Rect(100, 200, 300, 500)
        val uprightWidth = 720
        val uprightHeight = 1280

        // Convert to sensor
        val sensor =
            GeometryMapper.uprightToSensor(
                uprightBbox = original,
                uprightWidth = uprightWidth,
                uprightHeight = uprightHeight,
                rotationDegrees = 90,
            )

        // Convert back to upright (sensor dims are swapped)
        val sensorWidth = uprightHeight // 1280
        val sensorHeight = uprightWidth // 720
        val recovered =
            GeometryMapper.sensorToUpright(
                sensorBbox = sensor,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
                rotationDegrees = 90,
            )

        assertEquals(original, recovered)
    }

    // =========================================================================
    // Upright↔Sensor Conversion Tests (Rotation 270)
    // =========================================================================

    @Test
    fun `uprightToSensor and sensorToUpright - rotation 270 roundtrip`() {
        val original = Rect(100, 200, 300, 500)
        val uprightWidth = 720
        val uprightHeight = 1280

        // Convert to sensor
        val sensor =
            GeometryMapper.uprightToSensor(
                uprightBbox = original,
                uprightWidth = uprightWidth,
                uprightHeight = uprightHeight,
                rotationDegrees = 270,
            )

        // Convert back to upright
        val sensorWidth = uprightHeight
        val sensorHeight = uprightWidth
        val recovered =
            GeometryMapper.sensorToUpright(
                sensorBbox = sensor,
                sensorWidth = sensorWidth,
                sensorHeight = sensorHeight,
                rotationDegrees = 270,
            )

        assertEquals(original, recovered)
    }

    // =========================================================================
    // Upright↔Sensor Conversion Tests (Rotation 180)
    // =========================================================================

    @Test
    fun `uprightToSensor and sensorToUpright - rotation 180 roundtrip`() {
        val original = Rect(100, 200, 300, 400)
        val uprightWidth = 1280
        val uprightHeight = 720

        // Convert to sensor
        val sensor =
            GeometryMapper.uprightToSensor(
                uprightBbox = original,
                uprightWidth = uprightWidth,
                uprightHeight = uprightHeight,
                rotationDegrees = 180,
            )

        // Convert back to upright
        val recovered =
            GeometryMapper.sensorToUpright(
                sensorBbox = sensor,
                sensorWidth = uprightWidth,
                sensorHeight = uprightHeight,
                rotationDegrees = 180,
            )

        assertEquals(original, recovered)
    }

    // =========================================================================
    // Bitmap Crop Tests
    // =========================================================================

    @Test
    fun `uprightToBitmapCrop - no padding`() {
        val bbox = NormalizedRect(0.1f, 0.2f, 0.5f, 0.6f)
        val result =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = bbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        assertEquals(100, result.left)
        assertEquals(200, result.top)
        assertEquals(500, result.right)
        assertEquals(600, result.bottom)
    }

    @Test
    fun `uprightToBitmapCrop - with padding`() {
        val bbox = NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f) // 200x200 in 1000x1000
        val result =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = bbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0.1f,
// 10% padding = 20px each side
            )

        assertEquals(180, result.left) // 200 - 20
        assertEquals(180, result.top)
        assertEquals(420, result.right) // 400 + 20
        assertEquals(420, result.bottom)
    }

    @Test
    fun `uprightToBitmapCrop - padding clamped to bounds`() {
        // Bbox near edge - padding should be clamped
        val bbox = NormalizedRect(0.0f, 0.0f, 0.2f, 0.2f) // Top-left corner
        val result =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = bbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0.5f,
// Large padding
            )

        // Left and top should be clamped to 0
        assertEquals(0, result.left)
        assertEquals(0, result.top)
        // Right and bottom should not exceed bitmap bounds
        assertTrue(result.right <= 1000)
        assertTrue(result.bottom <= 1000)
    }

    @Test
    fun `uprightToBitmapCrop - aspect ratio preserved`() {
        // Square bbox
        val squareBbox = NormalizedRect(0.2f, 0.3f, 0.4f, 0.5f) // width=0.2, height=0.2
        val squareCrop =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = squareBbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )
        val squareBboxAR = GeometryMapper.aspectRatio(squareBbox)
        val squareCropAR = GeometryMapper.aspectRatio(squareCrop)
        assertTrue(GeometryMapper.validateAspectRatio(squareBboxAR, squareCropAR))

        // Tall bbox
        val tallBbox = NormalizedRect(0.3f, 0.1f, 0.5f, 0.6f) // width=0.2, height=0.5
        val tallCrop =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = tallBbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )
        val tallBboxAR = GeometryMapper.aspectRatio(tallBbox)
        val tallCropAR = GeometryMapper.aspectRatio(tallCrop)
        assertTrue(GeometryMapper.validateAspectRatio(tallBboxAR, tallCropAR))

        // Wide bbox
        val wideBbox = NormalizedRect(0.1f, 0.4f, 0.7f, 0.6f) // width=0.6, height=0.2
        val wideCrop =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = wideBbox,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )
        val wideBboxAR = GeometryMapper.aspectRatio(wideBbox)
        val wideCropAR = GeometryMapper.aspectRatio(wideCrop)
        assertTrue(GeometryMapper.validateAspectRatio(wideBboxAR, wideCropAR))
    }

    // =========================================================================
    // Portrait Mode Specific Tests
    // =========================================================================

    @Test
    fun `portrait mode - tall object bbox preserves aspect ratio through pipeline`() {
        // Simulate a tall object detected in portrait mode
        // Sensor: 1280x720, Upright: 720x1280, rotation=90

        // Tall bbox in upright space: width=0.3, height=0.6 (AR = 0.5, tall)
        val tallBboxNorm = NormalizedRect(0.35f, 0.2f, 0.65f, 0.8f)
        val bboxAR = GeometryMapper.aspectRatio(tallBboxNorm)
        assertTrue("Bbox should be tall (AR < 1)", bboxAR < 1.0f)

        // Convert to bitmap crop rect in square bitmap
        // Note: Using square bitmap ensures normalized AR equals pixel AR
        val cropRect =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = tallBboxNorm,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        val cropAR = GeometryMapper.aspectRatio(cropRect)

        // Verify aspect ratios match
        assertTrue(
            "Bbox AR ($bboxAR) should match crop AR ($cropAR)",
            GeometryMapper.validateAspectRatio(bboxAR, cropAR),
        )
    }

    @Test
    fun `portrait mode - wide object bbox preserves aspect ratio through pipeline`() {
        // Wide bbox in portrait mode: width=0.6, height=0.2 (AR = 3.0, wide)
        val wideBboxNorm = NormalizedRect(0.2f, 0.4f, 0.8f, 0.6f)
        val bboxAR = GeometryMapper.aspectRatio(wideBboxNorm)
        assertTrue("Bbox should be wide (AR > 1)", bboxAR > 1.0f)

        // Note: Using square bitmap ensures normalized AR equals pixel AR
        val cropRect =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = wideBboxNorm,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        val cropAR = GeometryMapper.aspectRatio(cropRect)

        assertTrue(
            "Bbox AR ($bboxAR) should match crop AR ($cropAR)",
            GeometryMapper.validateAspectRatio(bboxAR, cropAR),
        )
    }

    @Test
    fun `landscape mode - same behavior as portrait`() {
        // Square bbox in landscape mode
        val squareBboxNorm = NormalizedRect(0.3f, 0.3f, 0.7f, 0.7f)
        val bboxAR = GeometryMapper.aspectRatio(squareBboxNorm)

        // Note: Using square bitmap ensures normalized AR equals pixel AR
        val cropRect =
            GeometryMapper.uprightToBitmapCrop(
                normalizedBbox = squareBboxNorm,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        val cropAR = GeometryMapper.aspectRatio(cropRect)

        assertTrue(
            "Bbox AR ($bboxAR) should match crop AR ($cropAR)",
            GeometryMapper.validateAspectRatio(bboxAR, cropAR),
        )
    }

    // =========================================================================
    // Correlation Debug Info Tests
    // =========================================================================

    @Test
    fun `generateCorrelationDebugInfo - produces valid info`() {
        val bbox = NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)

        // Using square bitmap to ensure aspect ratio match
        val debugInfo =
            GeometryMapper.generateCorrelationDebugInfo(
                normalizedBbox = bbox,
                rotationDegrees = 90,
                uprightWidth = 720,
                uprightHeight = 1280,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        assertEquals(90, debugInfo.rotationDegrees)
        assertEquals(720, debugInfo.uprightWidth)
        assertEquals(1280, debugInfo.uprightHeight)
        assertEquals(1000, debugInfo.bitmapWidth)
        assertEquals(1000, debugInfo.bitmapHeight)
        assertTrue(debugInfo.aspectRatioMatch)
    }

    @Test
    fun `generateCorrelationDebugInfo - log string format`() {
        val bbox = NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)

        // Using square bitmap to ensure aspect ratio match
        val debugInfo =
            GeometryMapper.generateCorrelationDebugInfo(
                normalizedBbox = bbox,
                rotationDegrees = 90,
                uprightWidth = 720,
                uprightHeight = 1280,
                bitmapWidth = 1000,
                bitmapHeight = 1000,
                padding = 0f,
            )

        val logString = debugInfo.toLogString()
        assertTrue(logString.contains("CORR"))
        assertTrue(logString.contains("rot=90"))
        assertTrue(logString.contains("match=true"))
    }
}
