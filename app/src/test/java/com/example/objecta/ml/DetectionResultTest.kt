package com.example.objecta.ml

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for DetectionResult data class.
 *
 * Tests verify:
 * - Data class initialization with all parameters
 * - Formatted price range display logic
 * - Proper handling of optional trackingId
 */
class DetectionResultTest {

    @Test
    fun whenDetectionResultCreated_thenAllFieldsAreSet() {
        // Arrange & Act
        val boundingBox = Rect(100, 200, 300, 400)
        val category = ItemCategory.FASHION
        val priceRange = Pair(10.0, 50.0)
        val confidence = 0.85f
        val trackingId = 12345

        val result = DetectionResult(
            boundingBox = boundingBox,
            category = category,
            priceRange = priceRange,
            confidence = confidence,
            trackingId = trackingId
        )

        // Assert
        assertThat(result.boundingBox).isEqualTo(boundingBox)
        assertThat(result.category).isEqualTo(category)
        assertThat(result.priceRange).isEqualTo(priceRange)
        assertThat(result.confidence).isEqualTo(confidence)
        assertThat(result.trackingId).isEqualTo(trackingId)
    }

    @Test
    fun whenTrackingIdIsNull_thenDetectionResultIsValid() {
        // Arrange & Act
        val result = DetectionResult(
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
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
        val boundingBox = Rect(50, 100, 250, 400)
        val result = DetectionResult(
            boundingBox = boundingBox,
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 20.0),
            confidence = 0.8f
        )

        // Act & Assert
        assertThat(result.boundingBox.left).isEqualTo(50)
        assertThat(result.boundingBox.top).isEqualTo(100)
        assertThat(result.boundingBox.right).isEqualTo(250)
        assertThat(result.boundingBox.bottom).isEqualTo(400)
        assertThat(result.boundingBox.width()).isEqualTo(200)
        assertThat(result.boundingBox.height()).isEqualTo(300)
    }

    @Test
    fun whenConfidenceIsLow_thenStillCreatesValidResult() {
        // Arrange & Act
        val result = DetectionResult(
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
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
            boundingBox = Rect(0, 0, 100, 100),
            category = ItemCategory.FASHION,
            priceRange = Pair(10.0, 20.0),
            confidence = 0.8f,
            trackingId = 1
        )

        val detection2 = DetectionResult(
            boundingBox = Rect(200, 200, 300, 300),
            category = ItemCategory.FOOD,
            priceRange = Pair(5.0, 10.0),
            confidence = 0.9f,
            trackingId = 2
        )

        // Assert - Each detection maintains its own values
        assertThat(detection1.trackingId).isEqualTo(1)
        assertThat(detection2.trackingId).isEqualTo(2)
        assertThat(detection1.category).isNotEqualTo(detection2.category)
        assertThat(detection1.boundingBox).isNotEqualTo(detection2.boundingBox)
    }
}
