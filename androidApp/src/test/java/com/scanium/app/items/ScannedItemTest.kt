package com.scanium.app.items

import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.ml.ItemCategory
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import org.junit.Test

/**
 * Unit tests for ScannedItem confidence classification and formatting.
 *
 * Tests verify:
 * - Confidence level classification (LOW/MEDIUM/HIGH)
 * - Price range formatting
 * - Confidence percentage formatting
 * - Threshold boundary conditions
 */
class ScannedItemTest {

    @Test
    fun whenConfidenceBelow50Percent_thenLowConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.3f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.LOW)
        assertThat(item.confidenceLevel.displayName).isEqualTo("Low")
    }

    @Test
    fun whenConfidenceBetween50And75Percent_thenMediumConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.6f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.MEDIUM)
        assertThat(item.confidenceLevel.displayName).isEqualTo("Medium")
    }

    @Test
    fun whenConfidence75PercentOrAbove_thenHighConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.85f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.HIGH)
        assertThat(item.confidenceLevel.displayName).isEqualTo("High")
    }

    @Test
    fun whenConfidenceExactly50Percent_thenMediumConfidence() {
        // Arrange - Exactly at MEDIUM threshold
        val item = createTestItem(confidence = 0.5f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.MEDIUM)
    }

    @Test
    fun whenConfidenceExactly75Percent_thenHighConfidence() {
        // Arrange - Exactly at HIGH threshold
        val item = createTestItem(confidence = 0.75f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.HIGH)
    }

    @Test
    fun whenConfidenceZero_thenLowConfidence() {
        // Arrange - Edge case: 0% confidence
        val item = createTestItem(confidence = 0.0f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.LOW)
    }

    @Test
    fun whenConfidence100Percent_thenHighConfidence() {
        // Arrange - Edge case: 100% confidence
        val item = createTestItem(confidence = 1.0f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.HIGH)
    }

    @Test
    fun whenConfidenceJustBelow50Percent_thenLowConfidence() {
        // Arrange - Just below MEDIUM threshold
        val item = createTestItem(confidence = 0.49f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.LOW)
    }

    @Test
    fun whenConfidenceJustBelow75Percent_thenMediumConfidence() {
        // Arrange - Just below HIGH threshold
        val item = createTestItem(confidence = 0.74f)

        // Assert
        assertThat(item.confidenceLevel).isEqualTo(ConfidenceLevel.MEDIUM)
    }

    @Test
    fun whenFormattingConfidence_thenReturnsPercentage() {
        // Arrange
        val item = createTestItem(confidence = 0.85f)

        // Assert
        assertThat(item.formattedConfidence).isEqualTo("85%")
    }

    @Test
    fun whenFormattingLowConfidence_thenRoundsDown() {
        // Arrange
        val item = createTestItem(confidence = 0.456f) // 45.6%

        // Assert
        assertThat(item.formattedConfidence).isEqualTo("45%")
    }

    @Test
    fun whenFormattingHighConfidence_thenRoundsDown() {
        // Arrange
        val item = createTestItem(confidence = 0.999f) // 99.9%

        // Assert
        assertThat(item.formattedConfidence).isEqualTo("99%")
    }

    @Test
    fun whenFormattingZeroConfidence_thenReturnsZeroPercent() {
        // Arrange
        val item = createTestItem(confidence = 0.0f)

        // Assert
        assertThat(item.formattedConfidence).isEqualTo("0%")
    }

    @Test
    fun whenFormattingPriceRange_thenReturnsEuroFormat() {
        // Arrange
        val item = createTestItem(priceRange = 20.0 to 50.0)

        // Assert
        assertThat(item.formattedPriceRange).isEqualTo("€20–50")
    }

    @Test
    fun whenFormattingPriceWithDecimals_thenRoundsToWholeNumber() {
        // Arrange
        val item = createTestItem(priceRange = 19.99 to 49.99)

        // Assert
        assertThat(item.formattedPriceRange).isEqualTo("€20–50")
    }

    @Test
    fun whenFormattingLargePriceRange_thenFormatsCorrectly() {
        // Arrange
        val item = createTestItem(priceRange = 100.0 to 500.0)

        // Assert
        assertThat(item.formattedPriceRange).isEqualTo("€100–500")
    }

    @Test
    fun whenFormattingSmallPriceRange_thenFormatsCorrectly() {
        // Arrange
        val item = createTestItem(priceRange = 1.0 to 5.0)

        // Assert
        assertThat(item.formattedPriceRange).isEqualTo("€1–5")
    }

    @Test
    fun whenFormattingZeroPrice_thenFormatsCorrectly() {
        // Arrange - For PLACE category
        val item = createTestItem(priceRange = 0.0 to 0.0)

        // Assert
        assertThat(item.formattedPriceRange).isEqualTo("€0–0")
    }

    @Test
    fun whenItemHasCategory_thenCategoryStored() {
        // Arrange
        val item = createTestItem(category = ItemCategory.ELECTRONICS)

        // Assert
        assertThat(item.category).isEqualTo(ItemCategory.ELECTRONICS)
        assertThat(item.category.displayName).isEqualTo("Electronics")
    }

    @Test
    fun whenItemHasId_thenIdStored() {
        // Arrange
        val item = createTestItem(id = "test-tracking-id-123")

        // Assert
        assertThat(item.id).isEqualTo("test-tracking-id-123")
    }

    @Test
    fun whenItemHasTimestamp_thenTimestampStored() {
        // Arrange
        val now = System.currentTimeMillis()
        val item = createTestItem(timestamp = now)

        // Assert
        assertThat(item.timestamp).isEqualTo(now)
    }

    @Test
    fun whenDefaultItemCreated_thenHasUniqueId() {
        // Act - Create two items without specifying ID
        val item1 = ScannedItem(
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 20.0
        )
        val item2 = ScannedItem(
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 20.0
        )

        // Assert - IDs should be different (UUIDs)
        assertThat(item1.id).isNotEqualTo(item2.id)
    }

    @Test
    fun whenConfidenceLevelThresholdsChecked_thenMatchExpectedValues() {
        // Assert - Verify threshold constants
        assertThat(ConfidenceLevel.LOW.threshold).isEqualTo(0.0f)
        assertThat(ConfidenceLevel.MEDIUM.threshold).isEqualTo(0.5f)
        assertThat(ConfidenceLevel.HIGH.threshold).isEqualTo(0.75f)
    }

    @Test
    fun whenConfidenceLevelDescriptionsChecked_thenHaveAppropriateText() {
        // Assert - Verify descriptions exist and are non-empty
        assertThat(ConfidenceLevel.LOW.description).isNotEmpty()
        assertThat(ConfidenceLevel.MEDIUM.description).isNotEmpty()
        assertThat(ConfidenceLevel.HIGH.description).isNotEmpty()
    }

    @Test
    fun whenMultipleConfidenceLevels_thenAllHaveUniqueDisplayNames() {
        // Act
        val displayNames = ConfidenceLevel.values().map { it.displayName }

        // Assert
        assertThat(displayNames).containsNoDuplicates()
    }

    @Test
    fun whenBoundaryTestingAllThresholds_thenCorrectClassification() {
        // Test values just above and below each threshold
        val testCases = mapOf(
            0.0f to ConfidenceLevel.LOW,
            0.49f to ConfidenceLevel.LOW,
            0.5f to ConfidenceLevel.MEDIUM,
            0.51f to ConfidenceLevel.MEDIUM,
            0.74f to ConfidenceLevel.MEDIUM,
            0.75f to ConfidenceLevel.HIGH,
            0.76f to ConfidenceLevel.HIGH,
            1.0f to ConfidenceLevel.HIGH
        )

        // Assert all test cases
        testCases.forEach { (confidence, expectedLevel) ->
            val item = createTestItem(confidence = confidence)
            assertThat(item.confidenceLevel)
                .isEqualTo(expectedLevel)
        }
    }

    // Helper function to create test items
    private fun createTestItem(
        id: String = "test-id",
        category: ItemCategory = ItemCategory.FASHION,
        priceRange: Pair<Double, Double> = 10.0 to 20.0,
        confidence: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis()
    ): ScannedItem {
        val range = PriceRange(Money(priceRange.first), Money(priceRange.second))
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = priceRange,
            estimatedPriceRange = range,
            priceEstimationStatus = PriceEstimationStatus.Ready(range),
            confidence = confidence,
            timestamp = timestamp
        )
    }
}
