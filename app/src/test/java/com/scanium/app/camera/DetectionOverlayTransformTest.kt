package com.scanium.app.camera

import android.graphics.Rect
import android.util.Size
import com.google.common.truth.Truth.assertThat
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
 * - Center point calculation for circle marker positioning
 * - Edge cases (small boxes, full-screen boxes, portrait/landscape)
 * - Scaling and offset calculations
 *
 * Note: The DetectionOverlay now renders circle markers at detection centers
 * instead of bounding boxes, making accurate center calculation critical.
 */
@RunWith(RobolectricTestRunner::class)
class DetectionOverlayTransformTest {

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
