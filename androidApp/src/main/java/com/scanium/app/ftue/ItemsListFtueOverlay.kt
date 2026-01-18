package com.scanium.app.ftue

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.scanium.app.R

/**
 * Items List FTUE overlay composable for showing gesture hints.
 *
 * Supports multiple hint types:
 * - Tap hint: Highlight first item row with pulse
 * - Swipe hint: Highlight first item with nudge animation
 * - Long-press hint: Brief hold indicator on first item
 * - Share goal hint: Highlight bottom action area
 *
 * Features:
 * - Dim background with spotlight hole around target area
 * - Tooltip bubble with short instruction text
 * - Subtle animations (pulse, nudge)
 * - Tap-to-dismiss (tap outside spotlight area)
 */
@Composable
private fun ItemsListFtueOverlayContent(
    isVisible: Boolean,
    hintType: ListHintType,
    targetItemRect: Rect? = null,
    actionAreaRect: Rect? = null,
    onDismiss: () -> Unit,
    swipeNudgeProgress: Float = 0f,
) {
    if (!isVisible) return

    val dimColor = Color.Black.copy(alpha = 0.5f)
    val spotlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    // Tooltip text based on hint type
    val tooltipText = when (hintType) {
        ListHintType.TAP_EDIT -> stringResource(R.string.ftue_list_tap_edit)
        ListHintType.SWIPE_DELETE -> stringResource(R.string.ftue_list_swipe_delete)
        ListHintType.LONG_PRESS_SELECT -> stringResource(R.string.ftue_list_long_press_select)
        ListHintType.SHARE_SELL -> stringResource(R.string.ftue_list_share_sell)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dimColor)
            .zIndex(999f)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            )
    ) {
        // Draw spotlight hole and animated border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    when (hintType) {
                        ListHintType.TAP_EDIT,
                        ListHintType.SWIPE_DELETE,
                        ListHintType.LONG_PRESS_SELECT,
                        -> drawItemSpotlight(
                            itemRect = targetItemRect,
                            spotlightColor = spotlightColor,
                            nudgeProgress = if (hintType == ListHintType.SWIPE_DELETE) swipeNudgeProgress else 0f,
                        )

                        ListHintType.SHARE_SELL -> drawActionAreaSpotlight(
                            actionRect = actionAreaRect,
                            spotlightColor = spotlightColor,
                        )
                    }
                }
        )

        // Tooltip bubble
        TooltipBubbleList(
            text = tooltipText,
            hintType = hintType,
            targetItemRect = targetItemRect,
            actionAreaRect = actionAreaRect,
        )
    }
}

@Composable
private fun TooltipBubbleList(
    text: String,
    hintType: ListHintType,
    targetItemRect: Rect?,
    actionAreaRect: Rect?,
) {
    // Determine tooltip position
    val tooltipPosition = when (hintType) {
        ListHintType.TAP_EDIT,
        ListHintType.SWIPE_DELETE,
        ListHintType.LONG_PRESS_SELECT,
        -> targetItemRect?.let {
            // Position above item
            Offset(it.center.x, it.top - 80f)
        } ?: return

        ListHintType.SHARE_SELL -> actionAreaRect?.let {
            // Position above action area
            Offset(it.center.x, it.top - 60f)
        } ?: return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
            .offset(
                x = (tooltipPosition.x).dp,
                y = (tooltipPosition.y).dp,
            ),
    ) {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .wrapContentHeight()
                .offset(x = -110.dp),  // Center horizontally
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawItemSpotlight(
    itemRect: Rect?,
    spotlightColor: Color,
    nudgeProgress: Float = 0f,
) {
    itemRect ?: return

    val padding = 8f
    val offsetX = nudgeProgress * itemRect.width  // Move right based on progress

    // Draw spotlight border around item (with nudge offset for swipe hint)
    drawRect(
        color = spotlightColor,
        topLeft = itemRect.topLeft.copy(
            x = itemRect.topLeft.x - padding + offsetX,
            y = itemRect.topLeft.y - padding,
        ),
        size = itemRect.size.copy(
            width = itemRect.size.width,
            height = itemRect.size.height,
        ),
        style = Stroke(width = 2.5f),
    )

    // Draw outer glow
    drawRect(
        color = spotlightColor.copy(alpha = 0.3f),
        topLeft = itemRect.topLeft.copy(
            x = itemRect.topLeft.x - padding * 2 + offsetX,
            y = itemRect.topLeft.y - padding * 2,
        ),
        size = itemRect.size.copy(
            width = itemRect.size.width + padding * 2,
            height = itemRect.size.height + padding * 2,
        ),
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawActionAreaSpotlight(
    actionRect: Rect?,
    spotlightColor: Color,
) {
    actionRect ?: return

    val padding = 12f

    // Draw spotlight border around action area
    drawRect(
        color = spotlightColor,
        topLeft = actionRect.topLeft.copy(
            x = actionRect.topLeft.x - padding,
            y = actionRect.topLeft.y - padding,
        ),
        size = actionRect.size.copy(
            width = actionRect.size.width + padding * 2,
            height = actionRect.size.height + padding * 2,
        ),
        style = Stroke(width = 2.5f),
    )

    // Draw outer glow
    drawRect(
        color = spotlightColor.copy(alpha = 0.3f),
        topLeft = actionRect.topLeft.copy(
            x = actionRect.topLeft.x - padding * 2,
            y = actionRect.topLeft.y - padding * 2,
        ),
        size = actionRect.size.copy(
            width = actionRect.size.width + padding * 4,
            height = actionRect.size.height + padding * 4,
        ),
        style = Stroke(width = 1.5f),
    )
}

enum class ListHintType {
    TAP_EDIT,
    SWIPE_DELETE,
    LONG_PRESS_SELECT,
    SHARE_SELL,
}

/**
 * Extension function to smoothly animate hint visibility and position.
 * Applies fade transitions for a polished appearance.
 */
@Composable
fun ItemsListFtueOverlay(
    isVisible: Boolean,
    hintType: ListHintType,
    targetItemRect: Rect? = null,
    actionAreaRect: Rect? = null,
    onDismiss: () -> Unit,
    swipeNudgeProgress: Float = 0f,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ftue_items_list_overlay_alpha",
    )

    if (alpha > 0f) {
        Box(modifier = Modifier.alpha(alpha)) {
            ItemsListFtueOverlayContent(
                isVisible = true,
                hintType = hintType,
                targetItemRect = targetItemRect,
                actionAreaRect = actionAreaRect,
                onDismiss = onDismiss,
                swipeNudgeProgress = swipeNudgeProgress,
            )
        }
    }
}
