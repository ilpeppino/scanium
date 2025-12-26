package com.scanium.app.camera

import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scanium.app.camera.detection.DocumentCandidateState
import com.scanium.app.platform.toRectF
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.ScaniumBlue

@Composable
fun DocumentAlignmentOverlay(
    candidateState: DocumentCandidateState,
    imageSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val transform = calculateTransform(
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            previewWidth = canvasWidth,
            previewHeight = canvasHeight
        )

        val strokeWidth = 2.dp.toPx()
        val glowWidth = 6.dp.toPx()
        val cornerRadius = 12.dp.toPx()
        val bounds = candidateState.candidate.bounds.toRectF(imageSize.width, imageSize.height)
        val transformedBounds = transformBoundingBox(bounds, transform)

        val topLeft = Offset(transformedBounds.left, transformedBounds.top)
        val boxSize = ComposeSize(transformedBounds.width(), transformedBounds.height())

        drawRoundRect(
            color = CyanGlow.copy(alpha = 0.35f),
            topLeft = topLeft,
            size = boxSize,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = glowWidth)
        )

        drawRoundRect(
            color = ScaniumBlue,
            topLeft = topLeft,
            size = boxSize,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth)
        )

        val quad = candidateState.candidate.quad
        if (quad.size == 4) {
            val points = quad.map { point ->
                val x = point.x * imageSize.width
                val y = point.y * imageSize.height
                Offset(
                    x = x * transform.scaleX + transform.offsetX,
                    y = y * transform.scaleY + transform.offsetY
                )
            }

            for (index in points.indices) {
                val start = points[index]
                val end = points[(index + 1) % points.size]
                drawLine(
                    color = ScaniumBlue,
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}
