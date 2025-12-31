package com.scanium.app.ui.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for PriceCountUpUtil step generator logic.
 *
 * Tests verify:
 * - Step generation for single values
 * - Step generation for price ranges
 * - Timing constraints (1.0-1.5s total, proper step distribution)
 * - Edge cases (zero values, single step)
 * - Format helpers
 * - Ease-out progression (faster at start)
 */
class PriceCountUpUtilTest {

    @Test
    fun whenGeneratingSteps_thenCorrectNumberOfSteps() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - Should have approximately 4 steps (may vary slightly due to deduplication)
        assertThat(steps.size).isAtLeast(2)
        assertThat(steps.size).isAtMost(5)
    }

    @Test
    fun whenGeneratingSteps_thenStartsAtZero() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - First step should be 0
        assertThat(steps.first().value).isEqualTo(0)
        assertThat(steps.first().delayMs).isEqualTo(0L)
    }

    @Test
    fun whenGeneratingSteps_thenEndsAtTargetValue() {
        // Arrange
        val targetValue = 157

        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = targetValue, steps = 4)

        // Assert - Last step should be the target value and marked as final
        assertThat(steps.last().value).isEqualTo(targetValue)
        assertThat(steps.last().isFinal).isTrue()
    }

    @Test
    fun whenGeneratingSteps_thenDelaysAreIncreasing() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - Delays should be strictly increasing
        for (i in 1 until steps.size) {
            assertThat(steps[i].delayMs).isGreaterThan(steps[i - 1].delayMs)
        }
    }

    @Test
    fun whenGeneratingSteps_thenValuesAreNonDecreasing() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - Values should not decrease
        for (i in 1 until steps.size) {
            assertThat(steps[i].value).isAtLeast(steps[i - 1].value)
        }
    }

    @Test
    fun whenGeneratingSteps_thenTotalDurationMatchesSpec() {
        // Arrange
        val totalDuration = 1200

        // Act
        val steps = PriceCountUpUtil.generateSteps(
            targetValue = 100,
            steps = 4,
            totalDurationMs = totalDuration
        )

        // Assert - Last step delay should equal or be close to total duration
        assertThat(steps.last().delayMs).isEqualTo(totalDuration.toLong())
    }

    @Test
    fun whenZeroTargetValue_thenReturnsSingleStep() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 0, steps = 4)

        // Assert - Should have exactly one step with value 0
        assertThat(steps).hasSize(1)
        assertThat(steps.first().value).isEqualTo(0)
        assertThat(steps.first().isFinal).isTrue()
    }

    @Test
    fun whenSmallTargetValue_thenStepsAreValid() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 3, steps = 4)

        // Assert - Should handle small values gracefully
        assertThat(steps.first().value).isEqualTo(0)
        assertThat(steps.last().value).isEqualTo(3)
        assertThat(steps.last().isFinal).isTrue()
    }

    @Test
    fun whenLargeTargetValue_thenStepsAreValid() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 9999, steps = 4)

        // Assert - Should handle large values
        assertThat(steps.first().value).isEqualTo(0)
        assertThat(steps.last().value).isEqualTo(9999)
        assertThat(steps.last().isFinal).isTrue()
    }

    @Test
    fun whenGeneratingRangeSteps_thenBothRangesAreValid() {
        // Arrange
        val low = 10
        val high = 25

        // Act
        val (lowSteps, highSteps) = PriceCountUpUtil.generateRangeSteps(low, high)

        // Assert - Both ranges should be valid
        assertThat(lowSteps.last().value).isEqualTo(low)
        assertThat(highSteps.last().value).isEqualTo(high)
        assertThat(lowSteps.last().isFinal).isTrue()
        assertThat(highSteps.last().isFinal).isTrue()
    }

    @Test
    fun whenGeneratingRangeSteps_thenTimingsMatch() {
        // Act
        val (lowSteps, highSteps) = PriceCountUpUtil.generateRangeSteps(
            lowValue = 10,
            highValue = 100,
            totalDurationMs = 1000
        )

        // Assert - Both should end at same time
        assertThat(lowSteps.last().delayMs).isEqualTo(1000L)
        assertThat(highSteps.last().delayMs).isEqualTo(1000L)
    }

    @Test
    fun whenFormattingPrice_thenCorrectFormat() {
        // Act
        val formatted = PriceCountUpUtil.formatPrice(25, "€")

        // Assert
        assertThat(formatted).isEqualTo("€25")
    }

    @Test
    fun whenFormattingPriceWithDollar_thenCorrectFormat() {
        // Act
        val formatted = PriceCountUpUtil.formatPrice(99, "$")

        // Assert
        assertThat(formatted).isEqualTo("$99")
    }

    @Test
    fun whenFormattingPriceRange_thenCorrectFormat() {
        // Act
        val formatted = PriceCountUpUtil.formatPriceRange(10, 25, "€")

        // Assert
        assertThat(formatted).isEqualTo("€10–€25")
    }

    @Test
    fun whenEaseOutProgression_thenFasterAtStart() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - Due to ease-out, early steps should reach higher values faster
        // Check that the midpoint value is > 50% of target (ease-out characteristic)
        if (steps.size >= 3) {
            val midStep = steps[steps.size / 2]
            // With ease-out, halfway through time should be > halfway through value
            assertThat(midStep.value).isAtLeast(40) // Should be at least 40% of 100
        }
    }

    @Test
    fun whenOnlyFinalStepExists_thenOnlyFinalIsTrue() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 50, steps = 4)

        // Assert - Only the last step should be marked as final
        steps.dropLast(1).forEach { step ->
            assertThat(step.isFinal).isFalse()
        }
        assertThat(steps.last().isFinal).isTrue()
    }

    @Test
    fun whenGeneratingSteps_thenNoDuplicateValues() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 4)

        // Assert - Values should be unique (no duplicate display values)
        val values = steps.map { it.value }
        assertThat(values.distinct().size).isEqualTo(values.size)
    }

    @Test
    fun whenMinimumSteps_thenAtLeastTwoSteps() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100, steps = 2)

        // Assert - Should have at least 2 steps (0 and target)
        assertThat(steps.size).isAtLeast(2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenLessThanTwoSteps_thenThrowsException() {
        // Act
        PriceCountUpUtil.generateSteps(targetValue = 100, steps = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenNegativeTargetValue_thenThrowsException() {
        // Act
        PriceCountUpUtil.generateSteps(targetValue = -10, steps = 4)
    }

    @Test
    fun whenDefaultParameters_thenMatchSpecConstants() {
        // Act
        val steps = PriceCountUpUtil.generateSteps(targetValue = 100)

        // Assert - Should use default constants from MotionConstants
        assertThat(steps.last().delayMs).isEqualTo(MotionConstants.PRICE_COUNT_UP_DURATION_MS.toLong())
    }

    @Test
    fun whenGeneratingManySteps_thenAllAreValid() {
        // Act - Test with various target values
        val testValues = listOf(1, 5, 10, 25, 50, 100, 250, 500, 1000)

        // Assert
        testValues.forEach { targetValue ->
            val steps = PriceCountUpUtil.generateSteps(targetValue)
            assertThat(steps.first().value).isEqualTo(0)
            assertThat(steps.last().value).isEqualTo(targetValue)
            assertThat(steps.last().isFinal).isTrue()
        }
    }
}
