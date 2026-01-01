package com.scanium.app.camera.detection

import android.os.SystemClock
import com.scanium.core.models.geometry.NormalizedRect

/**
 * Deduplication helper for detection results.
 *
 * Tracks recently seen detections to avoid emitting duplicates within a time window.
 * Uses a combination of:
 * - Spatial proximity (bounding box IoU)
 * - Category matching
 * - Time-based expiry
 *
 * Thread-safe: Uses synchronized access to internal state.
 */
class DedupeHelper(
    private val config: DedupeConfig = DedupeConfig(),
) {
    companion object {
        private const val TAG = "DedupeHelper"
    }

    /** Recently seen items, keyed by generated dedupe key */
    private val recentlySeen = mutableMapOf<String, SeenEntry>()

    /** Recently seen barcodes, keyed by rawValue for value-based deduplication */
    private val recentlySeenBarcodes = mutableMapOf<String, BarcodeSeenEntry>()

    /** Lock for thread-safe access */
    private val lock = Any()

    /**
     * Check if an item has been seen recently and should be deduplicated.
     *
     * @param detectorType Source detector
     * @param category Category/label of the detection
     * @param boundingBox Normalized bounding box (0-1 coordinates)
     * @param currentTimeMs Current timestamp
     * @return true if this is a duplicate that should be skipped
     */
    fun isDuplicate(
        detectorType: DetectorType,
        category: String,
        boundingBox: NormalizedRect,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        synchronized(lock) {
            // Clean expired entries periodically
            if (shouldCleanup()) {
                cleanupExpired(currentTimeMs)
            }

            // Check for spatial duplicates in the same category
            val expiryWindowMs = config.expiryWindowMs[detectorType] ?: config.defaultExpiryWindowMs

            for ((_, entry) in recentlySeen) {
                // Skip entries from different detector types
                if (entry.detectorType != detectorType) continue

                // Skip entries with different categories
                if (entry.category != category) continue

                // Skip expired entries
                if (currentTimeMs - entry.lastSeenMs > expiryWindowMs) continue

                // Check spatial overlap using IoU
                val iou = calculateIoU(entry.boundingBox, boundingBox)
                if (iou >= config.iouThreshold) {
                    // Update last seen time for this entry
                    val key = generateKey(entry.detectorType, entry.category, entry.boundingBox)
                    recentlySeen[key] =
                        entry.copy(
                            lastSeenMs = currentTimeMs,
                            seenCount = entry.seenCount + 1,
                        )
                    return true
                }
            }

            return false
        }
    }

    /**
     * Record an item as seen.
     * Call this after successfully processing a detection to track it for deduplication.
     */
    fun recordSeen(
        detectorType: DetectorType,
        category: String,
        boundingBox: NormalizedRect,
        itemId: String? = null,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ) {
        synchronized(lock) {
            val key = generateKey(detectorType, category, boundingBox)
            val existing = recentlySeen[key]

            recentlySeen[key] =
                SeenEntry(
                    detectorType = detectorType,
                    category = category,
                    boundingBox = boundingBox,
                    itemId = itemId,
                    firstSeenMs = existing?.firstSeenMs ?: currentTimeMs,
                    lastSeenMs = currentTimeMs,
                    seenCount = (existing?.seenCount ?: 0) + 1,
                )
        }
    }

    /**
     * Check and record in one atomic operation.
     *
     * @return true if this is a NEW item (not a duplicate), false if duplicate
     */
    fun checkAndRecord(
        detectorType: DetectorType,
        category: String,
        boundingBox: NormalizedRect,
        itemId: String? = null,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        synchronized(lock) {
            val isDupe = isDuplicate(detectorType, category, boundingBox, currentTimeMs)
            if (!isDupe) {
                recordSeen(detectorType, category, boundingBox, itemId, currentTimeMs)
            }
            return !isDupe
        }
    }

    /**
     * Generate a stable key for deduplication lookup.
     * Uses quantized bounding box position to allow for small movement.
     */
    private fun generateKey(
        detectorType: DetectorType,
        category: String,
        boundingBox: NormalizedRect,
    ): String {
        // Quantize position to grid cells for fuzzy matching
        val gridSize = config.spatialGridSize
        val centerX = ((boundingBox.left + boundingBox.right) / 2 * gridSize).toInt()
        val centerY = ((boundingBox.top + boundingBox.bottom) / 2 * gridSize).toInt()

        return "${detectorType.name}_${category}_${centerX}_$centerY"
    }

    /**
     * Calculate Intersection over Union between two bounding boxes.
     */
    private fun calculateIoU(
        a: NormalizedRect,
        b: NormalizedRect,
    ): Float {
        val intersectLeft = maxOf(a.left, b.left)
        val intersectTop = maxOf(a.top, b.top)
        val intersectRight = minOf(a.right, b.right)
        val intersectBottom = minOf(a.bottom, b.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val unionArea = a.area + b.area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    /** Counter for periodic cleanup */
    private var operationCount = 0

    private fun shouldCleanup(): Boolean {
        operationCount++
        return operationCount >= config.cleanupInterval
    }

    /**
     * Remove expired entries from the map.
     */
    private fun cleanupExpired(currentTimeMs: Long) {
        operationCount = 0

        val maxExpiryMs = config.expiryWindowMs.values.maxOrNull() ?: config.defaultExpiryWindowMs
        val iterator = recentlySeen.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (currentTimeMs - entry.lastSeenMs > maxExpiryMs) {
                iterator.remove()
            }
        }
    }

    // ==================== Barcode-Specific Deduplication ====================

    /**
     * Check if a barcode with this rawValue has been seen recently.
     * Uses value-based deduplication (not spatial) for barcodes.
     *
     * @param rawValue The raw barcode value
     * @param format The barcode format (e.g., FORMAT_QR_CODE, FORMAT_EAN_13)
     * @param currentTimeMs Current timestamp
     * @return true if this barcode is a duplicate that should be skipped
     */
    fun isBarcodeDuplicate(
        rawValue: String,
        format: Int,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        synchronized(lock) {
            // Clean expired barcode entries periodically
            if (shouldCleanup()) {
                cleanupExpiredBarcodes(currentTimeMs)
            }

            val expiryWindowMs =
                config.expiryWindowMs[DetectorType.BARCODE]
                    ?: config.defaultExpiryWindowMs

            val existing = recentlySeenBarcodes[rawValue]
            if (existing != null) {
                // Check if not expired
                if (currentTimeMs - existing.lastSeenMs <= expiryWindowMs) {
                    // Update last seen time
                    recentlySeenBarcodes[rawValue] =
                        existing.copy(
                            lastSeenMs = currentTimeMs,
                            seenCount = existing.seenCount + 1,
                        )
                    android.util.Log.d(TAG, "[DEDUPE_HIT] Barcode duplicate: value=$rawValue, seenCount=${existing.seenCount + 1}")
                    return true
                }
            }

            return false
        }
    }

    /**
     * Record a barcode as seen.
     * Call this after successfully processing a barcode detection.
     *
     * @param rawValue The raw barcode value
     * @param format The barcode format
     * @param itemId Optional ID of the created ScannedItem
     * @param currentTimeMs Current timestamp
     */
    fun recordBarcodeSeen(
        rawValue: String,
        format: Int,
        itemId: String? = null,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ) {
        synchronized(lock) {
            val existing = recentlySeenBarcodes[rawValue]

            recentlySeenBarcodes[rawValue] =
                BarcodeSeenEntry(
                    rawValue = rawValue,
                    format = format,
                    itemId = itemId,
                    firstSeenMs = existing?.firstSeenMs ?: currentTimeMs,
                    lastSeenMs = currentTimeMs,
                    seenCount = (existing?.seenCount ?: 0) + 1,
                )
            android.util.Log.d(TAG, "[BARCODE_SEEN] Recorded barcode: value=$rawValue, format=$format")
        }
    }

    /**
     * Check and record barcode in one atomic operation.
     *
     * @param rawValue The raw barcode value
     * @param format The barcode format
     * @param itemId Optional ID for the ScannedItem
     * @param currentTimeMs Current timestamp
     * @return true if this is a NEW barcode (not a duplicate), false if duplicate
     */
    fun checkAndRecordBarcode(
        rawValue: String,
        format: Int,
        itemId: String? = null,
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        synchronized(lock) {
            val isDupe = isBarcodeDuplicate(rawValue, format, currentTimeMs)
            if (!isDupe) {
                recordBarcodeSeen(rawValue, format, itemId, currentTimeMs)
                android.util.Log.d(TAG, "[BARCODE_NEW] New barcode detected: value=$rawValue, format=$format")
            }
            return !isDupe
        }
    }

    /**
     * Remove expired barcode entries.
     */
    private fun cleanupExpiredBarcodes(currentTimeMs: Long) {
        val expiryWindowMs =
            config.expiryWindowMs[DetectorType.BARCODE]
                ?: config.defaultExpiryWindowMs

        val iterator = recentlySeenBarcodes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (currentTimeMs - entry.lastSeenMs > expiryWindowMs) {
                iterator.remove()
            }
        }
    }

    /**
     * Reset barcode deduplication state.
     */
    fun resetBarcodes() {
        synchronized(lock) {
            recentlySeenBarcodes.clear()
        }
    }

    // ==================== General Reset Methods ====================

    /**
     * Reset deduplication state for a specific detector.
     */
    fun reset(detectorType: DetectorType) {
        synchronized(lock) {
            val iterator = recentlySeen.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.detectorType == detectorType) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Reset all deduplication state.
     */
    fun resetAll() {
        synchronized(lock) {
            recentlySeen.clear()
            recentlySeenBarcodes.clear()
            operationCount = 0
        }
    }

    /**
     * Get debug statistics.
     */
    fun getStats(): DedupeStats {
        synchronized(lock) {
            val byType =
                DetectorType.entries.associateWith { type ->
                    recentlySeen.values.count { it.detectorType == type }
                }
            return DedupeStats(
                totalTracked = recentlySeen.size,
                trackedByType = byType,
                trackedBarcodes = recentlySeenBarcodes.size,
            )
        }
    }
}

/**
 * Configuration for deduplication behavior.
 */
data class DedupeConfig(
    /** Time window for considering items as duplicates (per detector type) */
    val expiryWindowMs: Map<DetectorType, Long> =
        mapOf(
            DetectorType.OBJECT to 3000L,
// 3 seconds for objects
            DetectorType.BARCODE to 5000L,
// 5 seconds for barcodes (user might rescan)
            DetectorType.DOCUMENT to 4000L,
// 4 seconds for documents
        ),
    /** Default expiry window if not specified per type */
    val defaultExpiryWindowMs: Long = 3000L,
    /** Minimum IoU to consider two detections as the same item */
    val iouThreshold: Float = 0.3f,
    /** Grid size for spatial quantization (higher = more granular) */
    val spatialGridSize: Int = 10,
    /** Number of operations between cleanup runs */
    val cleanupInterval: Int = 50,
)

/**
 * Entry tracking a recently seen detection.
 */
private data class SeenEntry(
    val detectorType: DetectorType,
    val category: String,
    val boundingBox: NormalizedRect,
    val itemId: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val seenCount: Int,
)

/**
 * Entry tracking a recently seen barcode by its raw value.
 */
private data class BarcodeSeenEntry(
    val rawValue: String,
    val format: Int,
    val itemId: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val seenCount: Int,
)

/**
 * Deduplication statistics for debugging.
 */
data class DedupeStats(
    val totalTracked: Int,
    val trackedByType: Map<DetectorType, Int>,
    val trackedBarcodes: Int = 0,
)
