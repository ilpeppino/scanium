package com.scanium.app.aggregation

import android.net.Uri
import com.scanium.app.items.ScannedItem
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange

/**
 * Represents a unique physical object aggregated from multiple detections.
 *
 * This is the core model for the real-time item aggregation system. Unlike ScannedItem
 * (which represents a single detection), AggregatedItem represents a persistent physical
 * object that may have been detected multiple times with varying tracking IDs, positions,
 * and confidence levels.
 *
 * Key features:
 * - Maintains stable identity across trackingId changes
 * - Tracks aggregation statistics (merge count, confidence history)
 * - Provides the "best" detection data for UI display
 * - Records timestamps for staleness detection
 *
 * @property aggregatedId Stable unique identifier for this aggregated item
 * @property category Item category (must match for merging)
 * @property labelText Primary label text (from highest confidence detection)
 * @property boundingBox Current bounding box (updated on merge, normalized coordinates)
 * @property thumbnail Best thumbnail image captured
 * @property maxConfidence Highest confidence seen across all detections
 * @property averageConfidence Running average of confidence scores
 * @property priceRange Price range (EUR low to high)
 * @property mergeCount Number of detections merged into this item
 * @property firstSeenTimestamp When first detected
 * @property lastSeenTimestamp When most recently updated
 * @property sourceDetectionIds Set of ScannedItem IDs merged into this aggregate
 * @property dominantColor Optional dominant color for visual similarity (future enhancement)
 */
data class AggregatedItem(
    val aggregatedId: String,
    var category: ItemCategory,
    var labelText: String,
    var boundingBox: NormalizedRect,
    var thumbnail: ImageRef?,
    var maxConfidence: Float,
    var averageConfidence: Float,
    var priceRange: Pair<Double, Double>,
    var estimatedPriceRange: PriceRange? = null,
    var priceEstimationStatus: PriceEstimationStatus = PriceEstimationStatus.Idle,
    var mergeCount: Int = 1,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    val sourceDetectionIds: MutableSet<String> = mutableSetOf(),
    var dominantColor: Int? = null,
// For future thumbnail-based similarity
    var enhancedCategory: ItemCategory? = null,
    var enhancedLabelText: String? = null,
    var enhancedPriceRange: Pair<Double, Double>? = null,
    var classificationConfidence: Float? = null,
    var fullImageUri: Uri? = null,
    var fullImagePath: String? = null,
    // Classification status tracking (Phase 9)
    var classificationStatus: String = "NOT_STARTED",
// NOT_STARTED, PENDING, SUCCESS, FAILED
    var domainCategoryId: String? = null,
    var classificationErrorMessage: String? = null,
    var classificationRequestId: String? = null,
    var thumbnailQuality: Float = 0f,
    /** Enriched attributes from cloud classification (brand, model, color, etc.) */
    var enrichedAttributes: Map<String, ItemAttribute> = emptyMap(),
    /** Raw vision attributes from Google Vision API (OCR, colors, logos, labels, candidates) */
    var visionAttributes: VisionAttributes = VisionAttributes.EMPTY,
    /**
     * Original detected attributes from backend classification, preserved for reference.
     * When user edits an attribute, this retains the original detected value so the UI
     * can show "Detected: X" alongside the user's override.
     */
    var detectedAttributes: Map<String, ItemAttribute> = emptyMap(),
    // Multi-object scanning fields (v7)
    /** User-editable attribute summary text */
    var attributesSummaryText: String = "",
    /** Flag indicating user has manually edited the summary text */
    var summaryTextUserEdited: Boolean = false,
    /** Additional photos attached to this item */
    var additionalPhotos: List<ItemPhoto> = emptyList(),
    /** ID linking items from same multi-object capture */
    var sourcePhotoId: String? = null,
    /** Status of each enrichment layer */
    var enrichmentStatus: EnrichmentLayerStatus = EnrichmentLayerStatus(),
    // Export Assistant fields (v8)
    /** AI-generated marketplace-ready title */
    var exportTitle: String? = null,
    /** AI-generated marketplace-ready description */
    var exportDescription: String? = null,
    /** AI-generated bullet highlights for listing */
    var exportBullets: List<String> = emptyList(),
    /** Timestamp when export fields were generated */
    var exportGeneratedAt: Long? = null,
    /** Whether the export was served from cache */
    var exportFromCache: Boolean = false,
    /** LLM model used to generate the export */
    var exportModel: String? = null,
    /** Confidence tier of the AI-generated export */
    var exportConfidenceTier: String? = null,
    // Quality Loop fields (v9)
    /** Completeness score (0-100) based on category-specific required attributes */
    var completenessScore: Int = 0,
    /** List of missing attribute keys ordered by importance */
    var missingAttributes: List<String> = emptyList(),
    /** Timestamp of the last enrichment operation */
    var lastEnrichedAt: Long? = null,
    /** Photo shot types that have been captured */
    var capturedShotTypes: List<String> = emptyList(),
    /** Whether the item meets the completeness threshold */
    var isReadyForListing: Boolean = false,
) {
    /**
     * Convert this aggregated item to a ScannedItem for UI display.
     *
     * This creates a "snapshot" of the aggregated item suitable for the existing UI layer.
     * The ScannedItem retains the stable aggregatedId and uses the best available data.
     */
    fun toScannedItem(): ScannedItem {
        return ScannedItem(
            id = aggregatedId,
            thumbnail = thumbnail,
            category = enhancedCategory ?: category,
            priceRange = enhancedPriceRange ?: priceRange,
            estimatedPriceRange = estimatedPriceRange,
            priceEstimationStatus = priceEstimationStatus,
            confidence = maxConfidence,
            timestamp = lastSeenTimestamp,
            boundingBox = boundingBox,
            labelText = enhancedLabelText ?: labelText,
            fullImageUri = fullImageUri,
            fullImagePath = fullImagePath,
            classificationStatus = classificationStatus,
            domainCategoryId = domainCategoryId,
            classificationErrorMessage = classificationErrorMessage,
            classificationRequestId = classificationRequestId,
            qualityScore = thumbnailQuality,
            attributes = enrichedAttributes,
            visionAttributes = visionAttributes,
            detectedAttributes = detectedAttributes,
            attributesSummaryText = attributesSummaryText,
            summaryTextUserEdited = summaryTextUserEdited,
            additionalPhotos = additionalPhotos,
            sourcePhotoId = sourcePhotoId,
            enrichmentStatus = enrichmentStatus,
            exportTitle = exportTitle,
            exportDescription = exportDescription,
            exportBullets = exportBullets,
            exportGeneratedAt = exportGeneratedAt,
            exportFromCache = exportFromCache,
            exportModel = exportModel,
            exportConfidenceTier = exportConfidenceTier,
            completenessScore = completenessScore,
            missingAttributes = missingAttributes,
            lastEnrichedAt = lastEnrichedAt,
            capturedShotTypes = capturedShotTypes,
            isReadyForListing = isReadyForListing,
        )
    }

    /**
     * Merge a new detection into this aggregated item.
     *
     * Updates statistics and keeps the "best" data:
     * - Uses highest confidence detection for label and thumbnail
     * - Updates bounding box to most recent position
     * - Maintains running average of confidence
     * - Tracks all source detection IDs
     *
     * @param detection The new detection to merge
     */
    fun merge(detection: ScannedItem) {
        // Update merge count
        mergeCount++

        // Add source ID
        sourceDetectionIds.add(detection.id)

        // Update confidence statistics
        averageConfidence = ((averageConfidence * (mergeCount - 1)) + detection.confidence) / mergeCount

        // If new detection has higher confidence, update primary data
        if (detection.confidence > maxConfidence) {
            maxConfidence = detection.confidence
            labelText = detection.labelText ?: labelText
        }

        // Update thumbnail logic: Prefer higher quality scores
        // If current thumbnail is missing, take the new one
        // If new one has significantly better quality score, take it
        // If quality is similar, default to high confidence check (already handled implicitly if we track maxConfidence separately? No)

        val newThumbnail = detection.thumbnail
        if (newThumbnail != null) {
            val isBetterQuality = detection.qualityScore > thumbnailQuality
            val isFirstThumbnail = thumbnail == null

            // Allow update if better quality OR it's the first one
            // Also consider confidence: don't replace a high-confidence sharp image with a low-confidence sharp image?
            // Actually, quality score (sharpness) is usually king for visual search.
            if (isFirstThumbnail || isBetterQuality) {
                thumbnail = newThumbnail
                thumbnailQuality = detection.qualityScore
            }
        }

        // Always update bounding box to latest position (object may have moved)
        detection.boundingBox?.let { boundingBox = it }

        // Update price range (take the wider range if different)
        val newMin = minOf(priceRange.first, detection.priceRange.first)
        val newMax = maxOf(priceRange.second, detection.priceRange.second)
        priceRange = Pair(newMin, newMax)

        if (detection.estimatedPriceRange != null) {
            estimatedPriceRange = detection.estimatedPriceRange
            priceEstimationStatus = detection.priceEstimationStatus
        }

        detection.fullImageUri?.let { fullImageUri = it }
        detection.fullImagePath?.let { path ->
            if (path.isNotBlank()) {
                fullImagePath = path
            }
        }

        // Update last seen timestamp
        lastSeenTimestamp = System.currentTimeMillis()
    }

    /**
     * Calculate the center point of the bounding box.
     */
    fun getCenterPoint(): Pair<Float, Float> {
        return Pair(
            (boundingBox.left + boundingBox.right) / 2f,
            (boundingBox.top + boundingBox.bottom) / 2f,
        )
    }

    /**
     * Get normalized bounding box area.
     */
    fun getBoxArea(): Float {
        return boundingBox.area
    }

    /**
     * Check if this item is stale (hasn't been seen recently).
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if item is older than maxAgeMs
     */
    fun isStale(maxAgeMs: Long): Boolean {
        return (System.currentTimeMillis() - lastSeenTimestamp) >= maxAgeMs
    }

    /**
     * Cleanup resources (call before removing from memory).
     * Note: ImageRef is immutable and doesn't require explicit cleanup.
     */
    fun cleanup() {
        thumbnail = null
        fullImageUri = null
    }

    fun updatePriceEstimation(
        status: PriceEstimationStatus,
        priceRange: PriceRange?,
    ) {
        priceEstimationStatus = status
        priceRange?.let { range ->
            estimatedPriceRange = range
            this.priceRange = range.toPair()
        }
    }
}
