package com.scanium.app.ui.motion

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay

/**
 * Animated price display that counts up from 0 to the target value.
 *
 * Brand motion rules:
 * - 1.0-1.5s total duration
 * - 3-4 discrete steps (not continuous)
 * - Linear or ease-out progression
 * - Must remain stable once finished
 * - No bouncing
 *
 * @param targetValue The final price value to display
 * @param stableKey Unique key that identifies this price instance.
 *                  Animation only runs ONCE per key. Changing the key restarts animation.
 * @param currencySymbol Currency symbol to display (default: €)
 * @param textStyle Style for the price text
 * @param textColor Color for the price text
 * @param modifier Modifier for the text
 * @param onCountUpComplete Callback when animation finishes
 */
@Composable
fun PriceCountUp(
    targetValue: Int,
    stableKey: String,
    currencySymbol: String = "€",
    textStyle: TextStyle = LocalTextStyle.current,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onCountUpComplete: (() -> Unit)? = null,
) {
    if (!MotionConfig.isMotionOverlaysEnabled) {
        // Motion disabled - show static value
        Text(
            text = PriceCountUpUtil.formatPrice(targetValue, currencySymbol),
            style = textStyle,
            color = textColor,
            modifier = modifier,
        )
        return
    }

    // Track displayed value
    var displayedValue by remember(stableKey) { mutableIntStateOf(0) }

    // Track if animation has completed for this key
    var animationComplete by remember(stableKey) { mutableStateOf(false) }

    // Generate steps only once per stableKey
    val steps =
        remember(stableKey, targetValue) {
            PriceCountUpUtil.generateSteps(targetValue)
        }

    LaunchedEffect(stableKey, targetValue) {
        if (animationComplete) return@LaunchedEffect

        // Animate through steps
        steps.forEach { step ->
            delay(step.delayMs - (if (step.delayMs > 0) steps[steps.indexOf(step) - 1].delayMs else 0))
            displayedValue = step.value
        }

        animationComplete = true
        onCountUpComplete?.invoke()
    }

    Text(
        text =
            PriceCountUpUtil.formatPrice(
                if (animationComplete) targetValue else displayedValue,
                currencySymbol,
            ),
        style = textStyle,
        color = textColor,
        modifier = modifier,
    )
}

/**
 * Animated price range display that counts up both low and high values.
 *
 * @param lowValue The low end of the price range
 * @param highValue The high end of the price range
 * @param stableKey Unique key that identifies this price instance
 * @param currencySymbol Currency symbol to display (default: €)
 * @param textStyle Style for the price text
 * @param textColor Color for the price text
 * @param modifier Modifier for the row container
 * @param onCountUpComplete Callback when animation finishes
 */
@Composable
fun PriceRangeCountUp(
    lowValue: Int,
    highValue: Int,
    stableKey: String,
    currencySymbol: String = "€",
    textStyle: TextStyle = LocalTextStyle.current,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onCountUpComplete: (() -> Unit)? = null,
) {
    if (!MotionConfig.isMotionOverlaysEnabled) {
        // Motion disabled - show static value
        Text(
            text = PriceCountUpUtil.formatPriceRange(lowValue, highValue, currencySymbol),
            style = textStyle,
            color = textColor,
            modifier = modifier,
        )
        return
    }

    // Track displayed values
    var displayedLow by remember(stableKey) { mutableIntStateOf(0) }
    var displayedHigh by remember(stableKey) { mutableIntStateOf(0) }

    // Track if animation has completed
    var animationComplete by remember(stableKey) { mutableStateOf(false) }

    // Generate steps for both values
    val (lowSteps, highSteps) =
        remember(stableKey, lowValue, highValue) {
            PriceCountUpUtil.generateRangeSteps(lowValue, highValue)
        }

    LaunchedEffect(stableKey, lowValue, highValue) {
        if (animationComplete) return@LaunchedEffect

        // Animate both values in parallel by stepping through time
        val allDelays =
            (lowSteps.map { it.delayMs } + highSteps.map { it.delayMs })
                .distinct()
                .sorted()

        var lastDelay = 0L
        for (targetDelay in allDelays) {
            delay(targetDelay - lastDelay)
            lastDelay = targetDelay

            // Update low value
            lowSteps.lastOrNull { it.delayMs <= targetDelay }?.let {
                displayedLow = it.value
            }

            // Update high value
            highSteps.lastOrNull { it.delayMs <= targetDelay }?.let {
                displayedHigh = it.value
            }
        }

        // Ensure final values are set
        displayedLow = lowValue
        displayedHigh = highValue
        animationComplete = true
        onCountUpComplete?.invoke()
    }

    Row(modifier = modifier) {
        Text(
            text = "$currencySymbol${if (animationComplete) lowValue else displayedLow}",
            style = textStyle,
            color = textColor,
        )
        Text(
            text = "–",
            style = textStyle,
            color = textColor,
        )
        Text(
            text = "$currencySymbol${if (animationComplete) highValue else displayedHigh}",
            style = textStyle,
            color = textColor,
        )
    }
}

/**
 * Parse a formatted price range string and animate it.
 *
 * Handles formats like "€10–€25" or "€10–25"
 *
 * @param priceText The formatted price range string (e.g., "€10–25")
 * @param stableKey Unique key for animation state
 * @param textStyle Style for the text
 * @param textColor Color for the text
 * @param modifier Modifier
 * @param onCountUpComplete Callback when complete
 */
@Composable
fun AnimatedPriceText(
    priceText: String,
    stableKey: String,
    textStyle: TextStyle = LocalTextStyle.current,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onCountUpComplete: (() -> Unit)? = null,
) {
    if (!MotionConfig.isMotionOverlaysEnabled || priceText.isBlank()) {
        Text(
            text = priceText,
            style = textStyle,
            color = textColor,
            modifier = modifier,
        )
        return
    }

    // Try to parse the price range
    val parsedRange = remember(priceText) { parsePriceRange(priceText) }

    if (parsedRange != null) {
        val (low, high, symbol) = parsedRange
        PriceRangeCountUp(
            lowValue = low,
            highValue = high,
            stableKey = stableKey,
            currencySymbol = symbol,
            textStyle = textStyle,
            textColor = textColor,
            modifier = modifier,
            onCountUpComplete = onCountUpComplete,
        )
    } else {
        // Couldn't parse - just show static text
        Text(
            text = priceText,
            style = textStyle,
            color = textColor,
            modifier = modifier,
        )
    }
}

/**
 * Parse a price range string into (low, high, currencySymbol).
 *
 * Handles formats:
 * - "€10–€25" -> (10, 25, "€")
 * - "€10–25" -> (10, 25, "€")
 * - "$10–$25" -> (10, 25, "$")
 */
private fun parsePriceRange(text: String): Triple<Int, Int, String>? {
    // Common currency symbols
    val currencyPattern = """([€$£¥])"""
    val numberPattern = """\d+"""

    // Try to find currency symbol
    val currencyMatch = Regex(currencyPattern).find(text)
    val currencySymbol = currencyMatch?.value ?: "€"

    // Find all numbers
    val numbers =
        Regex(numberPattern)
            .findAll(text)
            .map { it.value.toIntOrNull() }
            .filterNotNull()
            .toList()

    return when {
        numbers.size >= 2 -> Triple(numbers[0], numbers[1], currencySymbol)
        numbers.size == 1 -> Triple(numbers[0], numbers[0], currencySymbol)
        else -> null
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PriceCountUpPreview() {
    PriceCountUp(
        targetValue = 25,
        stableKey = "preview-1",
        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PriceRangeCountUpPreview() {
    PriceRangeCountUp(
        lowValue = 10,
        highValue = 25,
        stableKey = "preview-2",
        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
    )
}
