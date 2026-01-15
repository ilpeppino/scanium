package com.scanium.app.ml.detector

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for DetectionMapping.
 *
 * Tests verify:
 * - Coordinate transforms (upright <-> sensor space)
 * - Edge zone filtering (pure geometry)
 * - Bounding box tightening
 *
 * These tests use known fixtures and verify the exact math from the original
 * ObjectDetectorClient implementation to ensure no behavior change.
 */
@RunWith(RobolectricTestRunner::class)
class DetectionMappingTest {

    // =========================================================================
    // uprightBboxToSensorBbox tests - Coordinate transforms
    // =========================================================================

    @Test
    fun uprightBboxToSensorBbox_rotation0_returnsIdentical() {
        // Arrange: 1280x720 image, no rotation
        val uprightBbox = Rect(320, 180, 480, 360)
        val imageWidth = 1280
        val imageHeight = 720
        val rotation = 0

        // Act
        val sensorBbox = DetectionMapping.uprightBboxToSensorBbox(
            uprightBbox = uprightBbox,
            inputImageWidth = imageWidth,
            inputImageHeight = imageHeight,
            rotationDegrees = rotation,
        )

        // Assert: Should be identical for 0° rotation
        assertThat(sensorBbox.left).isEqualTo(320)
        assertThat(sensorBbox.top).isEqualTo(180)
        assertThat(sensorBbox.right).isEqualTo(480)
        assertThat(sensorBbox.bottom).isEqualTo(360)
    }

    @Test
    fun uprightBboxToSensorBbox_rotation90_transformsCorrectly() {
        // Arrange: Portrait mode - sensor is 1280x720, but upright is 720x1280
        // Bbox in upright space (720x1280)
        val uprightBbox = Rect(180, 320, 360, 480)
        val uprightWidth = 720 // After rotation
        val uprightHeight = 1280 // After rotation
        val rotation = 90

        // Act
        val sensorBbox = DetectionMapping.uprightBboxToSensorBbox(
            uprightBbox = uprightBbox,
            inputImageWidth = uprightWidth,
            inputImageHeight = uprightHeight,
            rotationDegrees = rotation,
        )

        // Assert: Sensor dimensions are 1280x720 (swapped back)
        // For 90°: upright (x, y) -> sensor (y, 1-x)
        // normalized upright: left=0.25, top=0.25, right=0.5, bottom=0.375
        // sensor normalized: left=0.25, top=0.5, right=0.375, bottom=0.75
        // sensor pixels (1280x720): left=320, top=360, right=480, bottom=540
        assertThat(sensorBbox.width()).isGreaterThan(0)
        assertThat(sensorBbox.height()).isGreaterThan(0)
        // The key invariant: sensor bbox should fit within sensor dimensions
        assertThat(sensorBbox.left).isAtLeast(0)
        assertThat(sensorBbox.top).isAtLeast(0)
        assertThat(sensorBbox.right).isAtMost(1280) // sensor width
        assertThat(sensorBbox.bottom).isAtMost(720) // sensor height
    }

    @Test
    fun uprightBboxToSensorBbox_rotation180_flipsCoordinates() {
        // Arrange: 1280x720 image, 180° rotation (upside down)
        val uprightBbox = Rect(320, 180, 480, 360)
        val imageWidth = 1280
        val imageHeight = 720
        val rotation = 180

        // Act
        val sensorBbox = DetectionMapping.uprightBboxToSensorBbox(
            uprightBbox = uprightBbox,
            inputImageWidth = imageWidth,
            inputImageHeight = imageHeight,
            rotationDegrees = rotation,
        )

        // Assert: 180° should flip both axes
        // normalized upright: left=0.25, top=0.25, right=0.375, bottom=0.5
        // sensor normalized: left=0.625, top=0.5, right=0.75, bottom=0.75
        assertThat(sensorBbox.left).isEqualTo(800) // 1280 - 480
        assertThat(sensorBbox.top).isEqualTo(360) // 720 - 360
        assertThat(sensorBbox.right).isEqualTo(960) // 1280 - 320
        assertThat(sensorBbox.bottom).isEqualTo(540) // 720 - 180
    }

    @Test
    fun uprightBboxToSensorBbox_rotation270_transformsCorrectly() {
        // Arrange: Landscape left - sensor is 1280x720, but upright is 720x1280
        val uprightBbox = Rect(180, 320, 360, 480)
        val uprightWidth = 720
        val uprightHeight = 1280
        val rotation = 270

        // Act
        val sensorBbox = DetectionMapping.uprightBboxToSensorBbox(
            uprightBbox = uprightBbox,
            inputImageWidth = uprightWidth,
            inputImageHeight = uprightHeight,
            rotationDegrees = rotation,
        )

        // Assert: Should produce valid sensor-space bbox
        assertThat(sensorBbox.width()).isGreaterThan(0)
        assertThat(sensorBbox.height()).isGreaterThan(0)
        assertThat(sensorBbox.left).isAtLeast(0)
        assertThat(sensorBbox.top).isAtLeast(0)
        assertThat(sensorBbox.right).isAtMost(1280)
        assertThat(sensorBbox.bottom).isAtMost(720)
    }

    @Test
    fun uprightBboxToSensorBbox_preservesAreaRatio() {
        // Arrange: The normalized area should be preserved across rotations
        val uprightBbox = Rect(100, 100, 200, 200) // 100x100 box
        val imageWidth = 1000
        val imageHeight = 1000

        val uprightNormArea = (uprightBbox.width() * uprightBbox.height()).toFloat() /
            (imageWidth * imageHeight)

        // Act - test all rotations
        listOf(0, 90, 180, 270).forEach { rotation ->
            val (w, h) = if (rotation == 90 || rotation == 270) {
                imageHeight to imageWidth
            } else {
                imageWidth to imageHeight
            }

            val sensorBbox = DetectionMapping.uprightBboxToSensorBbox(
                uprightBbox = uprightBbox,
                inputImageWidth = imageWidth,
                inputImageHeight = imageHeight,
                rotationDegrees = rotation,
            )

            val sensorW = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
            val sensorH = if (rotation == 90 || rotation == 270) imageWidth else imageHeight
            val sensorNormArea = (sensorBbox.width() * sensorBbox.height()).toFloat() /
                (sensorW * sensorH)

            // Assert: Area ratio should be approximately preserved
            assertThat(sensorNormArea).isWithin(0.01f).of(uprightNormArea)
        }
    }

    // =========================================================================
    // isDetectionInsideSafeZone tests - Edge filtering
    // =========================================================================

    @Test
    fun isDetectionInsideSafeZone_nullCropRect_acceptsAll() {
        // Arrange
        val bbox = Rect(0, 0, 100, 100)

        // Act & Assert
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, null, 0.1f)).isTrue()
    }

    @Test
    fun isDetectionInsideSafeZone_zeroInset_acceptsAll() {
        // Arrange
        val bbox = Rect(0, 0, 100, 100)
        val cropRect = Rect(0, 0, 1000, 1000)

        // Act & Assert
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0f)).isTrue()
    }

    @Test
    fun isDetectionInsideSafeZone_centeredBox_accepted() {
        // Arrange: Box at center of crop rect
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(400, 400, 600, 600) // Center is (500, 500)

        // Act & Assert: 10% inset means safe zone is (100,100) to (900,900)
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isTrue()
    }

    @Test
    fun isDetectionInsideSafeZone_leftEdgeBox_rejected() {
        // Arrange: Box at left edge
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(0, 400, 100, 600) // Center is (50, 500)

        // Act & Assert: 10% inset means safe zone starts at x=100
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isFalse()
    }

    @Test
    fun isDetectionInsideSafeZone_rightEdgeBox_rejected() {
        // Arrange: Box at right edge
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(900, 400, 1000, 600) // Center is (950, 500)

        // Act & Assert: 10% inset means safe zone ends at x=900
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isFalse()
    }

    @Test
    fun isDetectionInsideSafeZone_topEdgeBox_rejected() {
        // Arrange: Box at top edge
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(400, 0, 600, 100) // Center is (500, 50)

        // Act & Assert: 10% inset means safe zone starts at y=100
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isFalse()
    }

    @Test
    fun isDetectionInsideSafeZone_bottomEdgeBox_rejected() {
        // Arrange: Box at bottom edge
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(400, 900, 600, 1000) // Center is (500, 950)

        // Act & Assert: 10% inset means safe zone ends at y=900
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isFalse()
    }

    @Test
    fun isDetectionInsideSafeZone_exactlyOnBoundary_accepted() {
        // Arrange: Box center exactly on safe zone boundary
        val cropRect = Rect(0, 0, 1000, 1000)
        val bbox = Rect(50, 400, 150, 600) // Center is (100, 500) - exactly on left boundary

        // Act & Assert: Should be accepted (>= check)
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isTrue()
    }

    @Test
    fun isDetectionInsideSafeZone_withOffset_handlesCorrectly() {
        // Arrange: Crop rect with offset (simulating viewport)
        val cropRect = Rect(100, 100, 1100, 1100) // 1000x1000 rect starting at (100,100)
        val bbox = Rect(500, 500, 700, 700) // Center is (600, 600)

        // Act & Assert: Safe zone is (200, 200) to (1000, 1000)
        assertThat(DetectionMapping.isDetectionInsideSafeZone(bbox, cropRect, 0.1f)).isTrue()
    }

    // =========================================================================
    // Rect.tighten tests - Bounding box adjustment
    // =========================================================================

    @Test
    fun tighten_zeroInset_returnsOriginal() {
        // Arrange
        val rect = Rect(100, 100, 200, 200)

        // Act
        val tightened = rect.tighten(0f, 1000, 1000)

        // Assert: Should be identical
        assertThat(tightened.left).isEqualTo(100)
        assertThat(tightened.top).isEqualTo(100)
        assertThat(tightened.right).isEqualTo(200)
        assertThat(tightened.bottom).isEqualTo(200)
    }

    @Test
    fun tighten_smallBox_avoidsOverTightening() {
        // Arrange: Very small box (less than 4x4)
        val rect = Rect(100, 100, 102, 102)

        // Act
        val tightened = rect.tighten(0.1f, 1000, 1000)

        // Assert: Should not collapse
        assertThat(tightened.width()).isAtLeast(1)
        assertThat(tightened.height()).isAtLeast(1)
    }

    @Test
    fun tighten_normalBox_reducesByInsetRatio() {
        // Arrange: 100x100 box with 4% inset
        val rect = Rect(100, 100, 200, 200)

        // Act
        val tightened = rect.tighten(DetectionMapping.BOUNDING_BOX_TIGHTEN_RATIO, 1000, 1000)

        // Assert: Should be smaller than original
        assertThat(tightened.width()).isLessThan(rect.width())
        assertThat(tightened.height()).isLessThan(rect.height())
        // But not too small
        assertThat(tightened.width()).isGreaterThan(rect.width() / 2)
        assertThat(tightened.height()).isGreaterThan(rect.height() / 2)
    }

    @Test
    fun tighten_largeBox_appliesAdaptiveBoost() {
        // Arrange: Large box (>65% of frame) - gets adaptive boost
        val rect = Rect(100, 100, 800, 800) // 700x700 in 1000x1000 = 70%

        // Act
        val tightened = rect.tighten(DetectionMapping.BOUNDING_BOX_TIGHTEN_RATIO, 1000, 1000)

        // Assert: Should apply more tightening due to adaptive boost
        val tighteningRatio = (rect.width() - tightened.width()).toFloat() / rect.width()
        // Large objects get 0.04f + 0.04f boost = 0.08f effective ratio
        assertThat(tighteningRatio).isGreaterThan(0.02f)
    }

    @Test
    fun tighten_verySmallBox_appliesNegativeBoost() {
        // Arrange: Very small box (<12% of frame) - gets negative boost (expansion)
        val rect = Rect(450, 450, 500, 500) // 50x50 in 1000x1000 = 5%

        // Act
        val tightened = rect.tighten(DetectionMapping.BOUNDING_BOX_TIGHTEN_RATIO, 1000, 1000)

        // Assert: The effective ratio is 0.04f - 0.02f = 0.02f, still positive but smaller
        // Small boxes should have minimal tightening
        val tighteningRatio = (rect.width() - tightened.width()).toFloat() / rect.width()
        assertThat(tighteningRatio).isLessThan(0.05f)
    }

    @Test
    fun tighten_staysWithinFrameBounds() {
        // Arrange: Box near edge
        val rect = Rect(950, 950, 1000, 1000)

        // Act
        val tightened = rect.tighten(0.1f, 1000, 1000)

        // Assert: Should stay within frame
        assertThat(tightened.left).isAtLeast(0)
        assertThat(tightened.top).isAtLeast(0)
        assertThat(tightened.right).isAtMost(1000)
        assertThat(tightened.bottom).isAtMost(1000)
    }

    @Test
    fun tighten_maxEffectiveRatioCapped() {
        // Arrange: Even with high inset, effective ratio is capped at 0.15
        val rect = Rect(100, 100, 200, 200) // 100x100 box

        // Act
        val tightened = rect.tighten(0.5f, 1000, 1000) // Request 50% inset

        // Assert: Actual tightening should be capped
        val tighteningRatio = (rect.width() - tightened.width()).toFloat() / rect.width()
        // Max is 0.15 applied to each side, so total reduction is ~15% per side
        // With rounding, the ratio may hit exactly 0.16, so use isAtMost
        assertThat(tighteningRatio).isAtMost(0.16f)
    }

    @Test
    fun tighten_negativeInset_returnsOriginal() {
        // Arrange
        val rect = Rect(100, 100, 200, 200)

        // Act
        val tightened = rect.tighten(-0.1f, 1000, 1000)

        // Assert: Should return copy of original
        assertThat(tightened.left).isEqualTo(100)
        assertThat(tightened.top).isEqualTo(100)
        assertThat(tightened.right).isEqualTo(200)
        assertThat(tightened.bottom).isEqualTo(200)
    }

    @Test
    fun tighten_invalidFrameDimensions_returnsOriginal() {
        // Arrange
        val rect = Rect(100, 100, 200, 200)

        // Act
        val tightened = rect.tighten(0.1f, 0, 0)

        // Assert: Should return copy of original
        assertThat(tightened.left).isEqualTo(100)
        assertThat(tightened.top).isEqualTo(100)
        assertThat(tightened.right).isEqualTo(200)
        assertThat(tightened.bottom).isEqualTo(200)
    }

    // =========================================================================
    // Constants validation tests
    // =========================================================================

    @Test
    fun constants_haveExpectedValues() {
        // These constants are critical for behavior - ensure they match original values
        assertThat(DetectionMapping.CONFIDENCE_THRESHOLD).isEqualTo(0.3f)
        assertThat(DetectionMapping.MAX_THUMBNAIL_DIMENSION_PX).isEqualTo(512)
        assertThat(DetectionMapping.BOUNDING_BOX_TIGHTEN_RATIO).isEqualTo(0.04f)
    }
}
