package com.scanium.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Specifies how an attribute should be extracted from the scanned item.
 *
 * This enum defines the extraction pipeline for domain attributes:
 * - OCR: Extract from text visible in the image
 * - BARCODE: Extract from barcode/QR code data
 * - CLIP: Infer from on-device CLIP classification
 * - CLOUD: Infer from cloud-based classifier
 * - HEURISTIC: Derive from bounding box or other metadata
 * - NONE: Manual entry or not extracted
 */
@Serializable
enum class ExtractionMethod {
    /** Extract text using OCR (e.g., brand names, model numbers) */
    OCR,

    /** Extract from barcode/QR code data (e.g., ISBN, SKU) */
    BARCODE,

    /** Infer using on-device CLIP model (e.g., color, material) */
    CLIP,

    /** Infer using cloud-based classifier (e.g., condition assessment) */
    CLOUD,

    /** Derive from metadata like bounding box size (e.g., size estimation) */
    HEURISTIC,

    /** Not automatically extracted; requires manual input */
    NONE,
}
