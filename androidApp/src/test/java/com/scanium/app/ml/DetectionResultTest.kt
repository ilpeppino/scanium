package com.scanium.app.ml

import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for DetectionResult data class.
 *
 * Tests verify:
 * - Data class initialization with all parameters
 * - Formatted price range display logic
 * - Proper handling of optional trackingId
 */
@RunWith(RobolectricTestRunner::class)
class DetectionResultTest {

    @Test
    fun whenDetectionResultCreated_thenAllFieldsAreSet() {
        // Arrange & Act
        val bboxNorm = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        val category = ItemCategory.FASHION
        val priceRange = Pair(10.0, 50.0)
        val confidence = 0.85f
        val trackingId = 12345

        val result = DetectionResult(
            bboxNorm = bboxNorm,
            category = category,
            priceRange = priceRange,
            confidence = confidence,
            trackingId = trackingId
        )

        // Assert
        assertThat(result.bboxNorm).isEqualTo(bboxNorm)
        assertThat(result.category).isEqualTo(category)
        assertThat(result.priceRange).isEqualTo(priceRange)
        assertThat(result.confidence).isEqualTo(confidence)
        assertThat(result.trackingId).isEqualTo(trackingId)
    }

    @Test
    fun whenTrackingIdIsNull_thenDetectionResultIsValid() {
        // Arrange & Act
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 15.0),
            confidence = 0.9f,
            trackingId = null
        )

        // Assert
        assertThat(result.trackingId).isNull()
    }

    @Test
    fun whenFormattedPriceRange_thenReturnsCorrectFormat() {
        // Arrange
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.HOME_GOOD,
            priceRange = Pair(20.0, 50.0),
            confidence = 0.75f
        )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertThat(formatted).isEqualTo("€20 - €50")
    }

    @Test
    fun whenPriceRangeHasDecimals_thenRoundsToWholeNumbers() {
        // Arrange
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.PLACE,
            priceRange = Pair(10.99, 25.49),
            confidence = 0.6f
        )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertThat(formatted).isEqualTo("€11 - €25")
    }

    @Test
    fun whenPriceRangeIsZero_thenFormatsCorrectly() {
        // Arrange
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.PLANT,
            priceRange = Pair(0.0, 0.0),
            confidence = 0.5f
        )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertThat(formatted).isEqualTo("€0 - €0")
    }

    @Test
    fun whenPriceRangeIsLarge_thenFormatsWithoutDecimals() {
        // Arrange
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(500.0, 1000.0),
            confidence = 0.95f
        )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertThat(formatted).isEqualTo("€500 - €1000")
    }

    @Test
    fun whenBoundingBoxHasDimensions_thenCanAccessProperties() {
        // Arrange
        val boundingBox = NormalizedRect(0.05f, 0.1f, 0.25f, 0.4f)
        val result = DetectionResult(
            bboxNorm = boundingBox,
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 20.0),
            confidence = 0.8f
        )

        // Act & Assert
        assertThat(result.bboxNorm.left).isEqualTo(0.05f)
        assertThat(result.bboxNorm.top).isEqualTo(0.1f)
        assertThat(result.bboxNorm.right).isEqualTo(0.25f)
        assertThat(result.bboxNorm.bottom).isEqualTo(0.4f)
        assertThat(result.bboxNorm.width).isEqualTo(0.2f)
        assertThat(result.bboxNorm.height).isEqualTo(0.3f)
    }

    @Test
    fun whenConfidenceIsLow_thenStillCreatesValidResult() {
        // Arrange & Act
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(1.0, 5.0),
            confidence = 0.1f
        )

        // Assert
        assertThat(result.confidence).isEqualTo(0.1f)
    }

    @Test
    fun whenConfidenceIsHigh_thenStillCreatesValidResult() {
        // Arrange & Act
        val result = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 15.0),
            confidence = 1.0f
        )

        // Assert
        assertThat(result.confidence).isEqualTo(1.0f)
    }

    @Test
    fun whenMultipleDetectionsCreated_thenEachIsIndependent() {
        // Arrange & Act
        val detection1 = DetectionResult(
            bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 20.0),
            confidence = 0.8f,
            trackingId = 1
        )

        val detection2 = DetectionResult(
            bboxNorm = NormalizedRect(0.2f, 0.2f, 0.3f, 0.3f),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 10.0),
            confidence = 0.9f,
            trackingId = 2
        )

        // Assert - Each detection maintains its own values
        assertThat(detection1.trackingId).isEqualTo(1)
        assertThat(detection2.trackingId).isEqualTo(2)
        assertThat(detection1.category).isNotEqualTo(detection2.category)
        assertThat(detection1.bboxNorm).isNotEqualTo(detection2.bboxNorm)
    }
}
