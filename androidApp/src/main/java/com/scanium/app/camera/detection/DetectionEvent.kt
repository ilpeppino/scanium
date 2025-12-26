package com.scanium.app.camera.detection

import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.DetectionResult

/**
 * Events emitted by the DetectionRouter.
 *
 * Sealed class hierarchy allows type-safe handling of different detection types
 * while maintaining a unified event stream from the router.
 */
sealed class DetectionEvent {

    /** Timestamp when this event was produced (System.currentTimeMillis()) */
    abstract val timestampMs: Long

    /** Source detector that produced this event */
    abstract val source: DetectorType

    /**
     * Object detection results from ML Kit.
     * Contains both confirmed ScannedItems and raw DetectionResults for overlay.
     */
    data class ObjectDetected(
        override val timestampMs: Long,
        override val source: DetectorType = DetectorType.OBJECT,
        val items: List<ScannedItem>,
        val detectionResults: List<DetectionResult>
    ) : DetectionEvent()

    /**
     * Barcode/QR code detection results.
     * Future: Will contain barcode value, format, and bounding box.
     */
    data class BarcodeDetected(
        override val timestampMs: Long,
        override val source: DetectorType = DetectorType.BARCODE,
        val items: List<ScannedItem>
    ) : DetectionEvent()

    /**
     * Document candidate detection results.
     * Future: Will contain text regions and document boundaries.
     */
    data class DocumentDetected(
        override val timestampMs: Long,
        override val source: DetectorType = DetectorType.DOCUMENT,
        val items: List<ScannedItem>
    ) : DetectionEvent()

    /**
     * No-op event indicating the frame was skipped due to throttling.
     * Useful for debugging and metrics.
     */
    data class Throttled(
        override val timestampMs: Long,
        override val source: DetectorType,
        val reason: ThrottleReason
    ) : DetectionEvent()
}

/**
 * Reasons why a detection was throttled.
 */
enum class ThrottleReason {
    /** Minimum interval between detections not met */
    INTERVAL_NOT_MET,

    /** Another heavy detector is currently running */
    DETECTOR_BUSY,

    /** Frame quality too low (blur, exposure) */
    LOW_QUALITY_FRAME
}
