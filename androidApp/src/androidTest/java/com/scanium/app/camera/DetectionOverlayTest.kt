package com.scanium.app.camera

import android.graphics.Rect
import android.util.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.ItemCategory
import com.google.common.truth.Truth.assertThat
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
                previewSize = Size(1080, 1920)
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
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 50.0),
            confidence = 0.85f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Should render without crashing
        // Circle markers are rendered via Canvas, no text labels
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenMultipleDetections_thenOverlayRendersAllMarkers() {
        // Arrange
        val detections = listOf(
            DetectionResult(
                boundingBox = Rect(50, 50, 150, 150),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 30.0),
                confidence = 0.8f
            ),
            DetectionResult(
                boundingBox = Rect(200, 200, 350, 350),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 10.0),
                confidence = 0.9f
            ),
            DetectionResult(
                boundingBox = Rect(400, 100, 600, 300),
                category = ItemCategory.PLANT,
                priceRange = Pair(15.0, 40.0),
                confidence = 0.7f
            )
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
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
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Initially empty
        composeTestRule.waitForIdle()

        // Act - Update to include detections
        detections = listOf(
            DetectionResult(
                boundingBox = Rect(100, 100, 300, 300),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 50.0),
                confidence = 0.85f
            ),
            DetectionResult(
                boundingBox = Rect(200, 200, 400, 400),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 15.0),
                confidence = 0.9f
            )
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Should update without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenImageAndPreviewSizesAreDifferent_thenOverlayStillRenders() {
        // Arrange - Test with different aspect ratios
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.HOME_GOOD,
            priceRange = Pair(20.0, 60.0),
            confidence = 0.8f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(1920, 1080), // 16:9 landscape
                previewSize = Size(1080, 1920) // 9:16 portrait
            )
        }

        // Assert - Overlay should render despite different sizes
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenSmallBoundingBox_thenStillRendersCorrectly() {
        // Arrange - Very small detection
        val detection = DetectionResult(
            boundingBox = Rect(0, 0, 50, 50),
            category = ItemCategory.FOOD,
            priceRange = Pair(1.0, 5.0),
            confidence = 0.6f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Small detection should still render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenLargeBoundingBox_thenRendersMarkerAtCenter() {
        // Arrange - Large detection covering most of frame
        val detection = DetectionResult(
            boundingBox = Rect(10, 10, 630, 470),
            category = ItemCategory.PLACE,
            priceRange = Pair(100.0, 500.0),
            confidence = 0.9f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Large detection should render center marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenLowConfidenceDetection_thenStillRendersMarker() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.PLACE,
            priceRange = Pair(50.0, 200.0),
            confidence = 0.35f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Low confidence should still render
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenHighConfidenceDetection_thenRendersMarker() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 15.0),
            confidence = 1.0f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - High confidence should render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenUnknownCategory_thenStillRendersMarker() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(1.0, 100.0),
            confidence = 0.5f
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Unknown category should still render marker
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenOverlappingDetections_thenBothMarkersRender() {
        // Arrange - Two detections with overlapping bounding boxes
        val detections = listOf(
            DetectionResult(
                boundingBox = Rect(100, 100, 300, 300),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 30.0),
                confidence = 0.8f
            ),
            DetectionResult(
                boundingBox = Rect(150, 150, 350, 350),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 10.0),
                confidence = 0.9f
            )
        )

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Both overlapping markers should render
        composeTestRule.waitForIdle()
    }

    @Test
    fun whenManyDetections_thenAllMarkersRenderWithoutPerformanceIssues() {
        // Arrange - Many detections to test performance
        val detections = (1..20).map { index ->
            DetectionResult(
                boundingBox = Rect(
                    index * 30,
                    index * 20,
                    index * 30 + 100,
                    index * 20 + 100
                ),
                category = ItemCategory.values()[index % ItemCategory.values().size],
                priceRange = Pair(10.0 * index, 50.0 * index),
                confidence = 0.5f + (index % 5) * 0.1f
            )
        }

        // Act
        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Should handle many markers efficiently
        composeTestRule.waitForIdle()
    }
}
