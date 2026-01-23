package com.scanium.app.ftue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.scanium.app.R

/**
 * Edit Item FTUE overlay composable for showing field improvement hints.
 *
 * Supports multiple hint types:
 * - Improve details: Highlight first editable field
 * - Condition/Price: Highlight condition dropdown and price field
 *
 * Features:
 * - Dim background with spotlight hole around target field
 * - Tooltip bubble with short instruction text
 * - Tap-to-dismiss
 */
@Composable
private fun EditItemFtueOverlayContent(
    isVisible: Boolean,
    hintType: EditHintType,
    targetFieldRect: Rect? = null,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val dimColor = Color.Black.copy(alpha = 0.5f)
    val spotlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    // Tooltip text based on hint type
    val tooltipText =
        when (hintType) {
            EditHintType.IMPROVE_DETAILS -> stringResource(R.string.ftue_edit_improve_details)
            EditHintType.CONDITION_PRICE -> stringResource(R.string.ftue_edit_condition_price)
            EditHintType.USE_AI -> stringResource(R.string.ftue_edit_use_ai)
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
        // Draw spotlight hole and border
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawFieldSpotlight(
                            fieldRect = targetFieldRect,
                            spotlightColor = spotlightColor,
                        )
                    },
        )

        // Tooltip bubble
        TooltipBubbleEdit(
            text = tooltipText,
            targetFieldRect = targetFieldRect,
        )
    }
}

@Composable
private fun TooltipBubbleEdit(
    text: String,
    targetFieldRect: Rect?,
) {
    targetFieldRect ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidth = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }

        Surface(
            modifier =
                Modifier
                    .width(260.dp)
                    .wrapContentHeight()
                    .offset {
                        // Calculate position: center horizontally on target, position above it
                        val tooltipWidth = 260.dp.toPx()
                        val x = (targetFieldRect.center.x - tooltipWidth / 2)
                            .coerceIn(16.dp.toPx(), containerWidth - tooltipWidth - 16.dp.toPx())

                        // Position tooltip above target with some spacing
                        val y = (targetFieldRect.top - 80.dp.toPx())
                            .coerceAtLeast(16.dp.toPx())

                        androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                    },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
        ) {
            Text(
                text = text,
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFieldSpotlight(
    fieldRect: Rect?,
    spotlightColor: Color,
) {
    fieldRect ?: return

    val padding = 8f

    // Draw spotlight border around field
    drawRect(
        color = spotlightColor,
        topLeft =
            fieldRect.topLeft.copy(
                x = fieldRect.topLeft.x - padding,
                y = fieldRect.topLeft.y - padding,
            ),
        size =
            fieldRect.size.copy(
                width = fieldRect.size.width + padding * 2,
                height = fieldRect.size.height + padding * 2,
            ),
        style = Stroke(width = 2.5f),
    )

    // Draw outer glow
    drawRect(
        color = spotlightColor.copy(alpha = 0.3f),
        topLeft =
            fieldRect.topLeft.copy(
                x = fieldRect.topLeft.x - padding * 2,
                y = fieldRect.topLeft.y - padding * 2,
            ),
        size =
            fieldRect.size.copy(
                width = fieldRect.size.width + padding * 4,
                height = fieldRect.size.height + padding * 4,
            ),
        style = Stroke(width = 1.5f),
    )
}

enum class EditHintType {
    IMPROVE_DETAILS,
    CONDITION_PRICE,
    USE_AI,
}

/**
 * Extension function to smoothly animate hint visibility.
 * Applies fade transitions for a polished appearance.
 */
@Composable
fun EditItemFtueOverlay(
    isVisible: Boolean,
    hintType: EditHintType,
    targetFieldRect: Rect? = null,
    onDismiss: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ftue_edit_overlay_alpha",
    )

    if (alpha > 0f) {
        Box(modifier = Modifier.alpha(alpha)) {
            EditItemFtueOverlayContent(
                isVisible = true,
                hintType = hintType,
                targetFieldRect = targetFieldRect,
                onDismiss = onDismiss,
            )
        }
    }
}
