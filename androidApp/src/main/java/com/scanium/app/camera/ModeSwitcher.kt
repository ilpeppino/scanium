package com.scanium.app.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Camera-style mode switcher resembling phone camera apps.
 *
 * Supports both tap and horizontal swipe gestures to switch between modes.
 * The selected mode is centered and highlighted, while other modes are dimmed.
 *
 * @param currentMode The currently selected scan mode
 * @param onModeChanged Callback when the user selects a different mode
 * @param modifier Optional modifier for the switcher container
 */
@Composable
fun ModeSwitcher(
    currentMode: ScanMode,
    onModeChanged: (ScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = ScanMode.values()
    val currentIndex = modes.indexOf(currentMode)

    // Track drag offset for swipe gestures
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val swipeThreshold = with(density) { 50.dp.toPx() }

    // Animate position when mode changes
    val targetOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "modeOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(currentMode) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false

                        // Determine if we should switch modes based on drag distance
                        when {
                            dragOffset < -swipeThreshold && currentIndex < modes.size - 1 -> {
                                // Swipe left -> next mode
                                onModeChanged(modes[currentIndex + 1])
                            }
                            dragOffset > swipeThreshold && currentIndex > 0 -> {
                                // Swipe right -> previous mode
                                onModeChanged(modes[currentIndex - 1])
                            }
                        }

                        dragOffset = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                        // Clamp drag offset to reasonable bounds
                        dragOffset = dragOffset.coerceIn(-200f, 200f)
                    }
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = with(density) { targetOffset.toDp() })
        ) {
            modes.forEach { mode ->
                CameraModeButton(
                    mode = mode,
                    isSelected = mode == currentMode,
                    onClick = { onModeChanged(mode) }
                )
            }
        }

        // Visual indicator line under selected mode
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .width(40.dp)
                .height(2.dp)
                .background(Color.White)
        )
    }
}

/**
 * Individual mode button within the camera-style switcher.
 */
@Composable
private fun CameraModeButton(
    mode: ScanMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate scale for selected state
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modeScale"
    )

    // Animate alpha for selected state
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.5f,
        animationSpec = tween(200),
        label = "modeAlpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mode.displayName.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontSize = if (isSelected) 16.sp else 14.sp,
            color = Color.White.copy(alpha = alpha),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (isSelected) 0.5.sp else 0.2.sp
        )
    }
}
