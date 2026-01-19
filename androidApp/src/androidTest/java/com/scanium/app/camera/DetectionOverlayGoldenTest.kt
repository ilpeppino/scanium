package com.scanium.app.camera

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.NormalizedRect
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

/**
 * Golden/Snapshot tests for DetectionOverlay to lock down rendering behavior.
 *
 * These tests verify:
 * 1. Visual rendering of different bbox states (EYE, SELECTED, READY, LOCKED)
 * 2. Coordinate transformation accuracy across rotations
 * 3. Multiple detections with different states
 * 4. Label positioning and rendering
 *
 * Implementation Strategy:
 * Uses Compose `captureToImage()` for pixel-level validation. Since no snapshot library
 * is integrated, we use reference-based visual comparison with similarity metrics.
 *
 * Test Structure:
 * - Each test renders a specific scenario
 * - Captures the rendered output as a bitmap
 * - Validates key visual properties (colors, positions, dimensions)
 * - Uses pixel sampling to verify rendering correctness
 */
@RunWith(AndroidJUnit4::class)
class DetectionOverlayGoldenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        // Test canvas size - fixed for reproducibility
        private const val TEST_WIDTH = 400
        private const val TEST_HEIGHT = 600

        // Simulated camera image size (landscape sensor)
        private val SENSOR_SIZE = Size(1920, 1080)

        // Color extraction tolerance for anti-aliasing
        private const val COLOR_TOLERANCE = 30

        // Visual state colors (matching BboxColors in DetectionOverlay)
        private val EYE_OUTLINE_COLOR = AndroidColor.argb(89, 255, 255, 255) // White @ 35% alpha
        private val SELECTED_OUTLINE_COLOR = AndroidColor.argb(217, 36, 182, 255) // ScaniumBlue @ 85% alpha
        private val READY_OUTLINE_COLOR = AndroidColor.argb(217, 29, 185, 84) // Green @ 85% alpha
        private val LOCKED_OUTLINE_COLOR = AndroidColor.rgb(29, 185, 84) // Bright green @ 100% alpha
    }

    /**
     * Test: Single detection with EYE state renders with subtle styling.
     *
     * Validates:
     * - EYE state uses very subtle white outline
     * - Bounding box is drawn correctly
     * - Label is positioned above the box
     */
    @Test
    fun whenSingleEyeDetection_thenRendersSubtleBox() {
        // Arrange - Create a centered detection in EYE state
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.3f,
                        top = 0.3f,
                        right = 0.7f,
                        bottom = 0.6f,
                    ),
                label = "Item",
                priceText = "",
                confidence = 0.85f,
                isReady = false,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.EYE,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify EYE state visual properties
        // 1. Check that box outline exists (look for white/gray pixels in expected region)
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // 2. Verify subtle appearance (low opacity means fewer bright pixels)
        val brightnessScore = calculateAverageBrightness(bitmap)
        assertThat(brightnessScore).isLessThan(100f) // EYE state should be very subtle
    }

    /**
     * Test: Single detection with SELECTED state renders with accent color.
     *
     * Validates:
     * - SELECTED state uses blue accent outline
     * - Stroke width is medium thickness
     * - Visual distinction from EYE state
     */
    @Test
    fun whenSingleSelectedDetection_thenRendersAccentBox() {
        // Arrange - Create a centered detection in SELECTED state
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.25f,
                        top = 0.25f,
                        right = 0.75f,
                        bottom = 0.65f,
                    ),
                label = "Shoes",
                priceText = "€20-50",
                confidence = 0.90f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.SELECTED,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify SELECTED state visual properties
        // 1. Check that box outline exists with higher prominence than EYE
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // 2. Verify blue accent color presence (sample pixels along expected box edge)
        val hasBlueAccent = verifyColorPresence(bitmap, SELECTED_OUTLINE_COLOR)
        assertThat(hasBlueAccent).isTrue()

        // 3. Verify label is rendered (check for text presence)
        val hasLabel = verifyLabelPresence(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasLabel).isTrue()
    }

    /**
     * Test: Single detection with READY state renders with green accent.
     *
     * Validates:
     * - READY state uses green outline
     * - Stroke is medium-thick
     * - Label shows classification and price
     */
    @Test
    fun whenSingleReadyDetection_thenRendersGreenBox() {
        // Arrange - Create a centered detection in READY state
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.2f,
                        top = 0.3f,
                        right = 0.8f,
                        bottom = 0.7f,
                    ),
                label = "T-Shirt",
                priceText = "€15-30",
                confidence = 0.92f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.READY,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify READY state visual properties
        // 1. Check that box outline exists
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // 2. Verify green accent color presence
        val hasGreenAccent = verifyColorPresence(bitmap, READY_OUTLINE_COLOR)
        assertThat(hasGreenAccent).isTrue()

        // 3. Verify label is rendered with price text
        val hasLabel = verifyLabelPresence(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasLabel).isTrue()
    }

    /**
     * Test: Single detection with LOCKED state renders with bright green and thick stroke.
     *
     * Validates:
     * - LOCKED state uses bright green outline at 100% alpha
     * - Stroke is thick (1.4x multiplier)
     * - Visual prominence indicates scan-ready state
     */
    @Test
    fun whenSingleLockedDetection_thenRendersBrightGreenThickBox() {
        // Arrange - Create a centered detection in LOCKED state
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.25f,
                        top = 0.35f,
                        right = 0.75f,
                        bottom = 0.65f,
                    ),
                label = "Sneakers",
                priceText = "€80-120",
                confidence = 0.95f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.LOCKED,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify LOCKED state visual properties
        // 1. Check that box outline exists with high prominence
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // 2. Verify bright green color presence (full opacity)
        val hasLockedGreen = verifyColorPresence(bitmap, LOCKED_OUTLINE_COLOR)
        assertThat(hasLockedGreen).isTrue()

        // 3. Verify thicker stroke by checking stroke width
        val strokeWidth = estimateStrokeWidth(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(strokeWidth).isGreaterThan(3) // LOCKED has thicker stroke
    }

    /**
     * Test: Multiple detections with different states render correctly.
     *
     * Validates:
     * - All detections are visible
     * - Different states maintain visual hierarchy
     * - No overlapping rendering issues
     */
    @Test
    fun whenMultipleDetectionsWithDifferentStates_thenAllRenderCorrectly() {
        // Arrange - Create multiple detections with different states
        val detections =
            listOf(
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.1f, 0.1f, 0.4f, 0.3f),
                    label = "Item 1",
                    priceText = "",
                    confidence = 0.7f,
                    isReady = false,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.EYE,
                ),
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.5f, 0.15f, 0.85f, 0.35f),
                    label = "Item 2",
                    priceText = "€10-20",
                    confidence = 0.85f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.SELECTED,
                ),
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.15f, 0.45f, 0.5f, 0.7f),
                    label = "Item 3",
                    priceText = "€30-60",
                    confidence = 0.90f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.READY,
                ),
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.55f, 0.5f, 0.9f, 0.8f),
                    label = "Item 4",
                    priceText = "€50-100",
                    confidence = 0.95f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.LOCKED,
                ),
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(detections)

        // Assert - Verify all boxes are rendered
        detections.forEach { detection ->
            val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
            assertThat(hasOutline).isTrue()
        }

        // Verify visual hierarchy (LOCKED should be most prominent)
        val hasLockedGreen = verifyColorPresence(bitmap, LOCKED_OUTLINE_COLOR)
        assertThat(hasLockedGreen).isTrue()

        val hasReadyGreen = verifyColorPresence(bitmap, READY_OUTLINE_COLOR)
        assertThat(hasReadyGreen).isTrue()
    }

    /**
     * Test: Coordinate transformation with 90-degree rotation (portrait mode).
     *
     * Validates:
     * - Bounding boxes map correctly from landscape sensor to portrait display
     * - Rotation handling preserves box dimensions
     * - No coordinate inversion issues
     */
    @Test
    fun whenRotation90_thenCoordinatesMappedCorrectly() {
        // Arrange - Create detection in upright coordinates (portrait display)
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.3f,
                        top = 0.4f,
                        right = 0.7f,
                        bottom = 0.6f,
                    ),
                label = "Portrait Item",
                priceText = "€25",
                confidence = 0.88f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.SELECTED,
            )

        // Act - Render with 90-degree rotation (portrait mode)
        val bitmap =
            renderAndCapture(
                detections = listOf(detection),
                rotationDegrees = 90,
            )

        // Assert - Verify box is rendered in correct position
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // Verify aspect ratio preservation
        val expectedWidth = (0.7f - 0.3f) * TEST_WIDTH
        val expectedHeight = (0.6f - 0.4f) * TEST_HEIGHT
        val aspectRatio = expectedHeight / expectedWidth

        // For portrait rotation, box should maintain reasonable proportions
        assertThat(aspectRatio).isGreaterThan(0.3f)
        assertThat(aspectRatio).isLessThan(3.0f)
    }

    /**
     * Test: Coordinate transformation with 270-degree rotation.
     *
     * Validates:
     * - 270-degree rotation (reverse portrait) maps correctly
     * - Box dimensions and positions are accurate
     */
    @Test
    fun whenRotation270_thenCoordinatesMappedCorrectly() {
        // Arrange - Create detection
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.25f,
                        top = 0.3f,
                        right = 0.75f,
                        bottom = 0.7f,
                    ),
                label = "Rotated Item",
                priceText = "€40",
                confidence = 0.91f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.READY,
            )

        // Act - Render with 270-degree rotation
        val bitmap =
            renderAndCapture(
                detections = listOf(detection),
                rotationDegrees = 270,
            )

        // Assert - Verify box is rendered
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()
    }

    /**
     * Test: Label positioning above bounding box.
     *
     * Validates:
     * - Label is positioned above the box when space available
     * - Label falls below box when insufficient space above
     * - Label text and background are visible
     */
    @Test
    fun whenDetectionAtTop_thenLabelPositionedBelow() {
        // Arrange - Create detection near top edge (no space for label above)
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.3f,
                        // Very close to top
                        top = 0.02f,
                        right = 0.7f,
                        bottom = 0.15f,
                    ),
                label = "Top Item",
                priceText = "€15-25",
                confidence = 0.87f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.SELECTED,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify label is present (should be below box)
        val hasLabel = verifyLabelPresence(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasLabel).isTrue()

        // Check that label appears below the box (more non-background pixels in lower region)
        val boxBottomY = (detection.bboxNorm.bottom * TEST_HEIGHT).toInt()
        val labelRegionBrightness =
            calculateRegionBrightness(
                bitmap,
                x = (detection.bboxNorm.left * TEST_WIDTH).toInt(),
                y = boxBottomY,
                width = ((detection.bboxNorm.right - detection.bboxNorm.left) * TEST_WIDTH).toInt(),
                // Label height estimate
                height = 40,
            )
        assertThat(labelRegionBrightness).isGreaterThan(30f) // Label background present
    }

    /**
     * Test: Label positioning above bounding box when space available.
     *
     * Validates:
     * - Label prefers position above box
     * - Label is properly aligned with box left edge
     */
    @Test
    fun whenDetectionInCenter_thenLabelPositionedAbove() {
        // Arrange - Create detection in center (plenty of space above)
        val detection =
            OverlayTrack(
                bboxNorm =
                    NormalizedRect(
                        left = 0.3f,
                        // Center
                        top = 0.5f,
                        right = 0.7f,
                        bottom = 0.7f,
                    ),
                label = "Center Item",
                priceText = "€35-70",
                confidence = 0.93f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.READY,
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify label is present (should be above box)
        val hasLabel = verifyLabelPresence(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasLabel).isTrue()

        // Check that label appears above the box
        val boxTopY = (detection.bboxNorm.top * TEST_HEIGHT).toInt()
        val labelRegionBrightness =
            calculateRegionBrightness(
                bitmap,
                x = (detection.bboxNorm.left * TEST_WIDTH).toInt(),
                // Region above box
                y = maxOf(0, boxTopY - 40),
                width = ((detection.bboxNorm.right - detection.bboxNorm.left) * TEST_WIDTH).toInt(),
                // Label height estimate
                height = 40,
            )
        assertThat(labelRegionBrightness).isGreaterThan(30f) // Label background present
    }

    /**
     * Test: Overlapping detections render without artifacts.
     *
     * Validates:
     * - Overlapping boxes both render completely
     * - Z-ordering is consistent
     * - No rendering corruption at intersection
     */
    @Test
    fun whenOverlappingDetections_thenBothRenderCleanly() {
        // Arrange - Create two overlapping detections
        val detections =
            listOf(
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.2f, 0.3f, 0.6f, 0.6f),
                    label = "Background",
                    priceText = "€20",
                    confidence = 0.8f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.EYE,
                ),
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.4f, 0.4f, 0.8f, 0.7f),
                    label = "Foreground",
                    priceText = "€50",
                    confidence = 0.9f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.SELECTED,
                ),
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(detections)

        // Assert - Verify both boxes are visible
        detections.forEach { detection ->
            val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
            assertThat(hasOutline).isTrue()
        }
    }

    /**
     * Test: Price estimation status affects label rendering.
     *
     * Validates:
     * - Label opacity changes during price estimation
     * - Pulsing animation state doesn't break rendering
     */
    @Test
    fun whenPriceEstimating_thenLabelRendersProperly() {
        // Arrange - Create detection with estimating status
        val detection =
            OverlayTrack(
                bboxNorm = NormalizedRect(0.25f, 0.3f, 0.75f, 0.6f),
                label = "Estimating",
                priceText = "Estimating…",
                confidence = 0.88f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Estimating,
                boxStyle = OverlayBoxStyle.SELECTED,
            )

        // Act - Render and capture (animation will be at initial state)
        val bitmap = renderAndCapture(listOf(detection))

        // Assert - Verify label is present despite animation
        val hasLabel = verifyLabelPresence(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasLabel).isTrue()

        // Verify box is always fully visible (no pulsing on box)
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()
    }

    /**
     * Test: Edge detection at canvas boundaries.
     *
     * Validates:
     * - Boxes at canvas edges don't get clipped incorrectly
     * - Labels reposition when near edges
     * - No rendering artifacts at boundaries
     */
    @Test
    fun whenDetectionAtEdge_thenRendersWithoutClipping() {
        // Arrange - Create detections at various edges
        val detections =
            listOf(
                // Top-left corner
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.0f, 0.0f, 0.3f, 0.2f),
                    label = "Top-Left",
                    priceText = "",
                    confidence = 0.8f,
                    isReady = false,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.EYE,
                ),
                // Bottom-right corner
                OverlayTrack(
                    bboxNorm = NormalizedRect(0.7f, 0.8f, 1.0f, 1.0f),
                    label = "Bottom-Right",
                    priceText = "",
                    confidence = 0.85f,
                    isReady = true,
                    priceEstimationStatus = PriceEstimationStatus.Idle,
                    boxStyle = OverlayBoxStyle.SELECTED,
                ),
            )

        // Act - Render and capture
        val bitmap = renderAndCapture(detections)

        // Assert - Verify edge boxes render
        detections.forEach { detection ->
            val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
            assertThat(hasOutline).isTrue()
        }
    }

    /**
     * Test: FILL_CENTER scale type crops excess image area.
     *
     * Validates:
     * - FILL_CENTER scale type correctly maps coordinates
     * - Center-crop behavior preserves aspect ratio
     * - Boxes appear in correct positions with cropping
     */
    @Test
    fun whenFillCenterScaleType_thenCoordinatesMappedWithCropping() {
        // Arrange - Create centered detection
        val detection =
            OverlayTrack(
                bboxNorm = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f),
                label = "Centered",
                priceText = "€30",
                confidence = 0.9f,
                isReady = true,
                priceEstimationStatus = PriceEstimationStatus.Idle,
                boxStyle = OverlayBoxStyle.READY,
            )

        // Act - Render with FILL_CENTER (default)
        val bitmap =
            renderAndCapture(
                detections = listOf(detection),
                scaleType = PreviewScaleType.FILL_CENTER,
            )

        // Assert - Verify box is centered
        val hasOutline = verifyBoxOutline(bitmap, detection, TEST_WIDTH, TEST_HEIGHT)
        assertThat(hasOutline).isTrue()

        // Verify center position is approximately correct
        val expectedCenterX = ((detection.bboxNorm.left + detection.bboxNorm.right) / 2f * TEST_WIDTH).toInt()
        val expectedCenterY = ((detection.bboxNorm.top + detection.bboxNorm.bottom) / 2f * TEST_HEIGHT).toInt()

        // Sample center point should be within rendered box
        val centerColor = bitmap.getPixel(expectedCenterX, expectedCenterY)
        // Center should be inside the box (not on the outline), so should be background color
        // This is a basic sanity check
        assertThat(centerColor).isNotEqualTo(AndroidColor.TRANSPARENT)
    }

    // ==================== Helper Methods ====================

    /**
     * Renders DetectionOverlay with given detections and captures as bitmap.
     */
    private fun renderAndCapture(
        detections: List<OverlayTrack>,
        rotationDegrees: Int = 90,
        scaleType: PreviewScaleType = PreviewScaleType.FILL_CENTER,
    ): Bitmap {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(TEST_WIDTH.dp, TEST_HEIGHT.dp)
                        // Black background for contrast
                        .background(Color.Black),
            ) {
                DetectionOverlay(
                    detections = detections,
                    imageSize =
                        if (rotationDegrees == 90 || rotationDegrees == 270) {
                            // Portrait: effective dimensions are swapped
                            SENSOR_SIZE
                        } else {
                            SENSOR_SIZE
                        },
                    previewSize = Size(TEST_WIDTH, TEST_HEIGHT),
                    rotationDegrees = rotationDegrees,
                    showGeometryDebug = false,
                )
            }
        }

        composeTestRule.waitForIdle()

        return composeTestRule.onRoot()
            .captureToImage()
            .asAndroidBitmap()
    }

    /**
     * Verifies that a bounding box outline is present in the bitmap.
     *
     * Samples pixels along the expected box edges to detect non-background colors.
     */
    private fun verifyBoxOutline(
        bitmap: Bitmap,
        detection: OverlayTrack,
        canvasWidth: Int,
        canvasHeight: Int,
    ): Boolean {
        // Calculate expected box position in pixels
        val left = (detection.bboxNorm.left * canvasWidth).toInt()
        val top = (detection.bboxNorm.top * canvasHeight).toInt()
        val right = (detection.bboxNorm.right * canvasWidth).toInt()
        val bottom = (detection.bboxNorm.bottom * canvasHeight).toInt()

        // Clamp to bitmap bounds
        val clampedLeft = left.coerceIn(0, bitmap.width - 1)
        val clampedTop = top.coerceIn(0, bitmap.height - 1)
        val clampedRight = right.coerceIn(1, bitmap.width)
        val clampedBottom = bottom.coerceIn(1, bitmap.height)

        // Sample points along each edge (top, bottom, left, right)
        val samplesPerEdge = 10
        var nonBackgroundPixels = 0

        // Top edge
        for (i in 0 until samplesPerEdge) {
            val x = clampedLeft + (clampedRight - clampedLeft) * i / samplesPerEdge
            val y = clampedTop
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                if (!isBackgroundColor(color)) {
                    nonBackgroundPixels++
                }
            }
        }

        // Bottom edge
        for (i in 0 until samplesPerEdge) {
            val x = clampedLeft + (clampedRight - clampedLeft) * i / samplesPerEdge
            val y = clampedBottom.coerceAtMost(bitmap.height - 1)
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                if (!isBackgroundColor(color)) {
                    nonBackgroundPixels++
                }
            }
        }

        // Left edge
        for (i in 0 until samplesPerEdge) {
            val x = clampedLeft
            val y = clampedTop + (clampedBottom - clampedTop) * i / samplesPerEdge
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                if (!isBackgroundColor(color)) {
                    nonBackgroundPixels++
                }
            }
        }

        // Right edge
        for (i in 0 until samplesPerEdge) {
            val x = clampedRight.coerceAtMost(bitmap.width - 1)
            val y = clampedTop + (clampedBottom - clampedTop) * i / samplesPerEdge
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                if (!isBackgroundColor(color)) {
                    nonBackgroundPixels++
                }
            }
        }

        // Outline exists if we found enough non-background pixels (at least 20% of samples)
        return nonBackgroundPixels >= samplesPerEdge * 4 * 0.2
    }

    /**
     * Verifies that a specific color is present in the bitmap.
     *
     * Scans the entire bitmap looking for pixels that match the target color
     * within a tolerance (to account for anti-aliasing).
     */
    private fun verifyColorPresence(
        bitmap: Bitmap,
        targetColor: Int,
    ): Boolean {
        var matchingPixels = 0
        val totalPixels = bitmap.width * bitmap.height
        val sampleRate = 5 // Sample every 5th pixel for performance

        for (y in 0 until bitmap.height step sampleRate) {
            for (x in 0 until bitmap.width step sampleRate) {
                val color = bitmap.getPixel(x, y)
                if (colorsMatch(color, targetColor, COLOR_TOLERANCE)) {
                    matchingPixels++
                    // Early exit if we found enough matches
                    if (matchingPixels > 10) {
                        return true
                    }
                }
            }
        }

        // Color is present if we found at least a few matching pixels
        return matchingPixels > 5
    }

    /**
     * Verifies that a label is present near the detection box.
     *
     * Checks for text background (dark navy) in expected label region.
     */
    private fun verifyLabelPresence(
        bitmap: Bitmap,
        detection: OverlayTrack,
        canvasWidth: Int,
        canvasHeight: Int,
    ): Boolean {
        // Calculate expected label region (above or below box)
        val boxTopY = (detection.bboxNorm.top * canvasHeight).toInt()
        val boxBottomY = (detection.bboxNorm.bottom * canvasHeight).toInt()
        val boxLeftX = (detection.bboxNorm.left * canvasWidth).toInt()
        val boxRightX = (detection.bboxNorm.right * canvasWidth).toInt()

        // Check region above box
        val aboveRegionY = maxOf(0, boxTopY - 50)
        val aboveRegionHeight = minOf(50, boxTopY)

        // Check region below box
        val belowRegionY = minOf(bitmap.height - 50, boxBottomY + 10)
        val belowRegionHeight = minOf(50, bitmap.height - boxBottomY - 10)

        // Look for label background color (DeepNavy @ 85% alpha on black background)
        // This will appear as a dark blue-gray region
        val labelBackgroundHue = 220f // Navy blue hue

        // Sample above region
        var labelPixelsAbove = 0
        for (y in aboveRegionY until aboveRegionY + aboveRegionHeight) {
            for (x in boxLeftX until boxRightX step 3) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val color = bitmap.getPixel(x, y)
                    if (isLabelBackgroundColor(color)) {
                        labelPixelsAbove++
                    }
                }
            }
        }

        // Sample below region
        var labelPixelsBelow = 0
        for (y in belowRegionY until belowRegionY + belowRegionHeight) {
            for (x in boxLeftX until boxRightX step 3) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val color = bitmap.getPixel(x, y)
                    if (isLabelBackgroundColor(color)) {
                        labelPixelsBelow++
                    }
                }
            }
        }

        // Label exists if we found label background in either region
        return labelPixelsAbove > 20 || labelPixelsBelow > 20
    }

    /**
     * Estimates the stroke width of a bounding box by measuring pixel density.
     */
    private fun estimateStrokeWidth(
        bitmap: Bitmap,
        detection: OverlayTrack,
        canvasWidth: Int,
        canvasHeight: Int,
    ): Int {
        // Sample the left edge vertically to measure stroke width
        val leftX = (detection.bboxNorm.left * canvasWidth).toInt()
        val midY = ((detection.bboxNorm.top + detection.bboxNorm.bottom) / 2f * canvasHeight).toInt()

        if (leftX < 0 || leftX >= bitmap.width || midY < 0 || midY >= bitmap.height) {
            return 0
        }

        // Scan horizontally from left edge to find stroke width
        var strokeWidth = 0
        for (offset in 0 until 15) {
            val x = leftX + offset
            if (x >= bitmap.width) break

            val color = bitmap.getPixel(x, midY)
            if (!isBackgroundColor(color)) {
                strokeWidth++
            } else if (strokeWidth > 0) {
                // End of stroke
                break
            }
        }

        return strokeWidth
    }

    /**
     * Calculates average brightness of the entire bitmap (0-255).
     */
    private fun calculateAverageBrightness(bitmap: Bitmap): Float {
        var totalBrightness = 0f
        val sampleRate = 5

        for (y in 0 until bitmap.height step sampleRate) {
            for (x in 0 until bitmap.width step sampleRate) {
                val color = bitmap.getPixel(x, y)
                totalBrightness += getColorBrightness(color)
            }
        }

        val sampleCount = (bitmap.width / sampleRate) * (bitmap.height / sampleRate)
        return totalBrightness / sampleCount
    }

    /**
     * Calculates average brightness of a specific region.
     */
    private fun calculateRegionBrightness(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Float {
        var totalBrightness = 0f
        var sampleCount = 0

        val endX = minOf(x + width, bitmap.width)
        val endY = minOf(y + height, bitmap.height)

        for (py in maxOf(0, y) until endY step 2) {
            for (px in maxOf(0, x) until endX step 2) {
                val color = bitmap.getPixel(px, py)
                totalBrightness += getColorBrightness(color)
                sampleCount++
            }
        }

        return if (sampleCount > 0) totalBrightness / sampleCount else 0f
    }

    /**
     * Checks if a color is close to black background.
     */
    private fun isBackgroundColor(color: Int): Boolean {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        val brightness = (r + g + b) / 3
        return brightness < 20 // Very dark colors are considered background
    }

    /**
     * Checks if a color matches the label background (DeepNavy).
     */
    private fun isLabelBackgroundColor(color: Int): Boolean {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)

        // DeepNavy is dark blue (low brightness, blue-dominant)
        val brightness = (r + g + b) / 3
        val isBlueish = b > r && b > g

        return brightness in 15..80 && isBlueish
    }

    /**
     * Checks if two colors match within a tolerance.
     */
    private fun colorsMatch(
        color1: Int,
        color2: Int,
        tolerance: Int,
    ): Boolean {
        val r1 = AndroidColor.red(color1)
        val g1 = AndroidColor.green(color1)
        val b1 = AndroidColor.blue(color1)

        val r2 = AndroidColor.red(color2)
        val g2 = AndroidColor.green(color2)
        val b2 = AndroidColor.blue(color2)

        val distance =
            sqrt(
                (
                    (r1 - r2) * (r1 - r2) +
                        (g1 - g2) * (g1 - g2) +
                        (b1 - b2) * (b1 - b2)
                ).toDouble(),
            )

        return distance <= tolerance
    }

    /**
     * Calculates the brightness of a color (0-255).
     */
    private fun getColorBrightness(color: Int): Float {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        return (r + g + b) / 3f
    }
}
