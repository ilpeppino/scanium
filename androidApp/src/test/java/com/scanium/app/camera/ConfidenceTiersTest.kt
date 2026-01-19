package com.scanium.app.camera

import com.google.common.truth.Truth.assertThat
import com.scanium.app.NormalizedRect
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import org.junit.Test

/**
 * Unit tests for [ConfidenceTiers] step generation and filtering.
 * These are pure Kotlin tests with no Android framework dependencies.
 */
class ConfidenceTiersTest {
    // ==================== Tier Structure Tests ====================

    @Test
    fun tiersAreOrderedByAscendingMinConfidence() {
        val tiers = ConfidenceTiers.tiers

        for (i in 0 until tiers.size - 1) {
            assertThat(tiers[i].minConfidence)
                .isLessThan(tiers[i + 1].minConfidence)
        }
    }

    @Test
    fun firstTierHasZeroMinConfidence() {
        val firstTier = ConfidenceTiers.tiers.first()

        assertThat(firstTier.minConfidence).isEqualTo(0f)
        assertThat(firstTier.name).isEqualTo("All")
    }

    @Test
    fun stepCountMatchesTierCount() {
        assertThat(ConfidenceTiers.stepCount).isEqualTo(ConfidenceTiers.tiers.size)
    }

    @Test
    fun allTiersHaveNonEmptyNamesAndDescriptions() {
        ConfidenceTiers.tiers.forEach { tier ->
            assertThat(tier.name).isNotEmpty()
            assertThat(tier.description).isNotEmpty()
        }
    }

    // ==================== Step Index Lookup Tests ====================

    @Test
    fun getTierReturnsCorrectTierForValidIndex() {
        ConfidenceTiers.tiers.forEachIndexed { index, expectedTier ->
            val actualTier = ConfidenceTiers.getTier(index)
            assertThat(actualTier).isEqualTo(expectedTier)
        }
    }

    @Test
    fun getTierClampsNegativeIndexToFirst() {
        val tier = ConfidenceTiers.getTier(-1)
        assertThat(tier).isEqualTo(ConfidenceTiers.tiers.first())
    }

    @Test
    fun getTierClampsOverflowIndexToLast() {
        val tier = ConfidenceTiers.getTier(999)
        assertThat(tier).isEqualTo(ConfidenceTiers.tiers.last())
    }

    @Test
    fun getMinConfidenceReturnsCorrectValueForEachStep() {
        assertThat(ConfidenceTiers.getMinConfidence(0)).isEqualTo(0f)
        assertThat(ConfidenceTiers.getMinConfidence(1)).isEqualTo(0.25f)
        assertThat(ConfidenceTiers.getMinConfidence(2)).isEqualTo(0.50f)
        assertThat(ConfidenceTiers.getMinConfidence(3)).isEqualTo(0.75f)
    }

    @Test
    fun getLabelReturnsCorrectValueForEachStep() {
        assertThat(ConfidenceTiers.getLabel(0)).isEqualTo("All")
        assertThat(ConfidenceTiers.getLabel(1)).isEqualTo("Low+")
        assertThat(ConfidenceTiers.getLabel(2)).isEqualTo("Medium+")
        assertThat(ConfidenceTiers.getLabel(3)).isEqualTo("High only")
    }

    // ==================== Display Text Tests ====================

    @Test
    fun getDisplayTextForAllStepShowsSimpleLabel() {
        val displayText = ConfidenceTiers.getDisplayText(0)
        assertThat(displayText).isEqualTo("Showing: All")
    }

    @Test
    fun getDisplayTextForOtherStepsIncludesPercentage() {
        assertThat(ConfidenceTiers.getDisplayText(1)).isEqualTo("Showing: Low+ (>=25%)")
        assertThat(ConfidenceTiers.getDisplayText(2)).isEqualTo("Showing: Medium+ (>=50%)")
        assertThat(ConfidenceTiers.getDisplayText(3)).isEqualTo("Showing: High only (>=75%)")
    }

    // ==================== Filter Tests ====================

    private fun createTestTrack(
        confidence: Float,
        label: String = "Test",
    ): OverlayTrack {
        return OverlayTrack(
            bboxNorm = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            label = label,
            priceText = "",
            confidence = confidence,
            isReady = true,
            priceEstimationStatus = PriceEstimationStatus.Idle,
        )
    }

    @Test
    fun filterDetectionsWithStep0ReturnsAll() {
        val detections =
            listOf(
                createTestTrack(0.1f, "VeryLow"),
                createTestTrack(0.3f, "Low"),
                createTestTrack(0.6f, "Medium"),
                createTestTrack(0.9f, "High"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 0)

        assertThat(filtered).hasSize(4)
        assertThat(filtered.map { it.label }).containsExactly("VeryLow", "Low", "Medium", "High")
    }

    @Test
    fun filterDetectionsWithStep1FiltersBelow25Percent() {
        val detections =
            listOf(
                createTestTrack(0.1f, "VeryLow"),
                createTestTrack(0.24f, "JustBelow"),
                createTestTrack(0.25f, "AtThreshold"),
                createTestTrack(0.3f, "Low"),
                createTestTrack(0.6f, "Medium"),
                createTestTrack(0.9f, "High"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 1)

        assertThat(filtered).hasSize(4)
        assertThat(filtered.map { it.label }).containsExactly("AtThreshold", "Low", "Medium", "High")
    }

    @Test
    fun filterDetectionsWithStep2FiltersBelow50Percent() {
        val detections =
            listOf(
                createTestTrack(0.1f, "VeryLow"),
                createTestTrack(0.3f, "Low"),
                createTestTrack(0.49f, "JustBelow"),
                createTestTrack(0.50f, "AtThreshold"),
                createTestTrack(0.6f, "Medium"),
                createTestTrack(0.9f, "High"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 2)

        assertThat(filtered).hasSize(3)
        assertThat(filtered.map { it.label }).containsExactly("AtThreshold", "Medium", "High")
    }

    @Test
    fun filterDetectionsWithStep3FiltersBelow75Percent() {
        val detections =
            listOf(
                createTestTrack(0.1f, "VeryLow"),
                createTestTrack(0.3f, "Low"),
                createTestTrack(0.6f, "Medium"),
                createTestTrack(0.74f, "JustBelow"),
                createTestTrack(0.75f, "AtThreshold"),
                createTestTrack(0.9f, "High"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 3)

        assertThat(filtered).hasSize(2)
        assertThat(filtered.map { it.label }).containsExactly("AtThreshold", "High")
    }

    @Test
    fun filterDetectionsWithEmptyListReturnsEmpty() {
        val filtered = ConfidenceTiers.filterDetections(emptyList(), 2)
        assertThat(filtered).isEmpty()
    }

    @Test
    fun filterDetectionsWithAllBelowThresholdReturnsEmpty() {
        val detections =
            listOf(
                createTestTrack(0.1f, "VeryLow"),
                createTestTrack(0.2f, "Low"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 3)
        assertThat(filtered).isEmpty()
    }

    @Test
    fun filterDetectionsWithBoundaryValuesIncludesExactMatches() {
        // Edge case: confidence exactly at threshold should be included
        val detections =
            listOf(
                createTestTrack(0.50f, "ExactlyAtMedium"),
            )

        val filtered = ConfidenceTiers.filterDetections(detections, 2) // Medium+ = >=50%

        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].label).isEqualTo("ExactlyAtMedium")
    }
}
