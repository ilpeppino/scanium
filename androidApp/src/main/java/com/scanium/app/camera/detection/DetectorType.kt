package com.scanium.app.camera.detection

/**
 * Types of detectors supported by the DetectionRouter.
 *
 * Each detector type can have independent throttling configuration
 * and produces its own event types.
 */
enum class DetectorType {
    /**
     * ML Kit object detection and classification.
     * Heavy detector - should not run every frame.
     */
    OBJECT,

    /**
     * Lightweight barcode/QR code detection.
     * Future: Will use ML Kit Barcode Scanner.
     */
    BARCODE,

    /**
     * Document candidate detection (text regions, receipts).
     * Future: Will use ML Kit Text Recognition for region detection.
     */
    DOCUMENT,
}
