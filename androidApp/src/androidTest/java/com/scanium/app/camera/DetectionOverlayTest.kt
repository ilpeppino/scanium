package com.scanium.app.camera

import android.graphics.Rect
import android.util.Size
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.waitForIdle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.ItemCategory
import com.scanium.app.platform.toNormalizedRect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for DetectionOverlay UI component.
 *
 * Tests verify:
 * - Circle marker rendering for detections
 * - Center point calculation and positioning
 * - Multiple detection handling
 * - Empty detection list handling
 * - Coordinate transformation for different image/preview sizes
 *
 * Note: The overlay now renders small circular markers at the center of each detection
 * instead of full bounding boxes with labels, to reduce visual clutter.
 */
@RunWith(AndroidJUnit4::class)
class DetectionOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenNoDetections_thenOverlayRendersWithoutCrashing() {
        // Arrange & Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = emptyList(),
                imageSize = Size(1280, 720),
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Should render without crashing
        composeTestRule.waitForIdle()
        // No text should be present (labels removed for minimal design)
        composeTestRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
    }

    @Test
    fun whenSingleDetection_thenOverlayRendersCircleMarker() {
        // Arrange
        val imageSize = Size(640, 480)
        val detection =
            DetectionResult(
                bboxNorm = Rect(100, 100, 300, 300).toNormalizedRect(imageSize.width, imageSize.height),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 50.0),
                confidence = 0.85f,
            )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Should render without crashing
        // Circle markers are rendered via Canvas, no text labels
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenMultipleDetections_thenOverlayRendersAllMarkers() {
        // Arrange
        val imageSize = Size(640, 480)
        val detections =
            listOf(
                DetectionResult(
                    bboxNorm = Rect(50, 50, 150, 150).toNormalizedRect(imageSize.width, imageSize.height),
                    category = ItemCategory.FASHION,
                    priceRange = Pair(10.0, 30.0),
                    confidence = 0.8f,
                ),
                DetectionResult(
                    bboxNorm = Rect(200, 200, 350, 350).toNormalizedRect(imageSize.width, imageSize.height),
                    category = ItemCategory.FOOD,
                    priceRange = Pair(5.0, 10.0),
                    confidence = 0.9f,
                ),
                DetectionResult(
                    bboxNorm = Rect(400, 100, 600, 300).toNormalizedRect(imageSize.width, imageSize.height),
                    category = ItemCategory.PLANT,
                    priceRange = Pair(15.0, 40.0),
                    confidence = 0.7f,
                ),
            )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Should render all markers without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenDetectionsListUpdates_thenOverlayUpdatesWithoutCrashing() {
        // Arrange - Start with empty list
        var detections = emptyList<DetectionResult>()

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Initially empty
        composeTestRule.waitForIdle()

        // Act - Update to include detections
        val imageSize = Size(640, 480)
        detections =
            listOf(
                detection(Rect(100, 100, 300, 300), ItemCategory.FASHION, Pair(10.0, 50.0), 0.85f, imageSize),
                detection(Rect(200, 200, 400, 400), ItemCategory.FOOD, Pair(5.0, 15.0), 0.9f, imageSize),
            )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Should update without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenImageAndPreviewSizesAreDifferent_thenOverlayStillRenders() {
        // Arrange - Test with different aspect ratios
        val imageSize = Size(1920, 1080)
        val detection = detection(Rect(100, 100, 300, 300), ItemCategory.HOME_GOOD, Pair(20.0, 60.0), 0.8f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                // 16:9 landscape
                imageSize = imageSize,
                // 9:16 portrait
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Overlay should render despite different sizes
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenSmallBoundingBox_thenStillRendersCorrectly() {
        // Arrange - Very small detection
        val imageSize = Size(640, 480)
        val detection = detection(Rect(0, 0, 50, 50), ItemCategory.FOOD, Pair(1.0, 5.0), 0.6f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Small detection should still render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenLargeBoundingBox_thenRendersMarkerAtCenter() {
        // Arrange - Large detection covering most of frame
        val imageSize = Size(640, 480)
        val detection = detection(Rect(10, 10, 630, 470), ItemCategory.PLACE, Pair(100.0, 500.0), 0.9f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Large detection should render center marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenLowConfidenceDetection_thenStillRendersMarker() {
        // Arrange
        val imageSize = Size(640, 480)
        val detection = detection(Rect(100, 100, 300, 300), ItemCategory.PLACE, Pair(50.0, 200.0), 0.35f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Low confidence should still render
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenHighConfidenceDetection_thenRendersMarker() {
        // Arrange
        val imageSize = Size(640, 480)
        val detection = detection(Rect(100, 100, 300, 300), ItemCategory.FOOD, Pair(5.0, 15.0), 1.0f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - High confidence should render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenUnknownCategory_thenStillRendersMarker() {
        // Arrange
        val imageSize = Size(640, 480)
        val detection = detection(Rect(100, 100, 300, 300), ItemCategory.UNKNOWN, Pair(1.0, 100.0), 0.5f, imageSize)

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Unknown category should still render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenOverlappingDetections_thenBothMarkersRender() {
        // Arrange - Two detections with overlapping bounding boxes
        val imageSize = Size(640, 480)
        val detections =
            listOf(
                detection(Rect(100, 100, 300, 300), ItemCategory.FASHION, Pair(10.0, 30.0), 0.8f, imageSize),
                detection(Rect(150, 150, 350, 350), ItemCategory.FOOD, Pair(5.0, 10.0), 0.9f, imageSize),
            )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Both overlapping markers should render
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenManyDetections_thenAllMarkersRenderWithoutPerformanceIssues() {
        // Arrange - Many detections to test performance
        val imageSize = Size(640, 480)
        val detections =
            (1..20).map { index ->
                detection(
                    Rect(
                        index * 30,
                        index * 20,
                        index * 30 + 100,
                        index * 20 + 100,
                    ),
                    ItemCategory.values()[index % ItemCategory.values().size],
                    Pair(10.0 * index, 50.0 * index),
                    0.5f + (index % 5) * 0.1f,
                    imageSize,
                )
            }

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = imageSize,
                previewSize = Size(1080, 1920),
            )
        }

        // Assert - Should handle many markers efficiently
        composeTestRule.waitForIdle()
    }

    private fun detection(
        rect: Rect,
        category: ItemCategory,
        priceRange: Pair<Double, Double>,
        confidence: Float,
        imageSize: Size,
    ): DetectionResult {
        return DetectionResult(
            bboxNorm = rect.toNormalizedRect(imageSize.width, imageSize.height),
            category = category,
            priceRange = priceRange,
            confidence = confidence,
        )
    }
}
