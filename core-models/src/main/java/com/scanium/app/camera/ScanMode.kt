package com.scanium.app.camera

/**
 * Scanning modes available in the camera screen.
 */
enum class ScanMode(val displayName: String) {
    /**
     * Object Detection mode using ML Kit Object Detection & Tracking.
     * Detects and classifies everyday objects.
     */
    OBJECT_DETECTION("Items"),

    /**
     * Barcode Scanning mode using ML Kit Barcode Scanning.
     * Scans barcodes and QR codes.
     */
    BARCODE("Barcode"),

    /**
     * Document/Text Scanning mode using ML Kit Text Recognition.
     * Detects and extracts text from documents and images.
     */
    DOCUMENT_TEXT("Document"),
}
