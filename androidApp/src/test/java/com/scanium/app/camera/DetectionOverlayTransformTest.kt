package com.scanium.app.camera

import android.graphics.Rect
import android.util.Size
import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * Unit tests for DetectionOverlay coordinate transformation logic.
 *
 * Tests verify:
 * - Aspect ratio handling for different image/preview combinations
 * - Bounding box transformation accuracy
 * - Center point calculation for any annotations that use box centers
 * - Edge cases (small boxes, full-screen boxes, portrait/landscape)
 * - Scaling and offset calculations
 * - Rotation transformations for portrait mode (new)
 *
 * Note: The DetectionOverlay now renders bounding boxes around detections,
 * so transform math must keep the boxes aligned with the underlying camera buffer.
 */
@RunWith(RobolectricTestRunner::class)
class DetectionOverlayTransformTest {

    // ==========================================
    // NEW TESTS: Rotation-aware transformations
    // ==========================================

    @Test
    fun `rotateNormalizedRect with 0 degrees returns unchanged rect`() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 0)

        assertThat(rotated.left).isEqualTo(rect.left)
        assertThat(rotated.top).isEqualTo(rect.top)
        assertThat(rotated.right).isEqualTo(rect.right)
        assertThat(rotated.bottom).isEqualTo(rect.bottom)
    }

    @Test
    fun `rotateNormalizedRect with 90 degrees rotates clockwise`() {
        // Original: top-left corner of landscape image (left=0, top=0, right=0.2, bottom=0.2)
        // After 90° rotation: should be top-right corner in portrait
        // (x, y) -> (y, 1-x)
        val rect = NormalizedRect(0f, 0f, 0.2f, 0.2f)
        val rotated = rotateNormalizedRect(rect, 90)

        // left -> top (0 -> 0)
        // top -> 1-right (0 -> 0.8)
        // right -> bottom (0.2 -> 0.2)
        // bottom -> 1-left (0.2 -> 1)
        assertThat(rotated.left).isWithin(0.001f).of(0f)
        assertThat(rotated.top).isWithin(0.001f).of(0.8f)
        assertThat(rotated.right).isWithin(0.001f).of(0.2f)
        assertThat(rotated.bottom).isWithin(0.001f).of(1f)
    }

    @Test
    fun `rotateNormalizedRect with 180 degrees flips both axes`() {
        // (x, y) -> (1-x, 1-y)
        val rect = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 180)

        assertThat(rotated.left).isWithin(0.001f).of(0.7f)   // 1 - 0.3
        assertThat(rotated.top).isWithin(0.001f).of(0.6f)    // 1 - 0.4
        assertThat(rotated.right).isWithin(0.001f).of(0.9f)  // 1 - 0.1
        assertThat(rotated.bottom).isWithin(0.001f).of(0.8f) // 1 - 0.2
    }

    @Test
    fun `rotateNormalizedRect with 270 degrees rotates counter-clockwise`() {
        // (x, y) -> (1-y, x)
        val rect = NormalizedRect(0f, 0f, 0.2f, 0.2f)
        val rotated = rotateNormalizedRect(rect, 270)

        // left -> 1-bottom (0 -> 0.8)
        // top -> left (0 -> 0)
        // right -> 1-top (0.2 -> 1)
        // bottom -> right (0.2 -> 0.2)
        assertThat(rotated.left).isWithin(0.001f).of(0.8f)
        assertThat(rotated.top).isWithin(0.001f).of(0f)
        assertThat(rotated.right).isWithin(0.001f).of(1f)
        assertThat(rotated.bottom).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `rotateNormalizedRect with center box stays centered`() {
        // A centered box should stay centered after any rotation
        val rect = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)

        for (degrees in listOf(0, 90, 180, 270)) {
            val rotated = rotateNormalizedRect(rect, degrees)
            val centerX = (rotated.left + rotated.right) / 2f
            val centerY = (rotated.top + rotated.bottom) / 2f

            assertThat(centerX).isWithin(0.001f).of(0.5f)
            assertThat(centerY).isWithin(0.001f).of(0.5f)
        }
    }

    @Test
    fun `calculateTransformWithRotation swaps dimensions for 90 degree rotation`() {
        // Sensor: 1280x720 landscape
        // Preview: 1080x1920 portrait
        // Rotation: 90 degrees (portrait mode)
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // Effective dimensions after rotation: 720x1280 (portrait)
        assertThat(transform.effectiveImageWidth).isEqualTo(720)
        assertThat(transform.effectiveImageHeight).isEqualTo(1280)
        assertThat(transform.rotationDegrees).isEqualTo(90)
    }

    @Test
    fun `calculateTransformWithRotation with FILL_CENTER uses larger or equal scale`() {
        val fillTransform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        val fitTransform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FIT_CENTER
        )

        // FILL_CENTER should never scale less than FIT_CENTER (fills the preview)
        assertThat(fillTransform.scale).isAtLeast(fitTransform.scale)
    }

    @Test
    fun `calculateTransformWithRotation with 0 degrees does not swap dimensions`() {
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1920f,
            previewHeight = 1080f,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FIT_CENTER
        )

        // Effective dimensions unchanged
        assertThat(transform.effectiveImageWidth).isEqualTo(1280)
        assertThat(transform.effectiveImageHeight).isEqualTo(720)
    }

    @Test
    fun `mapBboxToPreview applies rotation and scale correctly in portrait mode`() {
        // Simulate portrait mode: sensor 1280x720, rotation 90, preview 720x1280
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 720f,
            previewHeight = 1280f,
            rotationDegrees = 90,
            scaleType = PreviewScaleType.FILL_CENTER
        )

        // Object in top-right of sensor image (landscape coords)
        // After 90° rotation should appear at top of portrait preview
        val bboxNorm = NormalizedRect(
            left = 0.8f,   // right side in landscape
            top = 0f,      // top in landscape
            right = 1f,
            bottom = 0.2f
        )

        val result = mapBboxToPreview(bboxNorm, transform)

        // After rotation: should be near top of portrait preview
        // Check that top is in upper portion of preview
        assertThat(result.top).isLessThan(transform.effectiveImageHeight * transform.scale * 0.3f + transform.offsetY)
    }

    @Test
    fun `mapBboxToPreview full frame box maps to preview bounds`() {
        // Test with FIT_CENTER to ensure full image maps to preview
        val transform = calculateTransformWithRotation(
            imageWidth = 1280,
            imageHeight = 720,
            previewWidth = 1280f,
            previewHeight = 720f,
            rotationDegrees = 0,
            scaleType = PreviewScaleType.FIT_CENTER
        )

        // Full frame bbox
        val fullFrameBbox = NormalizedRect(0f, 0f, 1f, 1f)
        val result = mapBboxToPreview(fullFrameBbox, transform)

        // Should map to approximately full preview
        assertThat(result.left).isWithin(1f).of(0f)
        assertThat(result.top).isWithin(1f).of(0f)
        assertThat(result.right).isWithin(1f).of(1280f)
        assertThat(result.bottom).isWithin(1f).of(720f)
    }

    @Test
    fun `rotateNormalizedRect preserves area after rotation`() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.5f, 0.6f)
        val originalArea = rect.width * rect.height

        for (degrees in listOf(0, 90, 180, 270)) {
            val rotated = rotateNormalizedRect(rect, degrees)
            val rotatedArea = rotated.width * rotated.height

            assertThat(rotatedArea).isWithin(0.001f).of(originalArea)
        }
    }

    // ==========================================
    // Legacy tests (kept for reference, some marked @Ignore)
    // ==========================================

    @Ignore("Requires updated overlay transform contract; skip for now.")

    @Test
    fun whenImageAndPreviewHaveSameAspectRatio_thenNoOffsetApplied() {
        // Arrange - Both 16:9 aspect ratio
        val imageSize = Size(1920, 1080)
        val previewSize = Size(1600, 900)
        val box = Rect(100, 100, 200, 200)

        // Act - Transform would scale but not offset
        // Expected scale: 1600/1920 = 0.833
        val scaleX = previewSize.width.toFloat() / imageSize.width
        val scaleY = previewSize.height.toFloat() / imageSize.height

        // Assert - Equal scaling for same aspect ratio
        assertThat(abs(scaleX - scaleY)).isLessThan(0.01f)
    }

    @Test
    fun whenImageIsWiderThanPreview_thenVerticalOffsetApplied() {
        // Arrange - Image 16:9, Preview 9:16 (portrait)
        val imageWidth = 1920
        val imageHeight = 1080
        val previewWidth = 1080f
        val previewHeight = 1920f

        // Act - Calculate transform
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val previewAspect = previewWidth / previewHeight

        // Assert - Image is wider
        assertThat(imageAspect).isGreaterThan(previewAspect)

        // Verify offset calculation
        val scaleX = previewWidth / imageWidth
        val scaleY = scaleX // Same scale to maintain aspect ratio
        val offsetY = (previewHeight - imageHeight * scaleY) / 2f

        assertThat(offsetY).isGreaterThan(0f)
    }

    @Test
    fun whenImageIsTallerThanPreview_thenHorizontalOffsetApplied() {
        // Arrange - Image 9:16 (portrait), Preview 16:9 (landscape)
        val imageWidth = 1080
        val imageHeight = 1920
        val previewWidth = 1920f
        val previewHeight = 1080f

        // Act - Calculate transform
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val previewAspect = previewWidth / previewHeight

        // Assert - Image is taller
        assertThat(imageAspect).isLessThan(previewAspect)

        // Verify offset calculation
        val scaleY = previewHeight / imageHeight
        val scaleX = scaleY // Same scale to maintain aspect ratio
        val offsetX = (previewWidth - imageWidth * scaleX) / 2f

        assertThat(offsetX).isGreaterThan(0f)
    }

    @Test
    fun whenBoxIsCentered_thenCenterPointIsCalculatedCorrectly() {
        // Arrange - Box in center of 640x480 image
        val box = Rect(220, 140, 420, 340)

        // Act - Calculate center (as DetectionOverlay does)
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Center is at midpoint
        assertThat(centerX).isEqualTo(320f)
        assertThat(centerY).isEqualTo(240f)
    }

    @Test
    fun whenBoxIsAtOrigin_thenCenterPointIsCorrect() {
        // Arrange - Small box at top-left corner
        val box = Rect(0, 0, 100, 100)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Center is at 50,50
        assertThat(centerX).isEqualTo(50f)
        assertThat(centerY).isEqualTo(50f)
    }

    @Test
    fun whenBoxIsFullScreen_thenCenterIsScreenCenter() {
        // Arrange - Box covering entire image
        val imageWidth = 640
        val imageHeight = 480
        val box = Rect(0, 0, imageWidth, imageHeight)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Center is at image center
        assertThat(centerX).isEqualTo(imageWidth / 2f)
        assertThat(centerY).isEqualTo(imageHeight / 2f)
    }

    @Test
    fun whenBoxIsSmall_thenCenterIsStillAccurate() {
        // Arrange - Very small 10x10 box
        val box = Rect(100, 100, 110, 110)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Center is accurate to sub-pixel
        assertThat(centerX).isEqualTo(105f)
        assertThat(centerY).isEqualTo(105f)
    }

    @Test
    fun whenBoxIsTall_thenCenterReflectsShape() {
        // Arrange - Tall narrow box (person)
        val box = Rect(200, 50, 300, 400)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert
        assertThat(centerX).isEqualTo(250f) // Horizontal center
        assertThat(centerY).isEqualTo(225f) // Vertical center
    }

    @Test
    fun whenBoxIsWide_thenCenterReflectsShape() {
        // Arrange - Wide short box (table)
        val box = Rect(50, 200, 400, 300)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert
        assertThat(centerX).isEqualTo(225f) // Horizontal center
        assertThat(centerY).isEqualTo(250f) // Vertical center
    }

    @Test
    fun whenScaleIsApplied_thenCenterScalesProportionally() {
        // Arrange
        val box = Rect(100, 100, 300, 300)
        val scale = 0.5f // 50% scale

        // Act - Transform box
        val transformedLeft = box.left * scale
        val transformedTop = box.top * scale
        val transformedRight = box.right * scale
        val transformedBottom = box.bottom * scale

        // Calculate center of transformed box
        val centerX = transformedLeft + (transformedRight - transformedLeft) / 2f
        val centerY = transformedTop + (transformedBottom - transformedTop) / 2f

        // Assert - Center scales proportionally
        assertThat(centerX).isEqualTo(100f) // Original 200 * 0.5
        assertThat(centerY).isEqualTo(100f) // Original 200 * 0.5
    }

    @Test
    fun whenOffsetIsApplied_thenCenterShiftsCorrectly() {
        // Arrange
        val box = Rect(100, 100, 300, 300)
        val offsetX = 50f
        val offsetY = 25f

        // Act - Transform box with offset
        val transformedLeft = box.left.toFloat() + offsetX
        val transformedTop = box.top.toFloat() + offsetY
        val transformedRight = box.right.toFloat() + offsetX
        val transformedBottom = box.bottom.toFloat() + offsetY

        // Calculate center
        val centerX = transformedLeft + (transformedRight - transformedLeft) / 2f
        val centerY = transformedTop + (transformedBottom - transformedTop) / 2f

        // Assert - Center shifts by offset amount
        assertThat(centerX).isEqualTo(250f) // Original 200 + 50
        assertThat(centerY).isEqualTo(225f) // Original 200 + 25
    }

    @Test
    fun whenMultipleBoxesTransformed_thenRelativePositionsPreserved() {
        // Arrange - Two boxes with known relationship
        val box1 = Rect(100, 100, 200, 200)
        val box2 = Rect(300, 300, 400, 400)
        val scale = 0.5f

        // Act - Calculate centers
        val center1X = (box1.left + box1.right) / 2f * scale
        val center1Y = (box1.top + box1.bottom) / 2f * scale
        val center2X = (box2.left + box2.right) / 2f * scale
        val center2Y = (box2.top + box2.bottom) / 2f * scale

        val originalDistanceX = (box2.left + box2.right) / 2f - (box1.left + box1.right) / 2f
        val transformedDistanceX = center2X - center1X

        // Assert - Distance scales proportionally
        assertThat(transformedDistanceX).isEqualTo(originalDistanceX * scale)
    }

    @Test
    fun whenBoxAtEdge_thenCenterStillWithinBounds() {
        // Arrange - Box touching right and bottom edges
        val imageWidth = 640
        val imageHeight = 480
        val box = Rect(540, 380, 640, 480)

        // Act - Calculate center
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Center is within image bounds
        assertThat(centerX).isAtMost(imageWidth.toFloat())
        assertThat(centerY).isAtMost(imageHeight.toFloat())
        assertThat(centerX).isEqualTo(590f)
        assertThat(centerY).isEqualTo(430f)
    }

    @Test
    fun whenBoxHasOddDimensions_thenCenterIsAccurate() {
        // Arrange - Box with odd width and height
        val box = Rect(100, 100, 151, 181)

        // Act - Calculate center (should handle fractional centers)
        val centerX = box.left + (box.right - box.left) / 2f
        val centerY = box.top + (box.bottom - box.top) / 2f

        // Assert - Fractional center positions
        assertThat(centerX).isEqualTo(125.5f)
        assertThat(centerY).isEqualTo(140.5f)
    }

    @Test
    fun whenIdentityTransform_thenBoxUnchanged() {
        // Arrange - 1:1 scale, no offset
        val box = Rect(100, 200, 300, 400)
        val scale = 1f
        val offset = 0f

        // Act - Apply identity transform
        val transformedLeft = box.left * scale + offset
        val transformedTop = box.top * scale + offset
        val transformedRight = box.right * scale + offset
        val transformedBottom = box.bottom * scale + offset

        // Assert - Box unchanged
        assertThat(transformedLeft).isEqualTo(box.left.toFloat())
        assertThat(transformedTop).isEqualTo(box.top.toFloat())
        assertThat(transformedRight).isEqualTo(box.right.toFloat())
        assertThat(transformedBottom).isEqualTo(box.bottom.toFloat())
    }
}
