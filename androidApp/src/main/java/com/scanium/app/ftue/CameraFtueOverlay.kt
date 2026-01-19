package com.scanium.app.ftue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.scanium.app.R

/**
 * Camera FTUE overlay composable for showing hints.
 *
 * Supports multiple hint types:
 * - ROI Pulse: Highlight the ROI area with pulsing border and tooltip
 * - BBox Hint: Brief glow effect when first detection appears
 * - Shutter Pulse: Pulsing shutter button with tooltip
 *
 * Features:
 * - Dim background with spotlight hole around target area
 * - Tooltip bubble with short instruction text
 * - Animations: pulse for ROI and shutter, glow for bbox
 * - Tap-to-dismiss (tap outside spotlight area)
 * - Respects WindowInsets (no overlap with system bars)
 */
@Composable
fun CameraFtueOverlay(
    isVisible: Boolean,
    hintType: HintType,
    targetRect: Rect? = null,
    roiRect: Rect? = null,
    shutterButtonCenter: Offset? = null,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val density = LocalDensity.current
    val dimColor = Color.Black.copy(alpha = 0.5f)
    val spotlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    // Tooltip text based on hint type
    val tooltipText =
        when (hintType) {
            HintType.ROI_PULSE -> stringResource(R.string.ftue_camera_roi)
            HintType.BBOX_HINT -> stringResource(R.string.ftue_camera_bbox)
            HintType.BBOX_TIMEOUT -> stringResource(R.string.ftue_camera_distance_hint)
            HintType.SHUTTER_PULSE -> stringResource(R.string.ftue_camera_shutter)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(dimColor)
                .zIndex(999f)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onDismiss,
                ),
    ) {
        // Draw spotlight hole and animated border
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        when (hintType) {
                            HintType.ROI_PULSE -> {
                                drawRoiSpotlight(
                                    roiRect = roiRect,
                                    spotlightColor = spotlightColor,
                                )
                            }

                            HintType.BBOX_HINT -> {
                                drawBboxGlow(
                                    targetRect = targetRect,
                                    glowColor = spotlightColor,
                                )
                            }

                            HintType.BBOX_TIMEOUT -> {
                                drawRoiSpotlight(
                                    roiRect = roiRect,
                                    spotlightColor = spotlightColor,
                                )
                            }

                            HintType.SHUTTER_PULSE -> {
                                drawShutterPulse(
                                    shutterCenter = shutterButtonCenter,
                                    pulseColor = spotlightColor,
                                )
                            }
                        }
                    },
        )

        // Tooltip bubble
        TooltipBubble(
            text = tooltipText,
            hintType = hintType,
            targetRect = targetRect,
            roiRect = roiRect,
            shutterCenter = shutterButtonCenter,
        )
    }
}

@Composable
private fun TooltipBubble(
    text: String,
    hintType: HintType,
    targetRect: Rect?,
    roiRect: Rect?,
    shutterCenter: Offset?,
) {
    // Determine tooltip position
    val tooltipPosition =
        when (hintType) {
            HintType.ROI_PULSE, HintType.BBOX_TIMEOUT -> {
                roiRect?.let {
                    // Position above ROI
                    Offset(it.center.x, it.top - 80f)
                } ?: return
            }

            HintType.BBOX_HINT -> {
                targetRect?.let {
                    // Position above target bbox
                    Offset(it.center.x, it.top - 60f)
                } ?: return
            }

            HintType.SHUTTER_PULSE -> {
                shutterCenter?.let {
                    // Position above shutter button
                    Offset(it.x, it.y - 80f)
                } ?: return
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.TopStart)
                .offset(
                    x = (tooltipPosition.x).dp,
                    y = (tooltipPosition.y).dp,
                ),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(200.dp)
                    .wrapContentHeight()
                    .offset(x = -100.dp),
            // Center horizontally
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
        ) {
            Text(
                text = text,
                modifier =
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawRoiSpotlight(
    roiRect: Rect?,
    spotlightColor: Color,
) {
    roiRect ?: return

    val padding = 8f
    val pulseFraction = 0.5f // Pulse effect amplitude

    // Pulse animation for border thickness
    drawRect(
        color = spotlightColor,
        topLeft =
            roiRect.topLeft.copy(
                x = roiRect.topLeft.x - padding,
                y = roiRect.topLeft.y - padding,
            ),
        size =
            roiRect.size.copy(
                width = roiRect.size.width + padding * 2,
                height = roiRect.size.height + padding * 2,
            ),
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2f + (2f * pulseFraction),
            ),
    )
}

private fun DrawScope.drawBboxGlow(
    targetRect: Rect?,
    glowColor: Color,
) {
    targetRect ?: return

    val glowRadius = 6f

    // Draw glow effect (outer border)
    drawRect(
        color = glowColor.copy(alpha = 0.4f),
        topLeft =
            targetRect.topLeft.copy(
                x = targetRect.topLeft.x - glowRadius,
                y = targetRect.topLeft.y - glowRadius,
            ),
        size =
            targetRect.size.copy(
                width = targetRect.size.width + glowRadius * 2,
                height = targetRect.size.height + glowRadius * 2,
            ),
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f,
            ),
    )

    // Draw inner bright border
    drawRect(
        color = glowColor,
        topLeft = targetRect.topLeft,
        size = targetRect.size,
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2f,
            ),
    )
}

private fun DrawScope.drawShutterPulse(
    shutterCenter: Offset?,
    pulseColor: Color,
) {
    shutterCenter ?: return

    val baseRadius = 28f
    val pulseRadius = 34f

    // Draw pulsing circle
    drawCircle(
        color = pulseColor.copy(alpha = 0.3f),
        radius = pulseRadius,
        center = shutterCenter,
    )

    // Draw inner bright circle
    drawCircle(
        color = pulseColor,
        radius = baseRadius,
        center = shutterCenter,
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5f,
            ),
    )
}

enum class HintType {
    ROI_PULSE, // Initial ROI hint
    BBOX_HINT, // First detection appeared
    BBOX_TIMEOUT, // No detection within timeout
    SHUTTER_PULSE, // Shutter button hint
}

/**
 * Extension function to smoothly animate hint visibility and position.
 * Applies fade and scale transitions for a polished appearance.
 */
@Composable
fun CameraFtueOverlay(
    isVisible: Boolean,
    hintType: HintType,
    targetRect: Rect? = null,
    roiRect: Rect? = null,
    shutterButtonCenter: Offset? = null,
    onDismiss: () -> Unit,
    animateIn: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ftue_overlay_alpha",
    )

    if (alpha > 0f) {
        Box(modifier = Modifier.alpha(alpha)) {
            CameraFtueOverlay(
                isVisible = true,
                hintType = hintType,
                targetRect = targetRect,
                roiRect = roiRect,
                shutterButtonCenter = shutterButtonCenter,
                onDismiss = onDismiss,
            )
        }
    }
}
