package com.scanium.app.camera

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android-style camera shutter button with tap/long-press gestures.
 *
 * Behavior:
 * - Tap (quick press): Single frame capture
 * - Long press: Start scanning mode (continues after finger lifts)
 * - Tap while scanning: Stop scanning
 *
 * @param cameraState Current camera state (IDLE, CAPTURING, SCANNING)
 * @param onTap Callback for single tap (capture)
 * @param onLongPress Callback for long press (start scanning)
 * @param onStopScanning Callback for stopping scanning
 * @param modifier Modifier for positioning
 */
@Composable
fun ShutterButton(
    cameraState: CameraState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    showHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }

    // Pulsing animation when scanning
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_pulse")
    val scanningPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )

    // Press animation
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "press_scale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHint && cameraState == CameraState.IDLE) {
            Text(
                text = "Tap to capture, hold to scan",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier =
                    Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small,
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Scanning indicator text
        if (cameraState == CameraState.SCANNING) {
            Text(
                text = "Scanning...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontSize = 14.sp,
                modifier =
                    Modifier
                        .padding(bottom = 4.dp),
            )
        }

        // Shutter button
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription =
                            when (cameraState) {
                                CameraState.IDLE -> "Capture button. Double tap to take photo. Long press to scan continuously"
                                CameraState.SCANNING -> "Stop scanning. Double tap to stop"
                                CameraState.CAPTURING -> "Processing capture"
                                CameraState.ERROR -> "Camera error"
                            }
                    }.pointerInput(cameraState) {
                        detectTapGestures(
                            onPress = {
                                // Only handle press for IDLE and SCANNING states
                                if (cameraState == CameraState.IDLE || cameraState == CameraState.SCANNING) {
                                    isPressed = true
                                    longPressTriggered = false

                                    // Start long press timer (500ms threshold)
                                    val longPressJob =
                                        scope.launch {
                                            delay(500)
                                            if (isPressed && cameraState == CameraState.IDLE) {
                                                longPressTriggered = true
                                                onLongPress()
                                            }
                                        }

                                    // Wait for release
                                    tryAwaitRelease()
                                    longPressJob.cancel()
                                    isPressed = false

                                    // Handle tap if long press wasn't triggered
                                    if (!longPressTriggered) {
                                        if (cameraState == CameraState.SCANNING) {
                                            onStopScanning()
                                        } else if (cameraState == CameraState.IDLE) {
                                            onTap()
                                        }
                                    }
                                }
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val center = Offset(centerX, centerY)

                val outerRadius = size.minDimension / 2
                val innerRadius = outerRadius * 0.75f

                val scale = if (cameraState == CameraState.SCANNING) scanningPulse else pressScale

                // Outer ring
                drawCircle(
                    color = if (cameraState == CameraState.SCANNING) Color(0xFFFF4444) else Color.White,
                    radius = outerRadius * scale,
                    center = center,
                    style = Stroke(width = 4.dp.toPx()),
                )

                // Inner circle
                drawCircle(
                    color =
                        if (cameraState == CameraState.SCANNING) {
                            Color(0xFFFF4444).copy(alpha = 0.8f)
                        } else {
                            Color.White.copy(alpha = if (isPressed) 0.9f else 1f)
                        },
                    radius = innerRadius * scale,
                    center = center,
                )

                // Scanning indicator (red dot in center)
                if (cameraState == CameraState.SCANNING) {
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = center,
                    )
                }
            }
        }
    }
}
