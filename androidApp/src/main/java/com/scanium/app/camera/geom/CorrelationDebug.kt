package com.scanium.app.camera.geom

import android.os.SystemClock
import android.util.Log
import com.scanium.app.NormalizedRect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Correlation Debug System for verifying bbox↔snapshot alignment.
 *
 * When enabled (via Developer Options), this system:
 * 1. Logs correlation metrics once per second
 * 2. Provides data for the debug overlay
 * 3. Validates aspect ratio preservation between bbox and crop
 *
 * Tag: CORR (for logcat filtering)
 */
object CorrelationDebug {
    private const val TAG = "CORR"
    private const val LOG_INTERVAL_MS = 1000L

    private var _enabled = false
    val enabled: Boolean get() = _enabled

    private var lastLogTime = 0L

    private val _currentDebugInfo = MutableStateFlow<CorrelationDebugState?>(null)
    val currentDebugInfo: StateFlow<CorrelationDebugState?> = _currentDebugInfo.asStateFlow()

    /**
     * Enable or disable correlation debug logging and overlay.
     */
    fun setEnabled(enabled: Boolean) {
        _enabled = enabled
        if (!enabled) {
            _currentDebugInfo.value = null
        }
        Log.i(TAG, "Correlation debug ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Record correlation data for the current frame/detection.
     *
     * @param normalizedBbox The normalized bbox from detection
     * @param rotationDegrees Camera rotation
     * @param proxyWidth ImageProxy width (sensor)
     * @param proxyHeight ImageProxy height (sensor)
     * @param inputImageWidth InputImage width (upright)
     * @param inputImageHeight InputImage height (upright)
     * @param previewWidth Preview composable width
     * @param previewHeight Preview composable height
     * @param bitmapWidth Source bitmap width (for cropping)
     * @param bitmapHeight Source bitmap height (for cropping)
     */
    fun recordCorrelation(
        normalizedBbox: NormalizedRect?,
        rotationDegrees: Int,
        proxyWidth: Int,
        proxyHeight: Int,
        inputImageWidth: Int,
        inputImageHeight: Int,
        previewWidth: Int = 0,
        previewHeight: Int = 0,
        bitmapWidth: Int = 0,
        bitmapHeight: Int = 0,
    ) {
        if (!_enabled || normalizedBbox == null) return

        val now = SystemClock.elapsedRealtime()

        // Calculate metrics
        val bboxAR = GeometryMapper.aspectRatio(normalizedBbox)

        // For snapshot crop, if bitmap dims are provided, calculate crop AR
        val cropAR =
            if (bitmapWidth > 0 && bitmapHeight > 0) {
                val cropRect =
                    GeometryMapper.uprightToBitmapCrop(
                        normalizedBbox = normalizedBbox,
                        bitmapWidth = bitmapWidth,
                        bitmapHeight = bitmapHeight,
                        padding = 0f,
                    )
                GeometryMapper.aspectRatio(cropRect)
            } else {
                bboxAR // Assume same if no bitmap dims
            }

        val aspectRatioMatch = GeometryMapper.validateAspectRatio(bboxAR, cropAR)

        // Create state for overlay
        val state =
            CorrelationDebugState(
                rotationDegrees = rotationDegrees,
                proxyWidth = proxyWidth,
                proxyHeight = proxyHeight,
                inputImageWidth = inputImageWidth,
                inputImageHeight = inputImageHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                bboxNormalized = normalizedBbox,
                bboxAspectRatio = bboxAR,
                cropAspectRatio = cropAR,
                aspectRatioMatch = aspectRatioMatch,
                timestamp = now,
            )

        _currentDebugInfo.value = state

        // Rate-limited logging
        if (now - lastLogTime >= LOG_INTERVAL_MS) {
            lastLogTime = now
            logCorrelation(state)
        }
    }

    private fun logCorrelation(state: CorrelationDebugState) {
        Log.i(
            TAG,
            buildString {
                append("rotation=${state.rotationDegrees}, ")
                append("proxy=${state.proxyWidth}x${state.proxyHeight}, ")
                append("input=${state.inputImageWidth}x${state.inputImageHeight}, ")
                append("preview=${state.previewWidth}x${state.previewHeight}, ")
                append("bitmap=${state.bitmapWidth}x${state.bitmapHeight}")
            },
        )

        Log.i(
            TAG,
            buildString {
                append("bbox=(")
                append("${"%.3f".format(state.bboxNormalized.left)}, ")
                append("${"%.3f".format(state.bboxNormalized.top)}, ")
                append("${"%.3f".format(state.bboxNormalized.right)}, ")
                append("${"%.3f".format(state.bboxNormalized.bottom)})")
            },
        )

        val matchIndicator = if (state.aspectRatioMatch) "OK" else "MISMATCH"
        Log.i(
            TAG,
            buildString {
                append("bboxAR=${"%.3f".format(state.bboxAspectRatio)}, ")
                append("cropAR=${"%.3f".format(state.cropAspectRatio)}, ")
                append("match=$matchIndicator")
            },
        )

        if (!state.aspectRatioMatch) {
            val diff = kotlin.math.abs(state.bboxAspectRatio - state.cropAspectRatio)
            Log.w(TAG, "!!! ASPECT RATIO MISMATCH: diff=${"%.4f".format(diff)}")
        }
    }

    /**
     * Clear the current debug state.
     */
    fun clear() {
        _currentDebugInfo.value = null
    }
}

/**
 * State for the correlation debug overlay.
 */
data class CorrelationDebugState(
    val rotationDegrees: Int,
    val proxyWidth: Int,
    val proxyHeight: Int,
    val inputImageWidth: Int,
    val inputImageHeight: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val bboxNormalized: NormalizedRect,
    val bboxAspectRatio: Float,
    val cropAspectRatio: Float,
    val aspectRatioMatch: Boolean,
    val timestamp: Long,
)
