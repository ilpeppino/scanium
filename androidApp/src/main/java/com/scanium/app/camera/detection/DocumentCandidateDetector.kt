package com.scanium.app.camera.detection

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.scanium.app.BuildConfig
import com.scanium.app.NormalizedRect
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

data class DocumentCandidate(
    val bounds: NormalizedRect,
    val quad: List<NormalizedPoint>,
    val confidence: Float,
    val timestampMs: Long,
)

data class DocumentCandidateState(
    val candidate: DocumentCandidate,
    val lastSeenMs: Long,
    val averageProcessingMs: Double,
)

data class DocumentCandidateDetectorConfig(
    val sampleStep: Int = 8,
    val minEdgeThreshold: Int = 28,
    val edgeStdFactor: Float = 1.1f,
    val minEdgeCount: Int = 120,
    val minAreaRatio: Float = 0.2f,
    val maxAreaRatio: Float = 0.95f,
    val minAspectRatio: Float = 0.6f,
    val maxAspectRatio: Float = 1.8f,
    val targetAspectRatio: Float = 1.414f,
    val enableDebugLogging: Boolean = BuildConfig.DEBUG,
    val logEveryN: Int = 30,
)

class DocumentCandidateDetector(
    private val config: DocumentCandidateDetectorConfig = DocumentCandidateDetectorConfig(),
) {
    companion object {
        private const val TAG = "DocumentCandidateDetector"
    }

    private val totalProcessingMs = AtomicLong(0)
    private val totalRuns = AtomicLong(0)

    fun detect(
        imageProxy: ImageProxy,
        timestampMs: Long = SystemClock.elapsedRealtime(),
    ): DocumentCandidate? {
        val startTimeMs = SystemClock.elapsedRealtime()
        var candidate: DocumentCandidate? = null
        try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val width = imageProxy.width
            val height = imageProxy.height
            if (width <= 0 || height <= 0) return null

            val step = config.sampleStep.coerceAtLeast(2)
            val sampleWidth = (width + step - 1) / step
            val sampleHeight = (height + step - 1) / step
            val sampleSize = sampleWidth * sampleHeight
            val samples = ByteArray(sampleSize)

            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            var sampleIndex = 0
            var sum = 0L
            var sumSq = 0L
            var y = 0
            while (y < height) {
                val rowOffset = y * rowStride
                var x = 0
                while (x < width) {
                    val value = buffer.get(rowOffset + x * pixelStride).toInt() and 0xFF
                    samples[sampleIndex] = value.toByte()
                    sum += value
                    sumSq += value * value
                    sampleIndex++
                    x += step
                }
                y += step
            }

            if (sampleIndex == 0) return null

            val mean = sum.toDouble() / sampleIndex
            val variance = (sumSq.toDouble() / sampleIndex) - (mean * mean)
            val stdDev = sqrt(variance.coerceAtLeast(0.0))
            val edgeThreshold = max(config.minEdgeThreshold, (stdDev * config.edgeStdFactor).toInt())

            var edgeCount = 0
            var minX = sampleWidth
            var maxX = 0
            var minY = sampleHeight
            var maxY = 0

            for (sy in 1 until sampleHeight - 1) {
                val rowIndex = sy * sampleWidth
                for (sx in 1 until sampleWidth - 1) {
                    val idx = rowIndex + sx
                    val left = samples[idx - 1].toInt() and 0xFF
                    val right = samples[idx + 1].toInt() and 0xFF
                    val up = samples[idx - sampleWidth].toInt() and 0xFF
                    val down = samples[idx + sampleWidth].toInt() and 0xFF
                    val gradient = abs(right - left) + abs(down - up)
                    if (gradient > edgeThreshold) {
                        edgeCount++
                        if (sx < minX) minX = sx
                        if (sx > maxX) maxX = sx
                        if (sy < minY) minY = sy
                        if (sy > maxY) maxY = sy
                    }
                }
            }

            if (edgeCount < config.minEdgeCount) return null

            val bboxWidth = (maxX - minX + 1).coerceAtLeast(1)
            val bboxHeight = (maxY - minY + 1).coerceAtLeast(1)
            val bboxArea = bboxWidth * bboxHeight
            val areaRatio = bboxArea.toFloat() / (sampleWidth * sampleHeight).toFloat()
            if (areaRatio < config.minAreaRatio || areaRatio > config.maxAreaRatio) return null

            val aspectRatio = bboxWidth.toFloat() / bboxHeight.toFloat()
            if (aspectRatio < config.minAspectRatio || aspectRatio > config.maxAspectRatio) return null

            val areaScore =
                (
                    (areaRatio - config.minAreaRatio) /
                        (config.maxAreaRatio - config.minAreaRatio)
                ).coerceIn(0f, 1f)
            val aspectScore =
                (
                    1f - (
                        abs(aspectRatio - config.targetAspectRatio) /
                            config.targetAspectRatio
                    )
                ).coerceIn(0f, 1f)
            val perimeter = (2 * (bboxWidth + bboxHeight)).coerceAtLeast(1)
            val edgeScore = (edgeCount.toFloat() / (perimeter * 2f)).coerceIn(0f, 1f)
            val confidence =
                (areaScore * 0.45f + aspectScore * 0.35f + edgeScore * 0.20f)
                    .coerceIn(0f, 1f)

            val leftNorm = minX.toFloat() / sampleWidth
            val topNorm = minY.toFloat() / sampleHeight
            val rightNorm = (maxX + 1).toFloat() / sampleWidth
            val bottomNorm = (maxY + 1).toFloat() / sampleHeight
            val bounds = NormalizedRect(leftNorm, topNorm, rightNorm, bottomNorm).clampToUnit()

            candidate =
                DocumentCandidate(
                    bounds = bounds,
                    quad =
                        listOf(
                            NormalizedPoint(bounds.left, bounds.top),
                            NormalizedPoint(bounds.right, bounds.top),
                            NormalizedPoint(bounds.right, bounds.bottom),
                            NormalizedPoint(bounds.left, bounds.bottom),
                        ),
                    confidence = confidence,
                    timestampMs = timestampMs,
                )
        } finally {
            val durationMs = SystemClock.elapsedRealtime() - startTimeMs
            val total = totalProcessingMs.addAndGet(durationMs)
            val count = totalRuns.incrementAndGet()
            if (config.enableDebugLogging && config.logEveryN > 0 && count % config.logEveryN == 0L) {
                val averageMs = total.toDouble() / count.toDouble()
                Log.d(TAG, "avg=${"%.1f".format(averageMs)}ms last=${durationMs}ms runs=$count")
            }
        }
        return candidate
    }

    fun averageProcessingMs(): Double {
        val count = totalRuns.get().coerceAtLeast(1)
        return totalProcessingMs.get().toDouble() / count.toDouble()
    }
}
