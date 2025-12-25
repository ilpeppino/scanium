package com.scanium.app.ftue

import android.util.Log
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig
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
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Get the system bar insets to compensate for coordinate space mismatch.
    // Target bounds are captured in window coordinates (boundsInWindow()),
    // but this overlay uses windowInsetsPadding which shifts content by system bar sizes.
    // We must subtract these insets from target coordinates.
    val statusBarHeightPx = with(density) {
        WindowInsets.systemBars.getTop(this).toFloat()
    }
    val leftInsetPx = with(density) {
        WindowInsets.systemBars.getLeft(this, layoutDirection).toFloat()
    }

    // Adjust bounds from window coordinates to overlay's coordinate space
    val adjustedBounds = remember(targetBounds, statusBarHeightPx, leftInsetPx) {
        targetBounds?.let { bounds ->
            Rect(
                left = bounds.left - leftInsetPx,
                top = bounds.top - statusBarHeightPx,
                right = bounds.right - leftInsetPx,
                bottom = bounds.bottom - statusBarHeightPx
            )
        }
    }

    // Debug logging (only in debug builds)
    if (BuildConfig.DEBUG && targetBounds != null) {
        Log.d("SpotlightOverlay", "Original bounds: $targetBounds")
        Log.d("SpotlightOverlay", "Status bar height: $statusBarHeightPx, left inset: $leftInsetPx")
        Log.d("SpotlightOverlay", "Adjusted bounds: $adjustedBounds")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Spotlight scrim with cutout
        SpotlightScrim(
            targetBounds = adjustedBounds,
            spotlightShape = step.spotlightShape,
            requiresUserAction = step.requiresUserAction
        )

        // Tooltip bubble
        TooltipBubble(
            step = step,
            targetBounds = adjustedBounds,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip
        )
    }
}

/**
 * Renders the dimmed scrim with a transparent spotlight cutout.
 *
 * BlendMode.Clear requires the Canvas to render to an offscreen buffer.
 * We achieve this by adding graphicsLayer { alpha = 0.99f } which forces
 * Compose to use a separate compositing layer. Without this, BlendMode.Clear
 * would clear to black (the window background) instead of transparent.
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
            // CRITICAL: graphicsLayer with alpha < 1.0 forces offscreen compositing.
            // This is required for BlendMode.Clear to work correctly.
            // Without this, Clear would clear to the window background (black)
            // instead of making a truly transparent hole.
            .graphicsLayer { alpha = 0.99f }
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

                    // Debug: draw outline around cutout (DEBUG builds only)
                    if (BuildConfig.DEBUG) {
                        drawCircle(
                            color = Color.Red,
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                SpotlightShape.ROUNDED_RECT -> {
                    val cutoutTopLeft = Offset(
                        bounds.left - spotlightPadding,
                        bounds.top - spotlightPadding
                    )
                    val cutoutSize = Size(
                        bounds.width + spotlightPadding * 2,
                        bounds.height + spotlightPadding * 2
                    )
                    val cornerRadius = CornerRadius(16.dp.toPx())

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = cutoutTopLeft,
                        size = cutoutSize,
                        cornerRadius = cornerRadius,
                        blendMode = BlendMode.Clear
                    )

                    // Debug: draw outline around cutout (DEBUG builds only)
                    if (BuildConfig.DEBUG) {
                        drawRoundRect(
                            color = Color.Red,
                            topLeft = cutoutTopLeft,
                            size = cutoutSize,
                            cornerRadius = cornerRadius,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
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
