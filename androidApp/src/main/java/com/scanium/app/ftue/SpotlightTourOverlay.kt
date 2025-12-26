package com.scanium.app.ftue

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
    // We use BoxWithConstraints to get the full screen size for clamping calculations.
    // The overlay is expected to be full-screen (edge-to-edge).
    // We do NOT apply windowInsetsPadding here, as targetBounds are captured in Window coordinates
    // (via boundsInWindow()), so we want our coordinate system to match the Window exactly.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidth = constraints.maxWidth
        val screenHeight = constraints.maxHeight

        // Debug logging (only in debug builds)
        if (BuildConfig.DEBUG && targetBounds != null) {
            Log.d("SpotlightOverlay", "Target bounds: $targetBounds")
            Log.d("SpotlightOverlay", "Screen size: $screenWidth x $screenHeight")
            Log.d("SpotlightOverlay", "Target Center X: ${targetBounds.center.x}")
        }

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
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip
        )
        
        // Debug visualization (DEBUG only)
        if (BuildConfig.DEBUG && targetBounds != null) {
             Canvas(modifier = Modifier.fillMaxSize()) {
                 // Draw target bounds outline
                 drawRect(
                     color = Color.Green,
                     topLeft = targetBounds.topLeft,
                     size = targetBounds.size,
                     style = Stroke(width = 2.dp.toPx())
                 )
                 
                 // Draw vertical line at target center X
                 drawLine(
                     color = Color.Cyan,
                     start = Offset(targetBounds.center.x, 0f),
                     end = Offset(targetBounds.center.x, size.height),
                     strokeWidth = 2.dp.toPx()
                 )
             }
        }
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
 * @param targetBounds Bounds of the control to highlight (Window coordinates)
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
            .graphicsLayer { alpha = 0.99f }
            .pointerInput(requiresUserAction) {
                if (!requiresUserAction) {
                    // Block all pointer events when user action is not required
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        // Draw semi-transparent scrim over entire screen
        drawRect(color = Color.Black.copy(alpha = 0.7f))

        // Cut out spotlight area using BlendMode.Clear
        targetBounds?.let { bounds ->
            when (spotlightShape) {
                SpotlightShape.CIRCLE -> {
                    // Use center from bounds directly
                    val centerX = bounds.center.x
                    val centerY = bounds.center.y
                    val radius = max(bounds.width, bounds.height) / 2 + spotlightPadding

                    drawCircle(
                        color = Color.Transparent,
                        radius = radius,
                        center = Offset(centerX, centerY),
                        blendMode = BlendMode.Clear
                    )
                }

                SpotlightShape.ROUNDED_RECT -> {
                    // Expand bounds by padding symmetrically
                    val cutoutLeft = bounds.left - spotlightPadding
                    val cutoutTop = bounds.top - spotlightPadding
                    val cutoutWidth = bounds.width + spotlightPadding * 2
                    val cutoutHeight = bounds.height + spotlightPadding * 2
                    
                    val cornerRadius = CornerRadius(16.dp.toPx())

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(cutoutLeft, cutoutTop),
                        size = Size(cutoutWidth, cutoutHeight),
                        cornerRadius = cornerRadius,
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
 * @param screenWidth Width of the screen in pixels
 * @param screenHeight Height of the screen in pixels
 * @param onNext Next button callback
 * @param onBack Back button callback
 * @param onSkip Skip button callback
 */
@Composable
private fun TooltipBubble(
    step: TourStep,
    targetBounds: Rect?,
    screenWidth: Int,
    screenHeight: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .width(320.dp) // Fixed preferred width, will be constrained by screen
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = placeable.width
                val height = placeable.height

                // Horizontal positioning:
                // Anchor to target center X, then clamp within screen bounds
                val targetCenterX = targetBounds?.center?.x ?: (screenWidth / 2f)
                val desiredLeft = targetCenterX - (width / 2)
                
                val marginPx = (16 * density.density).roundToInt()
                val minLeft = marginPx.toFloat()
                val maxLeft = (screenWidth - width - marginPx).toFloat()
                
                val x = desiredLeft.coerceIn(minLeft, maxLeft)

                // Vertical positioning:
                // Based on target position relative to screen center
                val y = calculateTooltipY(targetBounds, height, screenHeight.toFloat(), density)

                layout(width, height) {
                    placeable.place(x.roundToInt(), y.roundToInt())
                }
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
 * Calculates the vertical Y position for the tooltip based on target bounds.
 * Positions tooltip below target if in top half, above if in bottom half.
 *
 * @param targetBounds Bounds of the highlighted control
 * @param tooltipHeight Measured height of the tooltip
 * @param screenHeight Total screen height
 * @param density Current density for dp to px conversion
 * @return Y position in pixels
 */
private fun calculateTooltipY(
    targetBounds: Rect?,
    tooltipHeight: Int,
    screenHeight: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    if (targetBounds == null) {
        // For full-screen overlays, center vertically
        return (screenHeight - tooltipHeight) / 2
    }

    val targetCenterY = targetBounds.center.y
    val tooltipMargin = with(density) { 16.dp.toPx() }

    return if (targetCenterY < screenHeight / 2) {
        // Target in top half, show tooltip below
        targetBounds.bottom + tooltipMargin
    } else {
        // Target in bottom half, show tooltip above
        targetBounds.top - tooltipHeight - tooltipMargin
    }
}
