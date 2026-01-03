package com.scanium.app.ui.motion

import android.util.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.scanium.app.camera.ConfidenceTiers
import com.scanium.app.camera.DetectionOverlay
import com.scanium.app.camera.OverlayBoxStyle
import com.scanium.app.camera.OverlayTrack

/**
 * Motion-enhanced detection overlay that wraps the standard [DetectionOverlay]
 * with Scanium motion language features:
 *
 * 1. **ScanFrameAppear**: Quick fade-in when detections become available
 * 2. **LightningScanPulse**: Single pulse when an item is confirmed (LOCKED state)
 * 3. **PriceCountUp**: Animated price display (handled in label rendering)
 *
 * This composable handles state management for motion animations while
 * delegating the actual bounding box rendering to [DetectionOverlay].
 *
 * @param detections List of detection results to render
 * @param imageSize Size of the analyzed image from ML Kit
 * @param previewSize Size of the preview view on screen
 * @param rotationDegrees Image rotation for coordinate mapping
 * @param showGeometryDebug Developer toggle for geometry debug overlay
 * @param overlayAccuracyStep Developer debug filter: step index for confidence filtering
 *        (0 = show all, higher = more filtering). See [ConfidenceTiers] for tier definitions.
 * @param modifier Modifier for the container
 */
@Composable
fun MotionEnhancedOverlay(
    detections: List<OverlayTrack>,
    imageSize: Size,
    previewSize: Size,
    rotationDegrees: Int = 90,
    showGeometryDebug: Boolean = false,
    overlayAccuracyStep: Int = 0,
    modifier: Modifier = Modifier,
) {
    // Apply confidence-based filtering if enabled (developer debug feature)
    // This filters ONLY what is rendered - does NOT affect detection or aggregation logic
    val filteredDetections = remember(detections, overlayAccuracyStep) {
        ConfidenceTiers.filterDetections(detections, overlayAccuracyStep)
    }
    // Track if we have any detections (for scan frame appear)
    val hasDetections by remember(detections) {
        derivedStateOf { detections.isNotEmpty() }
    }

    // Track confirmed items to trigger pulse animation
    // Key: aggregatedId, Value: timestamp when confirmed
    val confirmedItems = remember { mutableStateMapOf<String, Long>() }

    // Track the latest pulse trigger key
    var pulseTriggerKey by remember { mutableLongStateOf(0L) }

    // Track frame visibility for ScanFrameAppear
    var showScanFrame by remember { mutableStateOf(false) }

    // Calculate bounding rect for the scan frame (union of all detection bboxes)
    val scanFrameRect by remember(detections) {
        derivedStateOf {
            if (detections.isEmpty()) {
                Rect(0.2f, 0.2f, 0.8f, 0.8f) // Default center rect
            } else {
                val minLeft = detections.minOf { it.bboxNorm.left }
                val minTop = detections.minOf { it.bboxNorm.top }
                val maxRight = detections.maxOf { it.bboxNorm.right }
                val maxBottom = detections.maxOf { it.bboxNorm.bottom }
                // Add padding around detections
                Rect(
                    left = (minLeft - 0.05f).coerceAtLeast(0f),
                    top = (minTop - 0.05f).coerceAtLeast(0f),
                    right = (maxRight + 0.05f).coerceAtMost(1f),
                    bottom = (maxBottom + 0.05f).coerceAtMost(1f),
                )
            }
        }
    }

    // Detect new LOCKED items to trigger pulse
    LaunchedEffect(detections) {
        val currentTime = System.currentTimeMillis()

        detections.forEach { track ->
            val aggregatedId = track.aggregatedId ?: return@forEach

            // Check if this item just became LOCKED (confirmed)
            if (track.boxStyle == OverlayBoxStyle.LOCKED) {
                val previousConfirmTime = confirmedItems[aggregatedId]

                // Only trigger pulse if this is a NEW confirmation
                // (not seen before, or was confirmed more than debounce period ago)
                if (previousConfirmTime == null ||
                    (currentTime - previousConfirmTime) > MotionConstants.PULSE_DEBOUNCE_MS
                ) {
                    confirmedItems[aggregatedId] = currentTime
                    pulseTriggerKey = currentTime
                }
            }
        }

        // Cleanup old confirmed items to prevent memory growth
        val staleThreshold = currentTime - 30_000L // 30 seconds
        confirmedItems.entries.removeAll { it.value < staleThreshold }
    }

    // Update scan frame visibility
    LaunchedEffect(hasDetections) {
        showScanFrame = hasDetections
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Scan frame appear animation (behind everything)
        if (MotionConfig.isMotionOverlaysEnabled && showScanFrame) {
            ScanFrameAppear(
                rect = scanFrameRect,
                isVisible = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 2: Standard detection overlay (bounding boxes and labels)
        // Uses filteredDetections to apply developer accuracy filter (visibility only)
        if (filteredDetections.isNotEmpty() && previewSize.width > 0 && previewSize.height > 0) {
            DetectionOverlay(
                detections = filteredDetections,
                imageSize = imageSize,
                previewSize = previewSize,
                rotationDegrees = rotationDegrees,
                showGeometryDebug = showGeometryDebug,
            )
        }

        // Layer 3: Lightning pulse animation (on top)
        if (MotionConfig.isMotionOverlaysEnabled && pulseTriggerKey > 0) {
            // Find the LOCKED detection for pulse positioning
            val lockedTrack = detections.firstOrNull { it.boxStyle == OverlayBoxStyle.LOCKED }
            val pulseRect =
                lockedTrack?.let {
                    Rect(
                        left = it.bboxNorm.left,
                        top = it.bboxNorm.top,
                        right = it.bboxNorm.right,
                        bottom = it.bboxNorm.bottom,
                    )
                } ?: scanFrameRect

            LightningScanPulse(
                rect = pulseRect,
                triggerKey = pulseTriggerKey,
                direction = PulseDirection.VERTICAL_DOWN,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * State holder for motion overlay animations.
 *
 * Use this to track animation state across recompositions
 * and avoid restarting animations unnecessarily.
 */
class MotionOverlayState {
    /** Set of aggregated IDs that have had their price animated */
    val animatedPrices = mutableSetOf<String>()

    /** Set of aggregated IDs that have triggered a pulse */
    val pulsedItems = mutableSetOf<String>()

    /** Timestamp of last pulse trigger */
    var lastPulseTime: Long = 0L

    /**
     * Check if price animation should run for this item.
     *
     * @param aggregatedId The item's aggregated ID
     * @return true if animation should run (first time seeing this ID)
     */
    fun shouldAnimatePrice(aggregatedId: String): Boolean {
        return !animatedPrices.contains(aggregatedId)
    }

    /**
     * Mark price as animated for this item.
     */
    fun markPriceAnimated(aggregatedId: String) {
        animatedPrices.add(aggregatedId)
    }

    /**
     * Check if pulse should trigger for this item confirmation.
     */
    fun shouldTriggerPulse(aggregatedId: String): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPulseTime < MotionConstants.PULSE_DEBOUNCE_MS) {
            return false
        }
        return !pulsedItems.contains(aggregatedId)
    }

    /**
     * Mark item as pulsed and update last pulse time.
     */
    fun markPulsed(aggregatedId: String) {
        pulsedItems.add(aggregatedId)
        lastPulseTime = System.currentTimeMillis()
    }

    /**
     * Clear all state (e.g., when session resets).
     */
    fun clear() {
        animatedPrices.clear()
        pulsedItems.clear()
        lastPulseTime = 0L
    }
}

/**
 * Remember a [MotionOverlayState] instance scoped to the composition.
 */
@Composable
fun rememberMotionOverlayState(): MotionOverlayState {
    return remember { MotionOverlayState() }
}
