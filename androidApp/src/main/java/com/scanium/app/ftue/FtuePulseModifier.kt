package com.scanium.app.ftue

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a pulsating scale animation to the composable when enabled.
 *
 * This creates a visible "breathing" effect on buttons during FTUE:
 * - Scale animates from 1.0 → 1.08 → 1.0
 * - Duration: 900ms
 * - Infinite repeat
 *
 * When disabled, the modifier has no effect.
 *
 * @param enabled If true, applies the pulse animation. If false, no animation.
 * @return Modifier with optional pulse animation
 */
fun Modifier.ftuePulse(enabled: Boolean): Modifier =
    composed {
        if (!enabled) {
            this
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "ftuePulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.08f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseScale",
            )

            this.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        }
    }
