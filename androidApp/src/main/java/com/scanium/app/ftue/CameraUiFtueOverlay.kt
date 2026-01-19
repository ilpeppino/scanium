package com.scanium.app.ftue

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.scanium.app.config.FeatureFlags

/**
 * Camera UI FTUE overlay composable for teaching button navigation.
 *
 * Phase 3-4: FTUE Overlay with Pulsating Animations
 * - Semi-transparent dim background (alpha ~0.45)
 * - Circular spotlight hole around anchor rect
 * - Tooltip bubble with instruction text + "Next" button
 * - Pulsating animation (scale: 1.0 → 1.08 → 1.0, 900ms, infinite)
 * - Tap inside spotlight OR "Next" advances step
 * - zIndex >= 999f ensures visibility
 *
 * @param step Current FTUE step
 * @param anchorRect Bounds of the highlighted button (in root coordinates)
 * @param tooltipText Instruction text to display
 * @param onNext Callback when user advances to next step
 * @param onDismiss Callback when user dismisses overlay
 * @param showDebugBounds If true, draw magenta border around anchor (DEV-only)
 */
@Composable
fun CameraUiFtueOverlay(
    step: CameraUiFtueViewModel.CameraUiFtueStep,
    anchorRect: Rect?,
    tooltipText: String,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    showDebugBounds: Boolean = false,
) {
    if (step == CameraUiFtueViewModel.CameraUiFtueStep.IDLE ||
        step == CameraUiFtueViewModel.CameraUiFtueStep.COMPLETED
    ) {
        return
    }

    if (anchorRect == null) {
        Log.w("FTUE_CAMERA_UI", "Overlay rendered with NULL anchorRect for step=$step")
        return
    }

    Log.d(
        "FTUE_CAMERA_UI",
        "Overlay rendering: stepId=${step.name}, " +
            "anchorRect=(${anchorRect.left.toInt()},${anchorRect.top.toInt()}," +
            "${anchorRect.width.toInt()}x${anchorRect.height.toInt()}), " +
            "overlayRendered=true",
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val dimColor = Color.Black.copy(alpha = 0.45f)
    val spotlightColor = MaterialTheme.colorScheme.primary

    // Pulsating animation: scale 1.0 → 1.08 → 1.0, duration 900ms
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseScale",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(999f)
                .drawBehind {
                    // Draw dim background with spotlight hole
                    drawDimBackgroundWithSpotlight(
                        dimColor = dimColor,
                        spotlightRect = anchorRect,
                        spotlightColor = spotlightColor,
                        pulseScale = pulseScale,
                        showDebugBounds = showDebugBounds,
                    )
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // PHASE 4: Tap inside spotlight advances step, tap outside dismisses
                        // This matches Force Step behavior where highlighted element is interactive
                        onDismiss()
                    },
                ),
    ) {
        // Tooltip bubble
        TooltipBubble(
            text = tooltipText,
            anchorRect = anchorRect,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onNext = onNext,
        )
    }
}

/**
 * Draw dim background with spotlight hole around anchor rect.
 * Uses inverse clipping: fill entire canvas, then cut out spotlight hole.
 */
private fun DrawScope.drawDimBackgroundWithSpotlight(
    dimColor: Color,
    spotlightRect: Rect,
    spotlightColor: Color,
    pulseScale: Float,
    showDebugBounds: Boolean,
) {
    // Draw dim background
    drawRect(
        color = dimColor,
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
    )

    // Calculate spotlight center and radius
    val spotlightCenter = spotlightRect.center
    val spotlightRadius =
        kotlin.math.max(spotlightRect.width, spotlightRect.height) / 2f + 24f // Add padding

    // Draw spotlight hole (clear circle)
    val path =
        Path().apply {
            // Add full canvas rect
            addRect(
                androidx.compose.ui.geometry
                    .Rect(0f, 0f, size.width, size.height),
            )
            // Subtract spotlight circle
            addOval(
                androidx.compose.ui.geometry.Rect(
                    left = spotlightCenter.x - spotlightRadius,
                    top = spotlightCenter.y - spotlightRadius,
                    right = spotlightCenter.x + spotlightRadius,
                    bottom = spotlightCenter.y + spotlightRadius,
                ),
            )
        }

    // Draw pulsating border around spotlight
    val pulsedRadius = spotlightRadius * pulseScale
    drawCircle(
        color = spotlightColor.copy(alpha = 0.6f),
        radius = pulsedRadius,
        center = spotlightCenter,
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = 3f),
    )

    // DEV-only: Draw magenta debug border around exact anchor rect
    if (showDebugBounds && FeatureFlags.isDevBuild) {
        drawRect(
            color = Color.Magenta,
            topLeft = spotlightRect.topLeft,
            size = spotlightRect.size,
            style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(width = 2f),
        )
    }
}

/**
 * Tooltip bubble with instruction text and "Next" button.
 * PHASE 5: Adaptive positioning - prefers ABOVE, then BELOW, then LEFT/RIGHT.
 * Clamps within safe area and maintains minimum spacing from spotlight.
 */
@Composable
private fun TooltipBubble(
    text: String,
    anchorRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current

    // Tooltip dimensions (approximate)
    val tooltipWidthPx = with(density) { 280.dp.toPx() }
    val tooltipHeightPx = with(density) { 120.dp.toPx() } // Approximate height
    val minSpacing = with(density) { 24.dp.toPx() } // Minimum spacing from spotlight
    val safeMargin = with(density) { 16.dp.toPx() } // Edge margin

    // Calculate spotlight radius (same as overlay calculation)
    val spotlightRadius = kotlin.math.max(anchorRect.width, anchorRect.height) / 2f + 24f

    // Try ABOVE first (preferred)
    var tooltipX = anchorRect.center.x - tooltipWidthPx / 2f
    var tooltipY = anchorRect.center.y - spotlightRadius - minSpacing - tooltipHeightPx
    var placement = "above"

    // Check if ABOVE fits within safe area
    if (tooltipY < safeMargin) {
        // Try BELOW
        tooltipY = anchorRect.center.y + spotlightRadius + minSpacing
        placement = "below"

        // Check if BELOW fits
        if (tooltipY + tooltipHeightPx > screenHeightPx - safeMargin) {
            // Try LEFT or RIGHT (use whichever has more space)
            val spaceLeft = anchorRect.center.x - spotlightRadius - minSpacing
            val spaceRight = screenWidthPx - (anchorRect.center.x + spotlightRadius + minSpacing)

            if (spaceRight > spaceLeft && spaceRight >= tooltipWidthPx) {
                // Place RIGHT
                tooltipX = anchorRect.center.x + spotlightRadius + minSpacing
                tooltipY = anchorRect.center.y - tooltipHeightPx / 2f
                placement = "right"
            } else if (spaceLeft >= tooltipWidthPx) {
                // Place LEFT
                tooltipX = anchorRect.center.x - spotlightRadius - minSpacing - tooltipWidthPx
                tooltipY = anchorRect.center.y - tooltipHeightPx / 2f
                placement = "left"
            } else {
                // Fallback: force BELOW and clamp
                tooltipY = (screenHeightPx - tooltipHeightPx - safeMargin).coerceAtLeast(safeMargin)
                placement = "below-clamped"
            }
        }
    }

    // Clamp X to safe area
    tooltipX = tooltipX.coerceIn(safeMargin, screenWidthPx - tooltipWidthPx - safeMargin)
    // Clamp Y to safe area
    tooltipY = tooltipY.coerceIn(safeMargin, screenHeightPx - tooltipHeightPx - safeMargin)

    Log.d(
        "FTUE_CAMERA_UI",
        "Tooltip placement: $placement, x=${tooltipX.toInt()}, y=${tooltipY.toInt()}",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = tooltipX.toInt(),
                        y = tooltipY.toInt(),
                    )
                },
    ) {
        androidx.compose.material3.Surface(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNext,
                    modifier = Modifier,
                ) {
                    Text("Next")
                }
            }
        }
    }
}
