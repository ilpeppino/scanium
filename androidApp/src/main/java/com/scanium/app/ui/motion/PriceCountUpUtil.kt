package com.scanium.app.ui.motion

import kotlin.math.roundToInt

/**
 * Pure Kotlin utility for generating price count-up animation steps.
 *
 * This is separated from Compose for easy unit testing.
 *
 * Animation rules:
 * - 3-4 discrete steps (not continuous)
 * - Linear or ease-out progression
 * - Total duration: 1.0-1.5s
 * - Must remain stable once finished
 */
object PriceCountUpUtil {
    /**
     * Data class representing a single step in the price count-up animation.
     *
     * @param value The displayed value at this step
     * @param delayMs Delay from animation start before showing this value
     * @param isFinal Whether this is the final (target) value
     */
    data class PriceStep(
        val value: Int,
        val delayMs: Long,
        val isFinal: Boolean,
    )

    /**
     * Generate count-up steps from 0 to [targetValue].
     *
     * Uses ease-out timing (faster at start, slower at end) to feel snappy
     * while giving users time to register the final price.
     *
     * @param targetValue The final price value to count up to
     * @param steps Number of intermediate steps (3-4 recommended)
     * @param totalDurationMs Total animation duration in milliseconds
     * @return List of [PriceStep] values with timing information
     */
    fun generateSteps(
        targetValue: Int,
        steps: Int = MotionConstants.PRICE_COUNT_UP_STEPS,
        totalDurationMs: Int = MotionConstants.PRICE_COUNT_UP_DURATION_MS,
    ): List<PriceStep> {
        require(steps >= 2) { "At least 2 steps required" }
        require(targetValue >= 0) { "Target value must be non-negative" }

        if (targetValue == 0) {
            return listOf(PriceStep(value = 0, delayMs = 0, isFinal = true))
        }

        val result = mutableListOf<PriceStep>()

        // Step 0: Always start with 0 (or a small initial value)
        result.add(PriceStep(value = 0, delayMs = 0, isFinal = false))

        // Intermediate steps with ease-out timing
        // Ease-out: t' = 1 - (1 - t)^2
        for (i in 1 until steps) {
            val linearProgress = i.toFloat() / steps
            // Ease-out function for timing (progress feels faster early)
            val easedProgress = easeOutQuad(linearProgress)

            // Calculate the displayed value at this step
            val stepValue = (targetValue * easedProgress).roundToInt()

            // Calculate delay using linear spacing for consistent rhythm
            val delayMs = (totalDurationMs * linearProgress).toLong()

            // Skip if value is same as previous (avoid duplicate steps)
            if (result.isNotEmpty() && result.last().value == stepValue) {
                continue
            }

            result.add(PriceStep(value = stepValue, delayMs = delayMs, isFinal = false))
        }

        // Final step: Always show exact target value
        val finalDelayMs = totalDurationMs.toLong()
        if (result.last().value != targetValue) {
            result.add(PriceStep(value = targetValue, delayMs = finalDelayMs, isFinal = true))
        } else {
            // Update last step to be final
            result[result.lastIndex] = result.last().copy(isFinal = true)
        }

        return result
    }

    /**
     * Generate count-up steps for a price range (low to high).
     *
     * @param lowValue The low end of the price range
     * @param highValue The high end of the price range
     * @param steps Number of intermediate steps
     * @param totalDurationMs Total animation duration
     * @return Pair of step lists: (lowSteps, highSteps)
     */
    fun generateRangeSteps(
        lowValue: Int,
        highValue: Int,
        steps: Int = MotionConstants.PRICE_COUNT_UP_STEPS,
        totalDurationMs: Int = MotionConstants.PRICE_COUNT_UP_DURATION_MS,
    ): Pair<List<PriceStep>, List<PriceStep>> =
        generateSteps(lowValue, steps, totalDurationMs) to
            generateSteps(highValue, steps, totalDurationMs)

    /**
     * Quadratic ease-out function.
     * Fast start, gradual slowdown at end.
     */
    private fun easeOutQuad(t: Float): Float = 1 - (1 - t) * (1 - t)

    /**
     * Format a price value with currency symbol.
     *
     * @param value The price value
     * @param currencySymbol Currency symbol (default: €)
     * @return Formatted price string
     */
    fun formatPrice(
        value: Int,
        currencySymbol: String = "€",
    ): String = "$currencySymbol$value"

    /**
     * Format a price range with currency symbol.
     *
     * @param low Low end of range
     * @param high High end of range
     * @param currencySymbol Currency symbol (default: €)
     * @return Formatted price range string (e.g., "€10–€25")
     */
    fun formatPriceRange(
        low: Int,
        high: Int,
        currencySymbol: String = "€",
    ): String = "$currencySymbol$low–$currencySymbol$high"
}
