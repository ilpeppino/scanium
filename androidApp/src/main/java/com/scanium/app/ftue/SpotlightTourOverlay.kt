package com.scanium.app.ftue

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Main composable for rendering the spotlight tour overlay with tooltip.
 *
 * @param step Current tour step to display
 * @param targetBounds Bounds of the highlighted control (null for full-screen steps)
 * @param onNext Callback when "Next" button is clicked
 * @param onBack Callback when "Back" button is clicked
 * @param onSkip Callback when "Skip" button is clicked
 * @param modifier Modifier for the overlay
 */
@Composable
fun SpotlightTourOverlay(
    step: TourStep,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Spotlight scrim with cutout
        SpotlightScrim(
            targetBounds = targetBounds,
            spotlightShape = step.spotlightShape,
            requiresUserAction = step.requiresUserAction
        )

        // Tooltip bubble
        TooltipBubble(
            step = step,
            targetBounds = targetBounds,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip
        )
    }
}

/**
 * Renders the dimmed scrim with a transparent spotlight cutout.
 *
 * @param targetBounds Bounds of the control to highlight
 * @param spotlightShape Shape of the spotlight cutout
 * @param requiresUserAction If true, allows pointer events through spotlight area
 */
@Composable
private fun SpotlightScrim(
    targetBounds: Rect?,
    spotlightShape: SpotlightShape,
    requiresUserAction: Boolean
) {
    val density = LocalDensity.current

    // Padding around spotlight for visual breathing room
    val spotlightPadding = with(density) { 8.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(requiresUserAction) {
                if (!requiresUserAction) {
                    // Block all pointer events when user action is not required
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // Consume all events to block interaction
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                // If requiresUserAction is true, allow events to pass through
            }
    ) {
        // Draw semi-transparent scrim over entire screen
        drawRect(color = Color.Black.copy(alpha = 0.7f))

        // Cut out spotlight area using BlendMode.Clear
        targetBounds?.let { bounds ->
            when (spotlightShape) {
                SpotlightShape.CIRCLE -> {
                    val centerX = bounds.left + bounds.width / 2
                    val centerY = bounds.top + bounds.height / 2
                    val radius = max(bounds.width, bounds.height) / 2 + spotlightPadding

                    drawCircle(
                        color = Color.Transparent,
                        radius = radius,
                        center = Offset(centerX, centerY),
                        blendMode = BlendMode.Clear
                    )
                }

                SpotlightShape.ROUNDED_RECT -> {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(
                            bounds.left - spotlightPadding,
                            bounds.top - spotlightPadding
                        ),
                        size = Size(
                            bounds.width + spotlightPadding * 2,
                            bounds.height + spotlightPadding * 2
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }
            }
        }
    }
}

/**
 * Renders the tooltip bubble with step information and navigation controls.
 *
 * @param step Current tour step
 * @param targetBounds Bounds of the highlighted control
 * @param onNext Next button callback
 * @param onBack Back button callback
 * @param onSkip Skip button callback
 */
@Composable
private fun TooltipBubble(
    step: TourStep,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current

    // Calculate tooltip position based on target bounds
    val tooltipOffset = remember(targetBounds) {
        calculateTooltipOffset(targetBounds, density)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset {
                IntOffset(0, tooltipOffset.roundToInt())
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip button (always visible)
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }

                Spacer(Modifier.width(8.dp))

                // Back button (hidden on first step)
                if (step.key != TourStepKey.WELCOME) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Next button
                Button(onClick = onNext) {
                    Text(
                        text = when {
                            step.requiresUserAction -> "Got it"
                            step.key == TourStepKey.COMPLETION -> "Done"
                            else -> "Next"
                        }
                    )
                }
            }
        }
    }
}

/**
 * Calculates the vertical offset for the tooltip based on target bounds.
 * Positions tooltip below target if in top half, above if in bottom half.
 *
 * @param targetBounds Bounds of the highlighted control
 * @param density Current density for dp to px conversion
 * @return Vertical offset in pixels
 */
private fun calculateTooltipOffset(
    targetBounds: Rect?,
    density: androidx.compose.ui.unit.Density
): Float {
    if (targetBounds == null) {
        // For full-screen overlays, center vertically
        return 0f
    }

    val targetCenterY = targetBounds.top + targetBounds.height / 2
    val screenHeight = 2000f // Approximate screen height (will be constrained by parent)

    val tooltipMargin = with(density) { 16.dp.toPx() }

    return if (targetCenterY < screenHeight / 2) {
        // Target in top half, show tooltip below
        targetBounds.bottom + tooltipMargin
    } else {
        // Target in bottom half, show tooltip above
        // Position calculated as negative offset from target top
        targetBounds.top - with(density) { 250.dp.toPx() } - tooltipMargin
    }
}
