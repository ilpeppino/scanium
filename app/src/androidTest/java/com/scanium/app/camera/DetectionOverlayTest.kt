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
 * - Bounding box rendering for detections
 * - Category label display
 * - Confidence score display
 * - Price range display
 * - Multiple detection handling
 * - Empty detection list handling
 * - Coordinate transformation for different image/preview sizes
 */
@RunWith(AndroidJUnit4::class)
class DetectionOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenNoDetections_thenOverlayIsEmpty() {
        // Arrange
        composeTestRule.setContent {
            DetectionOverlay(
                detections = emptyList(),
                imageSize = Size(1280, 720),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - No text nodes should be present
        composeTestRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
    }

    @Test
    fun whenSingleDetection_thenCategoryLabelIsDisplayed() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 50.0),
            confidence = 0.85f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Category label should be visible
        composeTestRule.onNodeWithText("Fashion", substring = true).assertExists()
    }

    @Test
    fun whenDetectionWithConfidence_thenConfidenceScoreIsDisplayed() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(50, 50, 200, 200),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 15.0),
            confidence = 0.92f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Confidence should be displayed as percentage
        composeTestRule.onNodeWithText("92%", substring = true).assertExists()
    }

    @Test
    fun whenDetectionWithPriceRange_thenPriceIsDisplayed() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.HOME_GOOD,
            priceRange = Pair(20.0, 80.0),
            confidence = 0.75f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Price range should be displayed
        composeTestRule.onNodeWithText("€20 - €80", substring = true).assertExists()
    }

    @Test
    fun whenMultipleDetections_thenAllCategoriesAreDisplayed() {
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

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - All three categories should be present
        composeTestRule.onNodeWithText("Fashion", substring = true).assertExists()
        composeTestRule.onNodeWithText("Food Product", substring = true).assertExists()
        composeTestRule.onNodeWithText("Plant", substring = true).assertExists()
    }

    @Test
    fun whenMultipleDetections_thenAllConfidenceScoresAreDisplayed() {
        // Arrange
        val detections = listOf(
            DetectionResult(
                boundingBox = Rect(50, 50, 150, 150),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 30.0),
                confidence = 0.85f
            ),
            DetectionResult(
                boundingBox = Rect(200, 200, 350, 350),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 10.0),
                confidence = 0.92f
            )
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Both confidence scores should be present
        composeTestRule.onNodeWithText("85%", substring = true).assertExists()
        composeTestRule.onNodeWithText("92%", substring = true).assertExists()
    }

    @Test
    fun whenUnknownCategory_thenUnknownLabelIsDisplayed() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(1.0, 100.0),
            confidence = 0.5f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Unknown category should be displayed
        composeTestRule.onNodeWithText("Unknown", substring = true).assertExists()
    }

    @Test
    fun whenLowConfidence_thenStillDisplaysCorrectly() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.PLACE,
            priceRange = Pair(50.0, 200.0),
            confidence = 0.35f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Low confidence should still render
        composeTestRule.onNodeWithText("35%", substring = true).assertExists()
        composeTestRule.onNodeWithText("Place", substring = true).assertExists()
    }

    @Test
    fun whenHighConfidence_thenDisplaysAsHundredPercent() {
        // Arrange
        val detection = DetectionResult(
            boundingBox = Rect(100, 100, 300, 300),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 15.0),
            confidence = 1.0f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - 100% confidence should be displayed
        composeTestRule.onNodeWithText("100%", substring = true).assertExists()
    }

    @Test
    fun whenDetectionsListUpdates_thenOverlayUpdates() {
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
        composeTestRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)

        // Act - Update to include a detection
        detections = listOf(
            DetectionResult(
                boundingBox = Rect(100, 100, 300, 300),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 50.0),
                confidence = 0.85f
            )
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = detections,
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Assert - Detection should now be visible
        composeTestRule.onNodeWithText("Fashion", substring = true).assertExists()
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

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(1920, 1080), // 16:9 landscape
                previewSize = Size(1080, 1920) // 9:16 portrait
            )
        }

        // Assert - Overlay should render despite different sizes
        composeTestRule.onNodeWithText("Home Good", substring = true).assertExists()
        composeTestRule.onNodeWithText("€20 - €60", substring = true).assertExists()
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

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Small detection should still be visible
        composeTestRule.onNodeWithText("Food Product", substring = true).assertExists()
        composeTestRule.onNodeWithText("60%", substring = true).assertExists()
    }

    @Test
    fun whenLargeBoundingBox_thenRendersWithinBounds() {
        // Arrange - Large detection covering most of frame
        val detection = DetectionResult(
            boundingBox = Rect(10, 10, 630, 470),
            category = ItemCategory.PLACE,
            priceRange = Pair(100.0, 500.0),
            confidence = 0.9f
        )

        composeTestRule.setContent {
            DetectionOverlay(
                detections = listOf(detection),
                imageSize = Size(640, 480),
                previewSize = Size(1080, 1920)
            )
        }

        // Assert - Large detection should be visible
        composeTestRule.onNodeWithText("Place", substring = true).assertExists()
        composeTestRule.onNodeWithText("€100 - €500", substring = true).assertExists()
    }
}
