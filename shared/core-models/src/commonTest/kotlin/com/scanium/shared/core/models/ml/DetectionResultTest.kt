package com.scanium.shared.core.models.ml

import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Unit tests for DetectionResult data class.
 *
 * Tests verify:
 * - Data class initialization with all parameters
 * - Formatted price range display logic
 * - Proper handling of optional trackingId
 *
 * Migrated from androidApp to KMP for cross-platform testing.
 */
class DetectionResultTest {
    @Test
    fun whenDetectionResultCreated_thenAllFieldsAreSet() {
        // Arrange & Act
        val bboxNorm = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        val category = ItemCategory.FASHION
        val priceRange = Pair(10.0, 50.0)
        val confidence = 0.85f
        val trackingId = 12345

        val result =
            DetectionResult(
                bboxNorm = bboxNorm,
                category = category,
                priceRange = priceRange,
                confidence = confidence,
                trackingId = trackingId,
            )

        // Assert
        assertEquals(bboxNorm, result.bboxNorm)
        assertEquals(category, result.category)
        assertEquals(priceRange, result.priceRange)
        assertEquals(confidence, result.confidence)
        assertEquals(trackingId, result.trackingId)
    }

    @Test
    fun whenTrackingIdIsNull_thenDetectionResultIsValid() {
        // Arrange & Act
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 15.0),
                confidence = 0.9f,
                trackingId = null,
            )

        // Assert
        assertNull(result.trackingId)
    }

    @Test
    fun whenFormattedPriceRange_thenReturnsCorrectFormat() {
        // Arrange
        val range = PriceRange(Money(20.0), Money(50.0))
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.HOME_GOOD,
                priceRange = Pair(20.0, 50.0),
                estimatedPriceRange = range,
                priceEstimationStatus = PriceEstimationStatus.Ready(range),
                confidence = 0.75f,
            )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertEquals("€20–50", formatted)
    }

    @Test
    fun whenPriceRangeHasDecimals_thenRoundsToWholeNumbers() {
        // Arrange
        val range = PriceRange(Money(10.99), Money(25.49))
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.PLACE,
                priceRange = Pair(10.99, 25.49),
                estimatedPriceRange = range,
                priceEstimationStatus = PriceEstimationStatus.Ready(range),
                confidence = 0.6f,
            )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertEquals("€11–25", formatted)
    }

    @Test
    fun whenPriceRangeIsZero_thenFormatsCorrectly() {
        // Arrange
        val range = PriceRange(Money(0.0), Money(0.0))
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.PLANT,
                priceRange = Pair(0.0, 0.0),
                estimatedPriceRange = range,
                priceEstimationStatus = PriceEstimationStatus.Ready(range),
                confidence = 0.5f,
            )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertEquals("€0–0", formatted)
    }

    @Test
    fun whenPriceRangeIsLarge_thenFormatsWithoutDecimals() {
        // Arrange
        val range = PriceRange(Money(500.0), Money(1000.0))
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.UNKNOWN,
                priceRange = Pair(500.0, 1000.0),
                estimatedPriceRange = range,
                priceEstimationStatus = PriceEstimationStatus.Ready(range),
                confidence = 0.95f,
            )

        // Act
        val formatted = result.formattedPriceRange

        // Assert
        assertEquals("€500–1000", formatted)
    }

    @Test
    fun whenBoundingBoxHasDimensions_thenCanAccessProperties() {
        // Arrange
        val boundingBox = NormalizedRect(0.05f, 0.1f, 0.25f, 0.4f)
        val result =
            DetectionResult(
                bboxNorm = boundingBox,
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 20.0),
                confidence = 0.8f,
            )

        // Act & Assert
        assertEquals(0.05f, result.bboxNorm.left)
        assertEquals(0.1f, result.bboxNorm.top)
        assertEquals(0.25f, result.bboxNorm.right)
        assertEquals(0.4f, result.bboxNorm.bottom)
        assertEquals(0.2f, result.bboxNorm.width, 0.001f)
        assertEquals(0.3f, result.bboxNorm.height, 0.001f)
    }

    @Test
    fun whenConfidenceIsLow_thenStillCreatesValidResult() {
        // Arrange & Act
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.UNKNOWN,
                priceRange = Pair(1.0, 5.0),
                confidence = 0.1f,
            )

        // Assert
        assertEquals(0.1f, result.confidence)
    }

    @Test
    fun whenConfidenceIsHigh_thenStillCreatesValidResult() {
        // Arrange & Act
        val result =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 15.0),
                confidence = 1.0f,
            )

        // Assert
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun whenMultipleDetectionsCreated_thenEachIsIndependent() {
        // Arrange & Act
        val detection1 =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.1f, 0.1f),
                category = ItemCategory.FASHION,
                priceRange = Pair(10.0, 20.0),
                confidence = 0.8f,
                trackingId = 1,
            )

        val detection2 =
            DetectionResult(
                bboxNorm = NormalizedRect(0.2f, 0.2f, 0.3f, 0.3f),
                category = ItemCategory.FOOD,
                priceRange = Pair(5.0, 10.0),
                confidence = 0.9f,
                trackingId = 2,
            )

        // Assert - Each detection maintains its own values
        assertEquals(1, detection1.trackingId)
        assertEquals(2, detection2.trackingId)
        assertNotEquals(detection1.category, detection2.category)
        assertNotEquals(detection1.bboxNorm, detection2.bboxNorm)
    }
}
