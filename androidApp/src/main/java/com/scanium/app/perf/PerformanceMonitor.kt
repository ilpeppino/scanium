package com.scanium.app.perf

import android.os.SystemClock
import android.os.Trace
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.SpanContext

/**
 * Centralized performance monitoring utility for Scanium.
 *
 * Provides a global access point for telemetry-based performance measurement,
 * eliminating the need to pass Telemetry instances through every constructor.
 *
 * ## Metrics Tracked
 * - `ml_inference_latency_ms` - ML Kit detection duration
 * - `frame_analysis_latency_ms` - End-to-end frame processing
 * - `aggregation_latency_ms` - ItemAggregator duration
 * - `bitmap_decode_latency_ms` - Image decoding
 * - `overlay_draw_latency_ms` - Canvas draw calls
 *
 * ## Usage
 * ```kotlin
 * // Initialize in Application.onCreate()
 * PerformanceMonitor.init(telemetry)
 *
 * // Measure ML inference
 * val result = PerformanceMonitor.measureMlInference("object_detection") {
 *     detector.process(image).await()
 * }
 *
 * // Manual timing
 * PerformanceMonitor.recordTimer("custom_operation_ms", durationMs)
 * ```
 */
object PerformanceMonitor {

    @Volatile
    private var telemetry: Telemetry? = null

    // Metric names for consistency
    object Metrics {
        const val ML_INFERENCE_LATENCY_MS = "ml_inference_latency_ms"
        const val FRAME_ANALYSIS_LATENCY_MS = "frame_analysis_latency_ms"
        const val AGGREGATION_LATENCY_MS = "aggregation_latency_ms"
        const val BITMAP_DECODE_LATENCY_MS = "bitmap_decode_latency_ms"
        const val OVERLAY_DRAW_LATENCY_MS = "overlay_draw_latency_ms"
        const val TRACKING_LATENCY_MS = "tracking_latency_ms"
        const val THUMBNAIL_CROP_LATENCY_MS = "thumbnail_crop_latency_ms"
    }

    // Span names for distributed tracing
    object Spans {
        const val ML_INFERENCE = "ml.inference"
        const val FRAME_ANALYSIS = "camera.frame_analysis"
        const val AGGREGATION = "tracking.aggregation"
        const val BITMAP_DECODE = "image.bitmap_decode"
        const val OVERLAY_DRAW = "ui.overlay_draw"
        const val TRACKING_PROCESS = "tracking.process_frame"
        const val THUMBNAIL_CROP = "image.thumbnail_crop"
    }

    /**
     * Initialize the performance monitor with a Telemetry instance.
     * Should be called from Application.onCreate() after telemetry is initialized.
     */
    fun init(telemetry: Telemetry) {
        this.telemetry = telemetry
    }

    /**
     * Returns true if performance monitoring is initialized and available.
     */
    fun isInitialized(): Boolean = telemetry != null

    /**
     * Begins a tracing span for measuring operation duration.
     * Returns a SpanContext that must be ended when the operation completes.
     *
     * @param name Span name (use Spans constants for consistency)
     * @param attributes Additional attributes to attach to the span
     */
    fun beginSpan(
        name: String,
        attributes: Map<String, String> = emptyMap()
    ): SpanContext? {
        return telemetry?.beginSpan(name, attributes)
    }

    /**
     * Records a timer metric with the given duration.
     *
     * @param name Metric name (use Metrics constants for consistency)
     * @param millis Duration in milliseconds
     * @param attributes Additional attributes for context
     */
    fun recordTimer(
        name: String,
        millis: Long,
        attributes: Map<String, String> = emptyMap()
    ) {
        telemetry?.timer(name, millis, attributes)
    }

    /**
     * Records a gauge metric with the given value.
     *
     * @param name Metric name
     * @param value Current value
     * @param attributes Additional attributes for context
     */
    fun recordGauge(
        name: String,
        value: Double,
        attributes: Map<String, String> = emptyMap()
    ) {
        telemetry?.gauge(name, value, attributes)
    }

    /**
     * Increments a counter metric.
     *
     * @param name Metric name
     * @param delta Amount to increment (default: 1)
     * @param attributes Additional attributes for context
     */
    fun incrementCounter(
        name: String,
        delta: Long = 1,
        attributes: Map<String, String> = emptyMap()
    ) {
        telemetry?.counter(name, delta, attributes)
    }

    /**
     * Measures the execution time of a block and records it as a timer metric.
     * Also creates a tracing span for distributed tracing.
     *
     * @param metricName Metric name for the timer
     * @param spanName Span name for tracing (defaults to metricName)
     * @param attributes Additional attributes for context
     * @param block The code block to measure
     * @return The result of the block
     */
    inline fun <T> measure(
        metricName: String,
        spanName: String = metricName,
        attributes: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val startTime = SystemClock.elapsedRealtime()
        Trace.beginSection(spanName)
        val span = beginSpan(spanName, attributes)

        return try {
            block()
        } catch (e: Exception) {
            span?.recordError(e.message ?: "Unknown error")
            throw e
        } finally {
            span?.end()
            Trace.endSection()
            val duration = SystemClock.elapsedRealtime() - startTime
            recordTimer(metricName, duration, attributes)
        }
    }

    /**
     * Measures ML inference duration and records metrics.
     *
     * @param detectorType Type of detector (e.g., "object_detection", "barcode", "text")
     * @param block The ML inference code to measure
     * @return The result of the inference
     */
    inline fun <T> measureMlInference(
        detectorType: String,
        block: () -> T
    ): T {
        return measure(
            metricName = Metrics.ML_INFERENCE_LATENCY_MS,
            spanName = Spans.ML_INFERENCE,
            attributes = mapOf("detector_type" to detectorType),
            block = block
        )
    }

    /**
     * Measures frame analysis duration and records metrics.
     *
     * @param scanMode Current scan mode
     * @param block The frame analysis code to measure
     * @return The result of the analysis
     */
    inline fun <T> measureFrameAnalysis(
        scanMode: String,
        block: () -> T
    ): T {
        return measure(
            metricName = Metrics.FRAME_ANALYSIS_LATENCY_MS,
            spanName = Spans.FRAME_ANALYSIS,
            attributes = mapOf("scan_mode" to scanMode),
            block = block
        )
    }

    /**
     * Measures aggregation duration and records metrics.
     *
     * @param itemCount Number of items being aggregated
     * @param block The aggregation code to measure
     * @return The result of the aggregation
     */
    inline fun <T> measureAggregation(
        itemCount: Int,
        block: () -> T
    ): T {
        return measure(
            metricName = Metrics.AGGREGATION_LATENCY_MS,
            spanName = Spans.AGGREGATION,
            attributes = mapOf("item_count" to itemCount.toString()),
            block = block
        )
    }

    /**
     * Measures bitmap decode duration and records metrics.
     *
     * @param imageSize Size description (e.g., "1920x1080")
     * @param block The bitmap decode code to measure
     * @return The result (bitmap)
     */
    inline fun <T> measureBitmapDecode(
        imageSize: String = "unknown",
        block: () -> T
    ): T {
        return measure(
            metricName = Metrics.BITMAP_DECODE_LATENCY_MS,
            spanName = Spans.BITMAP_DECODE,
            attributes = mapOf("image_size" to imageSize),
            block = block
        )
    }

    /**
     * Measures overlay draw duration and records metrics.
     *
     * @param detectionCount Number of detections being drawn
     * @param block The draw code to measure
     * @return The result of the block
     */
    inline fun <T> measureOverlayDraw(
        detectionCount: Int,
        block: () -> T
    ): T {
        return measure(
            metricName = Metrics.OVERLAY_DRAW_LATENCY_MS,
            spanName = Spans.OVERLAY_DRAW,
            attributes = mapOf("detection_count" to detectionCount.toString()),
            block = block
        )
    }

    /**
     * Simple timer utility for manual timing without telemetry dependency.
     * Useful for low-level code where telemetry might not be initialized.
     */
    class Timer(private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()

        fun stop(attributes: Map<String, String> = emptyMap()): Long {
            val duration = SystemClock.elapsedRealtime() - startTime
            recordTimer(name, duration, attributes)
            return duration
        }
    }

    /**
     * Creates a timer for manual timing.
     */
    fun startTimer(name: String): Timer = Timer(name)
}
