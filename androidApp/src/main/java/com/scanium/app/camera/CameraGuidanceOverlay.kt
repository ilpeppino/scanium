package com.scanium.app.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scanium.app.camera.detection.RoiFilterResult
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.ScaniumBlue
import com.scanium.core.models.scanning.GuidanceState
import com.scanium.core.models.scanning.ScanGuidanceState
import com.scanium.core.models.scanning.ScanRoi

/**
 * Camera guidance overlay that displays the scan zone and user hints.
 *
 * This overlay:
 * - Draws a rounded rectangle scan zone in the center
 * - Dims the area outside the scan zone
 * - Shows contextual hints based on guidance state
 * - Animates the scan zone based on state (pulse for good, static for locked)
 * - Provides visual feedback for dynamic sizing
 *
 * The scan zone corresponds directly to the ScanRoi used by the analyzer,
 * ensuring what the user sees matches what the system analyzes.
 */
@Composable
fun CameraGuidanceOverlay(
    guidanceState: ScanGuidanceState,
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = false,
    /** PHASE 6: ROI filter result for diagnostics display */
    roiFilterResult: RoiFilterResult? = null,
    /** PHASE 6: Current count of preview bboxes being displayed */
    previewBboxCount: Int = 0
) {
    val roi = guidanceState.scanRoi

    // Animate ROI size changes
    val animatedWidth by animateFloatAsState(
        targetValue = roi.widthNorm,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "roiWidth"
    )
    val animatedHeight by animateFloatAsState(
        targetValue = roi.heightNorm,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "roiHeight"
    )

    // Pulse animation for GOOD state
    val infiniteTransition = rememberInfiniteTransition(label = "guidancePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Determine colors based on state
    val (borderColor, glowColor, borderWidth) = remember(guidanceState.state) {
        when (guidanceState.state) {
            GuidanceState.SEARCHING -> Triple(ScaniumBlue.copy(alpha = 0.5f), CyanGlow.copy(alpha = 0.2f), 2f)
            GuidanceState.TOO_CLOSE, GuidanceState.TOO_FAR -> Triple(Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.3f), 2.5f)
            GuidanceState.OFF_CENTER -> Triple(Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.3f), 2.5f)
            GuidanceState.UNSTABLE, GuidanceState.FOCUSING -> Triple(ScaniumBlue.copy(alpha = 0.7f), CyanGlow.copy(alpha = 0.3f), 2f)
            GuidanceState.GOOD -> Triple(Color(0xFF1DB954), Color(0xFF1DB954).copy(alpha = 0.4f), 3f)
            GuidanceState.LOCKED -> Triple(Color(0xFF1DB954), Color(0xFF1DB954).copy(alpha = 0.5f), 4f)
        }
    }

    // Apply pulse effect for GOOD state
    val effectiveAlpha = when (guidanceState.state) {
        GuidanceState.GOOD -> pulseAlpha
        else -> 1f
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Canvas for scan zone overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate scan zone rectangle
            val zoneLeft = (roi.centerXNorm - animatedWidth / 2f) * canvasWidth
            val zoneTop = (roi.centerYNorm - animatedHeight / 2f) * canvasHeight
            val zoneWidth = animatedWidth * canvasWidth
            val zoneHeight = animatedHeight * canvasHeight
            val cornerRadius = 24.dp.toPx()

            // Draw dimmed background with cutout
            drawDimmedBackground(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                zoneLeft = zoneLeft,
                zoneTop = zoneTop,
                zoneWidth = zoneWidth,
                zoneHeight = zoneHeight,
                cornerRadius = cornerRadius,
                dimAlpha = 0.4f
            )

            // Draw glow effect
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * effectiveAlpha),
                topLeft = Offset(zoneLeft - 4.dp.toPx(), zoneTop - 4.dp.toPx()),
                size = Size(zoneWidth + 8.dp.toPx(), zoneHeight + 8.dp.toPx()),
                cornerRadius = CornerRadius(cornerRadius + 4.dp.toPx()),
                style = Stroke(width = 8.dp.toPx())
            )

            // Draw scan zone border
            drawRoundRect(
                color = borderColor.copy(alpha = borderColor.alpha * effectiveAlpha),
                topLeft = Offset(zoneLeft, zoneTop),
                size = Size(zoneWidth, zoneHeight),
                cornerRadius = CornerRadius(cornerRadius),
                style = Stroke(width = borderWidth.dp.toPx())
            )

            // Draw inner highlight for locked state
            if (guidanceState.state == GuidanceState.LOCKED) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(zoneLeft + 2.dp.toPx(), zoneTop + 2.dp.toPx()),
                    size = Size(zoneWidth - 4.dp.toPx(), zoneHeight - 4.dp.toPx()),
                    cornerRadius = CornerRadius(cornerRadius - 2.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Draw corner brackets for visual emphasis
            drawCornerBrackets(
                zoneLeft = zoneLeft,
                zoneTop = zoneTop,
                zoneWidth = zoneWidth,
                zoneHeight = zoneHeight,
                cornerRadius = cornerRadius,
                color = borderColor.copy(alpha = borderColor.alpha * effectiveAlpha),
                bracketSize = 20.dp.toPx(),
                strokeWidth = borderWidth.dp.toPx()
            )
        }

        // Hint text overlay
        AnimatedVisibility(
            visible = guidanceState.showHint && guidanceState.hintText != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            GuidanceHintChip(
                text = guidanceState.hintText ?: "",
                state = guidanceState.state
            )
        }

        // Debug info overlay
        // PHASE 6: Enhanced with ROI filter diagnostics
        if (showDebugInfo) {
            ScanDebugInfo(
                guidanceState = guidanceState,
                roiFilterResult = roiFilterResult,
                previewBboxCount = previewBboxCount,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Draw dimmed background with cutout for scan zone.
 */
private fun DrawScope.drawDimmedBackground(
    canvasWidth: Float,
    canvasHeight: Float,
    zoneLeft: Float,
    zoneTop: Float,
    zoneWidth: Float,
    zoneHeight: Float,
    cornerRadius: Float,
    dimAlpha: Float
) {
    // Create full screen path
    val fullPath = Path().apply {
        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
    }

    // Create cutout path (the scan zone)
    val cutoutPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = zoneLeft,
                top = zoneTop,
                right = zoneLeft + zoneWidth,
                bottom = zoneTop + zoneHeight,
                cornerRadius = CornerRadius(cornerRadius)
            )
        )
    }

    // Subtract cutout from full screen
    val resultPath = Path().apply {
        op(fullPath, cutoutPath, PathOperation.Difference)
    }

    // Draw the dimmed region
    drawPath(
        path = resultPath,
        color = Color.Black.copy(alpha = dimAlpha)
    )
}

/**
 * Draw corner brackets for visual emphasis.
 */
private fun DrawScope.drawCornerBrackets(
    zoneLeft: Float,
    zoneTop: Float,
    zoneWidth: Float,
    zoneHeight: Float,
    cornerRadius: Float,
    color: Color,
    bracketSize: Float,
    strokeWidth: Float
) {
    val bracketOffset = cornerRadius * 0.3f

    // Top-left corner
    drawLine(
        color = color,
        start = Offset(zoneLeft + bracketOffset, zoneTop),
        end = Offset(zoneLeft + bracketOffset + bracketSize, zoneTop),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(zoneLeft, zoneTop + bracketOffset),
        end = Offset(zoneLeft, zoneTop + bracketOffset + bracketSize),
        strokeWidth = strokeWidth
    )

    // Top-right corner
    drawLine(
        color = color,
        start = Offset(zoneLeft + zoneWidth - bracketOffset - bracketSize, zoneTop),
        end = Offset(zoneLeft + zoneWidth - bracketOffset, zoneTop),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(zoneLeft + zoneWidth, zoneTop + bracketOffset),
        end = Offset(zoneLeft + zoneWidth, zoneTop + bracketOffset + bracketSize),
        strokeWidth = strokeWidth
    )

    // Bottom-left corner
    drawLine(
        color = color,
        start = Offset(zoneLeft + bracketOffset, zoneTop + zoneHeight),
        end = Offset(zoneLeft + bracketOffset + bracketSize, zoneTop + zoneHeight),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(zoneLeft, zoneTop + zoneHeight - bracketOffset - bracketSize),
        end = Offset(zoneLeft, zoneTop + zoneHeight - bracketOffset),
        strokeWidth = strokeWidth
    )

    // Bottom-right corner
    drawLine(
        color = color,
        start = Offset(zoneLeft + zoneWidth - bracketOffset - bracketSize, zoneTop + zoneHeight),
        end = Offset(zoneLeft + zoneWidth - bracketOffset, zoneTop + zoneHeight),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(zoneLeft + zoneWidth, zoneTop + zoneHeight - bracketOffset - bracketSize),
        end = Offset(zoneLeft + zoneWidth, zoneTop + zoneHeight - bracketOffset),
        strokeWidth = strokeWidth
    )
}

/**
 * Hint chip displayed above the scan zone.
 */
@Composable
private fun GuidanceHintChip(
    text: String,
    state: GuidanceState,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (state) {
        GuidanceState.TOO_CLOSE, GuidanceState.TOO_FAR, GuidanceState.OFF_CENTER ->
            Color(0xFFFF9800).copy(alpha = 0.9f)
        GuidanceState.UNSTABLE ->
            Color(0xFFFF5722).copy(alpha = 0.9f)
        GuidanceState.FOCUSING ->
            ScaniumBlue.copy(alpha = 0.9f)
        GuidanceState.GOOD ->
            Color(0xFF1DB954).copy(alpha = 0.9f)
        else ->
            Color.Black.copy(alpha = 0.7f)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Debug info overlay showing scan diagnostics.
 *
 * PHASE 6: Enhanced with ROI filter diagnostics showing:
 * - Total detections from ML Kit
 * - ROI-eligible detections (shown as bboxes)
 * - Outside ROI count (filtered out)
 * - Lock state
 */
@Composable
private fun ScanDebugInfo(
    guidanceState: ScanGuidanceState,
    roiFilterResult: RoiFilterResult? = null,
    previewBboxCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val debugText = buildString {
        appendLine("State: ${guidanceState.state}")
        appendLine("ROI: ${(guidanceState.scanRoi.widthNorm * 100).toInt()}%")

        // PHASE 6: ROI filter diagnostics
        if (roiFilterResult != null) {
            appendLine("─── ROI Filter ───")
            appendLine("Total: ${roiFilterResult.totalDetections}")
            appendLine("In ROI: ${roiFilterResult.eligibleCount}")
            appendLine("Outside: ${roiFilterResult.outsideCount}")
            appendLine("Preview: $previewBboxCount")
            if (roiFilterResult.hasDetectionsOutsideRoiOnly) {
                appendLine("⚠ OUTSIDE ROI ONLY")
            }
        }

        appendLine("─── Quality ───")
        guidanceState.detectedBoxArea?.let {
            appendLine("Box: ${(it * 100).toInt()}%")
        }
        guidanceState.sharpnessScore?.let {
            appendLine("Sharp: ${it.toInt()}")
        }
        guidanceState.motionScore?.let {
            appendLine("Motion: ${String.format("%.2f", it)}")
        }
        guidanceState.centerDistance?.let {
            appendLine("Center: ${String.format("%.2f", it)}")
        }
        appendLine("─── Lock ───")
        if (guidanceState.lockedCandidateId != null) {
            appendLine("Locked: ${guidanceState.lockedCandidateId!!.take(8)}...")
        }
        if (guidanceState.canAddItem) {
            appendLine("✓ CAN ADD")
        }
    }

    Text(
        text = debugText,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    )
}

/**
 * Simplified overlay for when scanning is not active.
 */
@Composable
fun CameraGuidanceOverlayIdle(
    modifier: Modifier = Modifier
) {
    val defaultRoi = ScanRoi.DEFAULT

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val zoneLeft = (defaultRoi.centerXNorm - defaultRoi.widthNorm / 2f) * canvasWidth
        val zoneTop = (defaultRoi.centerYNorm - defaultRoi.heightNorm / 2f) * canvasHeight
        val zoneWidth = defaultRoi.widthNorm * canvasWidth
        val zoneHeight = defaultRoi.heightNorm * canvasHeight
        val cornerRadius = 24.dp.toPx()

        // Draw very subtle scan zone outline
        drawRoundRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(zoneLeft, zoneTop),
            size = Size(zoneWidth, zoneHeight),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * PHASE 4: Hint shown when detections exist but none are inside ROI.
 *
 * This teaches users to center objects in the scan zone.
 * Rate-limited to avoid flashing.
 */
@Composable
fun RoiCenteringHint(
    modifier: Modifier = Modifier
) {
    // Rate-limit hint display with simple animation
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.padding(top = 100.dp)
    ) {
        Text(
            text = "Center the object in the scan zone",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .background(
                    color = Color(0xFFFF9800).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
