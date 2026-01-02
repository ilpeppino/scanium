package com.scanium.shared.core.models.items

import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ScannedItem confidence classification and formatting.
 *
 * Tests verify:
 * - Confidence level classification (LOW/MEDIUM/HIGH)
 * - Price range formatting
 * - Confidence percentage formatting
 * - Threshold boundary conditions
 *
 * Migrated from androidApp to KMP for cross-platform testing.
 */
class ScannedItemTest {
    @Test
    fun whenConfidenceBelow50Percent_thenLowConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.3f)

        // Assert
        assertEquals(ConfidenceLevel.LOW, item.confidenceLevel)
        assertEquals("Low", item.confidenceLevel.displayName)
    }

    @Test
    fun whenConfidenceBetween50And75Percent_thenMediumConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.6f)

        // Assert
        assertEquals(ConfidenceLevel.MEDIUM, item.confidenceLevel)
        assertEquals("Medium", item.confidenceLevel.displayName)
    }

    @Test
    fun whenConfidence75PercentOrAbove_thenHighConfidence() {
        // Arrange
        val item = createTestItem(confidence = 0.85f)

        // Assert
        assertEquals(ConfidenceLevel.HIGH, item.confidenceLevel)
        assertEquals("High", item.confidenceLevel.displayName)
    }

    @Test
    fun whenConfidenceExactly50Percent_thenMediumConfidence() {
        // Arrange - Exactly at MEDIUM threshold
        val item = createTestItem(confidence = 0.5f)

        // Assert
        assertEquals(ConfidenceLevel.MEDIUM, item.confidenceLevel)
    }

    @Test
    fun whenConfidenceExactly75Percent_thenHighConfidence() {
        // Arrange - Exactly at HIGH threshold
        val item = createTestItem(confidence = 0.75f)

        // Assert
        assertEquals(ConfidenceLevel.HIGH, item.confidenceLevel)
    }

    @Test
    fun whenConfidenceZero_thenLowConfidence() {
        // Arrange - Edge case: 0% confidence
        val item = createTestItem(confidence = 0.0f)

        // Assert
        assertEquals(ConfidenceLevel.LOW, item.confidenceLevel)
    }

    @Test
    fun whenConfidence100Percent_thenHighConfidence() {
        // Arrange - Edge case: 100% confidence
        val item = createTestItem(confidence = 1.0f)

        // Assert
        assertEquals(ConfidenceLevel.HIGH, item.confidenceLevel)
    }

    @Test
    fun whenConfidenceJustBelow50Percent_thenLowConfidence() {
        // Arrange - Just below MEDIUM threshold
        val item = createTestItem(confidence = 0.49f)

        // Assert
        assertEquals(ConfidenceLevel.LOW, item.confidenceLevel)
    }

    @Test
    fun whenConfidenceJustBelow75Percent_thenMediumConfidence() {
        // Arrange - Just below HIGH threshold
        val item = createTestItem(confidence = 0.74f)

        // Assert
        assertEquals(ConfidenceLevel.MEDIUM, item.confidenceLevel)
    }

    @Test
    fun whenFormattingConfidence_thenReturnsPercentage() {
        // Arrange
        val item = createTestItem(confidence = 0.85f)

        // Assert
        assertEquals("85%", item.formattedConfidence)
    }

    @Test
    fun whenFormattingLowConfidence_thenRoundsDown() {
        // Arrange
        val item = createTestItem(confidence = 0.456f) // 45.6%

        // Assert
        assertEquals("45%", item.formattedConfidence)
    }

    @Test
    fun whenFormattingHighConfidence_thenRoundsDown() {
        // Arrange
        val item = createTestItem(confidence = 0.999f) // 99.9%

        // Assert
        assertEquals("99%", item.formattedConfidence)
    }

    @Test
    fun whenFormattingZeroConfidence_thenReturnsZeroPercent() {
        // Arrange
        val item = createTestItem(confidence = 0.0f)

        // Assert
        assertEquals("0%", item.formattedConfidence)
    }

    @Test
    fun whenFormattingPriceRange_thenReturnsEuroFormat() {
        // Arrange
        val item = createTestItem(priceRange = 20.0 to 50.0)

        // Assert
        assertEquals("€20–50", item.formattedPriceRange)
    }

    @Test
    fun whenFormattingPriceWithDecimals_thenRoundsToWholeNumber() {
        // Arrange
        val item = createTestItem(priceRange = 19.99 to 49.99)

        // Assert
        assertEquals("€20–50", item.formattedPriceRange)
    }

    @Test
    fun whenFormattingLargePriceRange_thenFormatsCorrectly() {
        // Arrange
        val item = createTestItem(priceRange = 100.0 to 500.0)

        // Assert
        assertEquals("€100–500", item.formattedPriceRange)
    }

    @Test
    fun whenFormattingSmallPriceRange_thenFormatsCorrectly() {
        // Arrange
        val item = createTestItem(priceRange = 1.0 to 5.0)

        // Assert
        assertEquals("€1–5", item.formattedPriceRange)
    }

    @Test
    fun whenFormattingZeroPrice_thenFormatsCorrectly() {
        // Arrange - For PLACE category
        val item = createTestItem(priceRange = 0.0 to 0.0)

        // Assert
        assertEquals("€0–0", item.formattedPriceRange)
    }

    @Test
    fun whenItemHasCategory_thenCategoryStored() {
        // Arrange
        val item = createTestItem(category = ItemCategory.ELECTRONICS)

        // Assert
        assertEquals(ItemCategory.ELECTRONICS, item.category)
        assertEquals("Electronics", item.category.displayName)
    }

    @Test
    fun whenItemHasId_thenIdStored() {
        // Arrange
        val item = createTestItem(id = "test-tracking-id-123")

        // Assert
        assertEquals("test-tracking-id-123", item.id)
    }

    @Test
    fun whenItemHasTimestamp_thenTimestampStored() {
        // Arrange
        val now = Clock.System.now().toEpochMilliseconds()
        val item = createTestItem(timestamp = now)

        // Assert
        assertEquals(now, item.timestamp)
    }

    @Test
    fun whenDefaultItemCreated_thenHasUniqueId() {
        // Act - Create two items without specifying ID
        val item1 =
            ScannedItem<Any>(
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0,
            )
        val item2 =
            ScannedItem<Any>(
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0,
            )

        // Assert - IDs should be different (UUIDs)
        assertNotEquals(item1.id, item2.id)
    }

    @Test
    fun whenConfidenceLevelThresholdsChecked_thenMatchExpectedValues() {
        // Assert - Verify threshold constants
        assertEquals(0.0f, ConfidenceLevel.LOW.threshold)
        assertEquals(0.5f, ConfidenceLevel.MEDIUM.threshold)
        assertEquals(0.75f, ConfidenceLevel.HIGH.threshold)
    }

    @Test
    fun whenConfidenceLevelDescriptionsChecked_thenHaveAppropriateText() {
        // Assert - Verify descriptions exist and are non-empty
        assertTrue(ConfidenceLevel.LOW.description.isNotEmpty())
        assertTrue(ConfidenceLevel.MEDIUM.description.isNotEmpty())
        assertTrue(ConfidenceLevel.HIGH.description.isNotEmpty())
    }

    @Test
    fun displayLabelPrefersSpecificLabelText() {
        val item = createTestItem(label = " vintage mug ")

        assertEquals("Vintage mug", item.displayLabel)
    }

    @Test
    fun displayLabelFallsBackToCategoryWhenLabelMissing() {
        val item = createTestItem(label = null, category = ItemCategory.ELECTRONICS)

        assertEquals("Electronics", item.displayLabel)
    }

    @Test
    fun displayLabelIgnoresBlankLabels() {
        val item = createTestItem(label = "   ", category = ItemCategory.HOME_GOOD)

        assertEquals("Home Good", item.displayLabel)
    }

    @Test
    fun whenMultipleConfidenceLevels_thenAllHaveUniqueDisplayNames() {
        // Act
        val displayNames = ConfidenceLevel.values().map { it.displayName }

        // Assert - Check for duplicates
        assertEquals(displayNames.toSet().size, displayNames.size, "Display names should be unique")
    }

    @Test
    fun whenBoundaryTestingAllThresholds_thenCorrectClassification() {
        // Test values just above and below each threshold
        val testCases =
            mapOf(
                0.0f to ConfidenceLevel.LOW,
                0.49f to ConfidenceLevel.LOW,
                0.5f to ConfidenceLevel.MEDIUM,
                0.51f to ConfidenceLevel.MEDIUM,
                0.74f to ConfidenceLevel.MEDIUM,
                0.75f to ConfidenceLevel.HIGH,
                0.76f to ConfidenceLevel.HIGH,
                1.0f to ConfidenceLevel.HIGH,
            )

        // Assert all test cases
        testCases.forEach { (confidence, expectedLevel) ->
            val item = createTestItem(confidence = confidence)
            assertEquals(
                expectedLevel,
                item.confidenceLevel,
                "Confidence $confidence should map to $expectedLevel",
            )
        }
    }

    // Helper function to create test items (KMP-compatible)
    private fun createTestItem(
        id: String = "test-id",
        category: ItemCategory = ItemCategory.FASHION,
        priceRange: Pair<Double, Double> = 10.0 to 20.0,
        confidence: Float = 0.5f,
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        label: String? = null,
    ): ScannedItem<Any> {
        val range = PriceRange(Money(priceRange.first), Money(priceRange.second))
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = priceRange,
            estimatedPriceRange = range,
            priceEstimationStatus = PriceEstimationStatus.Ready(range),
            confidence = confidence,
            timestamp = timestamp,
            labelText = label,
        )
    }
}
