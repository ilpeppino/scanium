package com.scanium.app.camera.detection

import android.os.SystemClock
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.camera.ScanMode
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.DetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Routes detection requests to appropriate detectors with throttling and deduplication.
 *
 * This router orchestrates multiple detection backends:
 * - Object detection (ML Kit Object Detection & Tracking)
 * - Barcode detection (future: ML Kit Barcode Scanning)
 * - Document detection (future: ML Kit Text Recognition)
 *
 * Key responsibilities:
 * 1. Throttle detector invocations to prevent excessive CPU/battery usage
 * 2. Deduplicate results to avoid emitting the same item multiple times
 * 3. Provide a unified event stream for downstream consumers
 * 4. Track debug metrics for performance monitoring
 *
 * Thread-safety: All public methods are thread-safe.
 */
class DetectionRouter(
    private val config: DetectionRouterConfig = DetectionRouterConfig(),
) {
    companion object {
        private const val TAG = "DetectionRouter"

        /** Log interval for debug counters (milliseconds) */
        private const val DEBUG_LOG_INTERVAL_MS = 5000L
    }

    // Throttling helper for rate-limiting detector invocations
    private val throttleHelper = ThrottleHelper()

    // Adaptive throttle policy for low-power mode
    private val adaptivePolicy = AdaptiveThrottlePolicy(config.adaptiveConfig)

    // Deduplication helper for filtering duplicate detections
    private val dedupeHelper = DedupeHelper(config.dedupeConfig)

    // Detector enable/disable flags (default ON for beta)
    private val _barcodeDetectionEnabled = AtomicBoolean(config.barcodeDetectionEnabled)
    private val _documentDetectionEnabled = AtomicBoolean(config.documentDetectionEnabled)

    // Expose adaptive throttle state
    val isAdaptiveThrottling: StateFlow<Boolean> = adaptivePolicy.isThrottling
    val adaptiveMultiplier: StateFlow<Float> = adaptivePolicy.adaptiveMultiplier

    // Debug counters (atomic for thread safety)
    private val frameCounter = AtomicLong(0)
    private val objectDetectionCounter = AtomicLong(0)
    private val barcodeDetectionCounter = AtomicLong(0)
    private val documentDetectionCounter = AtomicLong(0)
    private val throttledCounter = AtomicLong(0)
    private val dedupedCounter = AtomicLong(0)

    // Timing metrics
    private var sessionStartTimeMs = 0L
    private var lastDebugLogTimeMs = 0L

    // Current session state
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Last detection event for debugging
    private val _lastEvent = MutableStateFlow<DetectionEvent?>(null)
    val lastEvent: StateFlow<DetectionEvent?> = _lastEvent.asStateFlow()

    init {
        // Apply custom throttle intervals if provided
        config.throttleIntervals.forEach { (type, interval) ->
            throttleHelper.setMinInterval(type, interval)
        }
    }

    /**
     * Start a new detection session.
     * Resets counters and prepares for frame processing.
     */
    fun startSession() {
        Log.i(TAG, "Starting detection session")
        sessionStartTimeMs = SystemClock.elapsedRealtime()
        lastDebugLogTimeMs = sessionStartTimeMs
        resetCounters()
        throttleHelper.resetAll()
        dedupeHelper.resetAll()
        _isActive.value = true
    }

    /**
     * Stop the current detection session.
     * Logs final statistics and cleans up state.
     */
    fun stopSession() {
        if (!_isActive.value) return

        val duration = SystemClock.elapsedRealtime() - sessionStartTimeMs
        Log.i(TAG, "Stopping detection session after ${duration}ms")
        logDebugStats(force = true)
        _isActive.value = false
    }

    /**
     * Check if a detector type can be invoked right now.
     *
     * @param detectorType The detector to check
     * @return true if the detector can run, false if throttled
     */
    fun canInvokeDetector(detectorType: DetectorType): Boolean {
        return throttleHelper.canInvoke(detectorType)
    }

    /**
     * Attempt to invoke object detection.
     *
     * Returns true if detection should proceed, false if throttled.
     * Records the invocation timestamp if allowed.
     *
     * @param timestampMs Frame timestamp for throttle calculation
     * @return true if detection can proceed
     */
    fun tryInvokeObjectDetection(timestampMs: Long = SystemClock.elapsedRealtime()): Boolean {
        frameCounter.incrementAndGet()

        val allowed = throttleHelper.tryInvoke(DetectorType.OBJECT, timestampMs)

        if (allowed) {
            objectDetectionCounter.incrementAndGet()
            if (config.enableVerboseLogging) {
                Log.d(TAG, "[INVOKE] Object detection allowed at frame ${frameCounter.get()}")
            }
        } else {
            throttledCounter.incrementAndGet()
            if (config.enableVerboseLogging) {
                Log.d(TAG, "[THROTTLE] Object detection throttled at frame ${frameCounter.get()}")
            }
        }

        maybeLogDebugStats(timestampMs)
        return allowed
    }

    /**
     * Attempt to invoke barcode detection.
     * Returns false if detection is disabled or throttled.
     */
    fun tryInvokeBarcodeDetection(timestampMs: Long = SystemClock.elapsedRealtime()): Boolean {
        // Check if barcode detection is enabled
        if (!_barcodeDetectionEnabled.get()) {
            if (config.enableVerboseLogging) {
                Log.d(TAG, "[BARCODE] Detection disabled - skipping")
            }
            return false
        }

        val allowed = throttleHelper.tryInvoke(DetectorType.BARCODE, timestampMs)
        if (allowed) {
            barcodeDetectionCounter.incrementAndGet()
        } else {
            throttledCounter.incrementAndGet()
        }
        return allowed
    }

    /**
     * Attempt to invoke document detection.
     * Returns false if detection is disabled or throttled.
     */
    fun tryInvokeDocumentDetection(timestampMs: Long = SystemClock.elapsedRealtime()): Boolean {
        // Check if document detection is enabled
        if (!_documentDetectionEnabled.get()) {
            if (config.enableVerboseLogging) {
                Log.d(TAG, "[DOCUMENT] Detection disabled - skipping")
            }
            return false
        }

        val allowed = throttleHelper.tryInvoke(DetectorType.DOCUMENT, timestampMs)
        if (allowed) {
            documentDetectionCounter.incrementAndGet()
        } else {
            throttledCounter.incrementAndGet()
        }
        return allowed
    }

    /**
     * Route a detection request based on scan mode.
     *
     * This is the main entry point for the analyzer. It:
     * 1. Checks if detection should be throttled
     * 2. Returns the appropriate detector type and throttle status
     *
     * @param scanMode Current scan mode
     * @param timestampMs Frame timestamp
     * @return Pair of (canProceed, detectorType)
     */
    fun routeDetection(
        scanMode: ScanMode,
        timestampMs: Long = SystemClock.elapsedRealtime(),
    ): Pair<Boolean, DetectorType> {
        val detectorType =
            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> DetectorType.OBJECT
                ScanMode.BARCODE -> DetectorType.BARCODE
                ScanMode.DOCUMENT_TEXT -> DetectorType.DOCUMENT
            }

        val canProceed =
            when (detectorType) {
                DetectorType.OBJECT -> tryInvokeObjectDetection(timestampMs)
                DetectorType.BARCODE -> tryInvokeBarcodeDetection(timestampMs)
                DetectorType.DOCUMENT -> tryInvokeDocumentDetection(timestampMs)
            }

        return canProceed to detectorType
    }

    /**
     * Process object detection results and emit event.
     *
     * @param items Detected scanned items
     * @param detectionResults Raw detection results for overlay
     * @return DetectionEvent containing the results
     */
    fun processObjectResults(
        items: List<ScannedItem>,
        detectionResults: List<DetectionResult>,
    ): DetectionEvent.ObjectDetected {
        val event =
            DetectionEvent.ObjectDetected(
                timestampMs = System.currentTimeMillis(),
                source = DetectorType.OBJECT,
                items = items,
                detectionResults = detectionResults,
            )

        _lastEvent.value = event

        if (config.enableVerboseLogging) {
            Log.d(TAG, "[RESULT] Object detection: ${items.size} items, ${detectionResults.size} detections")
        }

        return event
    }

    /**
     * Process barcode detection results with deduplication and emit event.
     * Filters out barcodes that have been seen within the dedupe window.
     *
     * @param items Detected barcode items from BarcodeDetectorClient
     * @return DetectionEvent with deduplicated items, and a list of unique items to add
     */
    fun processBarcodeResults(items: List<ScannedItem>): Pair<DetectionEvent.BarcodeDetected, List<ScannedItem>> {
        val timestampMs = System.currentTimeMillis()
        val uniqueItems = mutableListOf<ScannedItem>()

        for (item in items) {
            val rawValue = item.barcodeValue
            if (rawValue.isNullOrEmpty()) {
                Log.w(TAG, "[BARCODE] Skipping item with empty rawValue: ${item.id}")
                continue
            }

            // Get format code from category (QR_CODE = 256, others = barcode format)
            val formatCode = if (item.category.name == "QR_CODE") 256 else 0

            // Check deduplication
            val isNew =
                dedupeHelper.checkAndRecordBarcode(
                    rawValue = rawValue,
                    format = formatCode,
                    itemId = item.id,
                )

            if (isNew) {
                uniqueItems.add(item)
                Log.i(TAG, "[BARCODE_DETECTED] New barcode: value=$rawValue, category=${item.category}, format=${item.labelText}")
            } else {
                dedupedCounter.incrementAndGet()
                Log.d(TAG, "[BARCODE_DEDUPE] Skipped duplicate: value=$rawValue")
            }
        }

        val dedupedCount = items.size - uniqueItems.size
        val event =
            DetectionEvent.BarcodeDetected(
                timestampMs = timestampMs,
                source = DetectorType.BARCODE,
                items = uniqueItems,
                rawDetectionCount = items.size,
                dedupedCount = dedupedCount,
            )
        _lastEvent.value = event

        if (config.enableVerboseLogging) {
            Log.d(TAG, "[RESULT] Barcode detection: ${items.size} raw -> ${uniqueItems.size} unique ($dedupedCount deduped)")
        }

        return event to uniqueItems
    }

    /**
     * Process document detection results and emit event.
     * Future: Will be implemented when document detector is integrated.
     */
    fun processDocumentResults(items: List<ScannedItem>): DetectionEvent.DocumentDetected {
        val event =
            DetectionEvent.DocumentDetected(
                timestampMs = System.currentTimeMillis(),
                source = DetectorType.DOCUMENT,
                items = items,
            )
        _lastEvent.value = event
        return event
    }

    /**
     * Create a throttled event for debugging.
     */
    fun createThrottledEvent(
        detectorType: DetectorType,
        reason: ThrottleReason = ThrottleReason.INTERVAL_NOT_MET,
    ): DetectionEvent.Throttled {
        return DetectionEvent.Throttled(
            timestampMs = System.currentTimeMillis(),
            source = detectorType,
            reason = reason,
        )
    }

    /**
     * Update throttle interval for a detector.
     */
    fun setThrottleInterval(
        detectorType: DetectorType,
        intervalMs: Long,
    ) {
        throttleHelper.setMinInterval(detectorType, intervalMs)
        Log.i(TAG, "Updated throttle interval for $detectorType: ${intervalMs}ms")
    }

    /**
     * Get current throttle interval for a detector.
     * If adaptive throttling is enabled, returns the adjusted interval.
     */
    fun getThrottleInterval(detectorType: DetectorType): Long {
        val baseInterval = throttleHelper.getMinInterval(detectorType)
        return adaptivePolicy.applyToInterval(baseInterval)
    }

    /**
     * Get the base (unadjusted) throttle interval for a detector.
     */
    fun getBaseThrottleInterval(detectorType: DetectorType): Long {
        return throttleHelper.getMinInterval(detectorType)
    }

    /**
     * Records frame processing time for adaptive throttling.
     *
     * @param processingTimeMs Time taken to process the frame in milliseconds
     */
    fun recordFrameProcessingTime(processingTimeMs: Long) {
        adaptivePolicy.recordProcessingTime(processingTimeMs)
    }

    // =========================================================================
    // Detector Enable/Disable Controls
    // =========================================================================

    /**
     * Enables or disables barcode/QR detection.
     */
    fun setBarcodeDetectionEnabled(enabled: Boolean) {
        val wasEnabled = _barcodeDetectionEnabled.getAndSet(enabled)
        if (wasEnabled != enabled) {
            Log.i(TAG, "Barcode detection ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Checks if barcode detection is enabled.
     */
    fun isBarcodeDetectionEnabled(): Boolean = _barcodeDetectionEnabled.get()

    /**
     * Enables or disables document candidate detection.
     */
    fun setDocumentDetectionEnabled(enabled: Boolean) {
        val wasEnabled = _documentDetectionEnabled.getAndSet(enabled)
        if (wasEnabled != enabled) {
            Log.i(TAG, "Document detection ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Checks if document detection is enabled.
     */
    fun isDocumentDetectionEnabled(): Boolean = _documentDetectionEnabled.get()

    /**
     * Enables or disables adaptive throttling (low-power mode).
     */
    fun setAdaptiveThrottlingEnabled(enabled: Boolean) {
        adaptivePolicy.setEnabled(enabled)
    }

    /**
     * Checks if adaptive throttling is enabled.
     */
    fun isAdaptiveThrottlingEnabled(): Boolean = adaptivePolicy.isEnabled

    /**
     * Gets adaptive throttle policy statistics.
     */
    fun getAdaptiveStats(): AdaptiveThrottleStats = adaptivePolicy.getStats()

    /**
     * Reset all router state.
     */
    fun reset() {
        Log.i(TAG, "Resetting router state")
        resetCounters()
        throttleHelper.resetAll()
        dedupeHelper.resetAll()
        adaptivePolicy.reset()
        _lastEvent.value = null
    }

    /**
     * Get current detection statistics.
     */
    fun getStats(): DetectionRouterStats {
        val uptime =
            if (sessionStartTimeMs > 0) {
                SystemClock.elapsedRealtime() - sessionStartTimeMs
            } else {
                0L
            }

        val totalFrames = frameCounter.get()
        val fps =
            if (uptime > 0) {
                (totalFrames * 1000.0) / uptime
            } else {
                0.0
            }

        return DetectionRouterStats(
            uptimeMs = uptime,
            totalFrames = totalFrames,
            objectDetections = objectDetectionCounter.get(),
            barcodeDetections = barcodeDetectionCounter.get(),
            documentDetections = documentDetectionCounter.get(),
            throttledFrames = throttledCounter.get(),
            dedupedItems = dedupedCounter.get(),
            framesPerSecond = fps,
            throttleStats = throttleHelper.getStats(),
            dedupeStats = dedupeHelper.getStats(),
        )
    }

    private fun resetCounters() {
        frameCounter.set(0)
        objectDetectionCounter.set(0)
        barcodeDetectionCounter.set(0)
        documentDetectionCounter.set(0)
        throttledCounter.set(0)
        dedupedCounter.set(0)
    }

    private fun maybeLogDebugStats(currentTimeMs: Long) {
        if (!config.enableDebugLogging) return
        if (currentTimeMs - lastDebugLogTimeMs < DEBUG_LOG_INTERVAL_MS) return

        logDebugStats(force = false)
        lastDebugLogTimeMs = currentTimeMs
    }

    private fun logDebugStats(force: Boolean) {
        if (!force && !config.enableDebugLogging) return

        val stats = getStats()
        Log.i(
            TAG,
            buildString {
                append("[STATS] ")
                append("frames=${stats.totalFrames}, ")
                append("objDet=${stats.objectDetections}, ")
                append("throttled=${stats.throttledFrames}, ")
                append("fps=${String.format("%.1f", stats.framesPerSecond)}, ")
                append("uptime=${stats.uptimeMs}ms")
            },
        )
    }
}

/**
 * Configuration for DetectionRouter behavior.
 */
data class DetectionRouterConfig(
    /** Custom throttle intervals per detector type (overrides defaults) */
    val throttleIntervals: Map<DetectorType, Long> = emptyMap(),
    /** Deduplication configuration */
    val dedupeConfig: DedupeConfig = DedupeConfig(),
    /** Adaptive throttle configuration for low-power mode */
    val adaptiveConfig: AdaptiveThrottleConfig = AdaptiveThrottleConfig(),
    /** Whether barcode/QR detection is enabled (default ON for beta) */
    val barcodeDetectionEnabled: Boolean = true,
    /** Whether document candidate detection is enabled (default ON for beta) */
    val documentDetectionEnabled: Boolean = true,
    /** Enable verbose logging for debugging */
    val enableVerboseLogging: Boolean = BuildConfig.DEBUG,
    /** Enable periodic debug stats logging */
    val enableDebugLogging: Boolean = BuildConfig.DEBUG,
)

/**
 * Statistics for detection router performance monitoring.
 */
data class DetectionRouterStats(
    val uptimeMs: Long,
    val totalFrames: Long,
    val objectDetections: Long,
    val barcodeDetections: Long,
    val documentDetections: Long,
    val throttledFrames: Long,
    val dedupedItems: Long,
    val framesPerSecond: Double,
    val throttleStats: Map<DetectorType, ThrottleStats>,
    val dedupeStats: DedupeStats,
)
