package com.scanium.app.aggregation

import com.scanium.app.items.ScannedItem
import com.scanium.core.tracking.Logger
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import java.util.UUID

/**
 * Real-time item aggregation engine for scanning mode.
 *
 * This component solves the duplicate detection problem by aggregating similar detections
 * into persistent AggregatedItems. Unlike the previous strict deduplication approach,
 * this system uses weighted similarity scoring that works even when:
 * - ML Kit trackingIds change frequently
 * - Bounding boxes shift during camera movement
 * - Users pan slowly across objects
 *
 * Core responsibilities:
 * - Maintain collection of AggregatedItems representing unique physical objects
 * - Compare new detections against existing items using multi-factor similarity
 * - Merge similar detections or create new aggregated items
 * - Provide tunable thresholds for different use cases
 * - Comprehensive logging for debugging and tuning
 *
 * Architecture:
 * - Delegates similarity scoring to SimilarityScorer (pure calculation logic)
 * - Delegates aggregation decisions to AggregationPolicy (merge vs. create logic)
 * - Focuses on coordination and state management
 *
 * @property config Configuration for aggregation behavior
 * @property logger Platform-specific logger implementation
 */
class ItemAggregator(
    private val config: AggregationConfig = AggregationConfig(),
    private val logger: Logger = Logger.NONE,
) {
    companion object {
        private const val TAG = "ItemAggregator"
    }

    // Active aggregated items, keyed by aggregatedId
    private val aggregatedItems = mutableMapOf<String, AggregatedItem>()

    // Similarity scorer for calculating detection-item similarity
    private val similarityScorer = SimilarityScorer(config)

    // Aggregation policy for merge/create decisions
    private val aggregationPolicy = AggregationPolicy(config, similarityScorer)

    /**
     * Update the similarity threshold dynamically.
     *
     * This allows real-time adjustment of the threshold without recreating the aggregator.
     * Useful for debugging and live tuning.
     *
     * @param threshold New threshold value (0-1), or null to use config default
     */
    fun updateSimilarityThreshold(threshold: Float?) {
        val oldThreshold = getCurrentSimilarityThreshold()
        aggregationPolicy.updateSimilarityThreshold(threshold)
        val newThreshold = getCurrentSimilarityThreshold()

        logger.w(TAG, "╔═══════════════════════════════════════════════════════════════")
        logger.w(TAG, "║ AGGREGATOR THRESHOLD UPDATED: $oldThreshold → $newThreshold")
        logger.w(TAG, "║ Dynamic override: $threshold | Config default: ${config.similarityThreshold}")
        logger.w(TAG, "╚═══════════════════════════════════════════════════════════════")
    }

    /**
     * Get the current effective similarity threshold.
     */
    fun getCurrentSimilarityThreshold(): Float {
        return aggregationPolicy.getCurrentSimilarityThreshold()
    }

    /**
     * Process a new detection and return the resulting aggregated item.
     *
     * This is the main entry point for the aggregation system. For each detection:
     * 1. Find the most similar existing aggregated item (if any)
     * 2. If similarity >= threshold: merge into existing item
     * 3. If similarity < threshold: create new aggregated item
     *
     * @param detection The new ScannedItem detection to process
     * @return The AggregatedItem (either merged or newly created)
     */
    fun processDetection(detection: ScannedItem): AggregatedItem {
        logger.i(TAG, ">>> processDetection: id=${detection.id}, category=${detection.category}, confidence=${detection.confidence}")

        // Use aggregation policy to decide on merge vs. create
        val aggregationDecision = aggregationPolicy.decideAggregation(detection, aggregatedItems.values)
        val matchResult = aggregationDecision.matchResult
        val currentThreshold = aggregationDecision.threshold

        logger.i(TAG, "    Using THRESHOLD=$currentThreshold")

        when (aggregationDecision.decision) {
            MergeDecision.MERGE -> {
                val bestMatch = matchResult.bestMatch!!
                // Merge into existing item
                logger.w(TAG, "    ✓ MERGE: detection ${detection.id} → aggregated ${bestMatch.aggregatedId}")
                logger.w(TAG, "    Similarity ${matchResult.similarity} >= threshold $currentThreshold")
                logSimilarityBreakdown(detection, bestMatch, matchResult.similarity)

                bestMatch.merge(detection)
                return bestMatch
            }

            MergeDecision.CREATE_NEW -> {
                // Create new aggregated item
                val newItem = createAggregatedItem(detection)
                aggregatedItems[newItem.aggregatedId] = newItem

                if (matchResult.bestMatch != null) {
                    logger.w(TAG, "    ✗ CREATE NEW: similarity ${matchResult.similarity} < threshold $currentThreshold")
                    logSimilarityBreakdown(detection, matchResult.bestMatch, matchResult.similarity)
                } else {
                    logger.i(TAG, "    ✗ CREATE NEW: no existing items to compare")
                }

                logger.i(TAG, "    Created aggregated item ${newItem.aggregatedId}")
                return newItem
            }
        }
    }

    /**
     * Process multiple detections in batch.
     *
     * @param detections List of new detections
     * @return List of resulting aggregated items (may be newly created or existing)
     */
    fun processDetections(detections: List<ScannedItem>): List<AggregatedItem> {
        logger.i(TAG, ">>> processDetections BATCH: ${detections.size} detections")
        return detections.map { processDetection(it) }
    }

    /**
     * Seed the aggregator from persisted ScannedItems without re-aggregating.
     *
     * This preserves stable IDs across app restarts while avoiding unintended merges.
     */
    @Synchronized
    fun seedFromScannedItems(items: List<ScannedItem>) {
        if (items.isEmpty()) return

        for (item in items) {
            val aggregatedItem =
                AggregatedItem(
                    aggregatedId = item.id,
                    category = item.category,
                    labelText = item.labelText ?: "",
                    boundingBox = item.boundingBox ?: NormalizedRect(0f, 0f, 0f, 0f),
                    thumbnail = item.thumbnailRef ?: item.thumbnail,
                    maxConfidence = item.confidence,
                    averageConfidence = item.confidence,
                    priceRange = item.priceRange,
                    mergeCount = 1,
                    firstSeenTimestamp = item.timestamp,
                    lastSeenTimestamp = item.timestamp,
                    sourceDetectionIds = mutableSetOf(item.id),
                    classificationStatus = item.classificationStatus,
                    domainCategoryId = item.domainCategoryId,
                    classificationErrorMessage = item.classificationErrorMessage,
                    classificationRequestId = item.classificationRequestId,
                    enrichedAttributes = item.attributes,
                    visionAttributes = item.visionAttributes,
                    detectedAttributes = item.detectedAttributes,
                )
            aggregatedItems[item.id] = aggregatedItem
        }
    }

    /**
     * Create a new AggregatedItem from a detection.
     */
    private fun createAggregatedItem(detection: ScannedItem): AggregatedItem {
        // Preserve provided detection IDs so downstream consumers (e.g., listing updates) can reference them.
        val aggregatedId = detection.id.ifBlank { "agg_${UUID.randomUUID()}" }

        return AggregatedItem(
            aggregatedId = aggregatedId,
            category = detection.category,
            labelText = detection.labelText ?: "",
            boundingBox = detection.boundingBox ?: NormalizedRect(0f, 0f, 0f, 0f),
            // Prefer thumbnailRef (WYSIWYG)
            thumbnail = detection.thumbnailRef ?: detection.thumbnail,
            maxConfidence = detection.confidence,
            averageConfidence = detection.confidence,
            priceRange = detection.priceRange,
            mergeCount = 1,
            firstSeenTimestamp = detection.timestamp,
            lastSeenTimestamp = detection.timestamp,
            sourceDetectionIds = mutableSetOf(detection.id),
            fullImageUri = detection.fullImageUri,
            fullImagePath = detection.fullImagePath,
            // Initialize quality score
            thumbnailQuality = detection.qualityScore,
            classificationStatus = detection.classificationStatus,
            domainCategoryId = detection.domainCategoryId,
            classificationErrorMessage = detection.classificationErrorMessage,
            classificationRequestId = detection.classificationRequestId,
            enrichedAttributes = detection.attributes,
            visionAttributes = detection.visionAttributes,
            detectedAttributes = detection.detectedAttributes,
        )
    }

    /**
     * Get all current aggregated items.
     * Thread-safe via synchronized block to prevent concurrent modification.
     */
    @Synchronized
    fun getAllItems(): List<AggregatedItem> {
        return aggregatedItems.values.toList()
    }

    /**
     * Get aggregated items as ScannedItems for UI compatibility.
     * Thread-safe via synchronized block to prevent concurrent modification.
     */
    @Synchronized
    fun getScannedItems(): List<ScannedItem> {
        return aggregatedItems.values.map { it.toScannedItem() }
    }

    /**
     * Remove stale items that haven't been seen recently.
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return Number of items removed
     */
    fun removeStaleItems(maxAgeMs: Long): Int {
        val toRemove = aggregatedItems.values.filter { it.isStale(maxAgeMs) }

        for (item in toRemove) {
            logger.d(TAG, "Removing stale item ${item.aggregatedId} (age=${System.currentTimeMillis() - item.lastSeenTimestamp}ms)")
            item.cleanup()
            aggregatedItems.remove(item.aggregatedId)
        }

        return toRemove.size
    }

    /**
     * Remove a specific aggregated item.
     */
    fun removeItem(aggregatedId: String) {
        aggregatedItems[aggregatedId]?.cleanup()
        aggregatedItems.remove(aggregatedId)
    }

    /**
     * Returns a snapshot of aggregated items for downstream components (e.g., classification).
     * Thread-safe via synchronized block to prevent concurrent modification.
     */
    @Synchronized
    fun getAggregatedItems(): List<AggregatedItem> = aggregatedItems.values.toList()

    /**
     * Applies enhanced classification results without altering tracking behavior.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * When [isFromBackend] is true (default), this also stores the attributes in
     * [detectedAttributes] for reference. When false (user edit), only [enrichedAttributes]
     * is updated, preserving the original detected values for UI comparison.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param category Optional category from classification
     * @param label Optional label from classification
     * @param priceRange Optional price range from classification
     * @param classificationConfidence Optional confidence score
     * @param attributes Optional enriched attributes (brand, model, color, etc.)
     * @param visionAttributes Optional raw vision data (OCR, colors, logos)
     * @param isFromBackend If true, this is a backend classification result and attributes
     *                      should be stored in detectedAttributes as well. If false, this is
     *                      a user edit and only enrichedAttributes is updated.
     */
    @Synchronized
    fun applyEnhancedClassification(
        aggregatedId: String,
        category: ItemCategory?,
        label: String?,
        priceRange: Pair<Double, Double>?,
        classificationConfidence: Float? = null,
        attributes: Map<String, ItemAttribute>? = null,
        visionAttributes: VisionAttributes? = null,
        isFromBackend: Boolean = true,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            category?.let { item.enhancedCategory = it }
            label?.let { item.enhancedLabelText = it }
            priceRange?.let { item.enhancedPriceRange = it }
            classificationConfidence?.let { item.classificationConfidence = it }
            attributes?.let { newAttrs ->
                // If from backend, store in detectedAttributes as the original reference
                if (isFromBackend) {
                    item.detectedAttributes = newAttrs
                    // Merge with existing attributes, preserving user overrides (source = "user")
                    val mergedAttributes = item.enrichedAttributes.toMutableMap()
                    for ((key, value) in newAttrs) {
                        val existing = mergedAttributes[key]
                        // Only update if not a user override
                        if (existing?.source != "user") {
                            mergedAttributes[key] = value
                        }
                    }
                    item.enrichedAttributes = mergedAttributes
                } else {
                    // User edit - only update enrichedAttributes, preserve detectedAttributes
                    item.enrichedAttributes = newAttrs
                }
            }
            visionAttributes?.let { item.visionAttributes = it }
        }
    }

    /**
     * Updates the thumbnail reference for an aggregated item.
     */
    @Synchronized
    fun updateThumbnail(
        aggregatedId: String,
        thumbnail: ImageRef?,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.thumbnail = thumbnail
        }
    }

    /**
     * Updates classification status for an aggregated item.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param status Classification status (e.g., "PENDING", "SUCCESS", "FAILED")
     * @param domainCategoryId Optional domain category ID from classification
     * @param errorMessage Optional error message if classification failed
     * @param requestId Optional request ID for tracking
     */
    @Synchronized
    fun updateClassificationStatus(
        aggregatedId: String,
        status: String,
        domainCategoryId: String? = null,
        errorMessage: String? = null,
        requestId: String? = null,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.classificationStatus = status
            item.domainCategoryId = domainCategoryId
            item.classificationErrorMessage = errorMessage
            item.classificationRequestId = requestId
        }
    }

    @Synchronized
    fun updatePriceEstimation(
        aggregatedId: String,
        status: PriceEstimationStatus,
        priceRange: PriceRange? = null,
    ) {
        aggregatedItems[aggregatedId]?.updatePriceEstimation(status, priceRange)
    }

    /**
     * Marks multiple items as pending classification.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * @param aggregatedIds List of aggregated item IDs to mark as pending
     */
    @Synchronized
    fun markClassificationPending(aggregatedIds: List<String>) {
        aggregatedIds.forEach { id ->
            aggregatedItems[id]?.let { item ->
                item.classificationStatus = "PENDING"
                item.classificationErrorMessage = null
            }
        }
    }

    /**
     * Clear all aggregated items (call when starting new session).
     */
    fun reset() {
        logger.i(TAG, "Resetting aggregator: clearing ${aggregatedItems.size} items")

        for (item in aggregatedItems.values) {
            item.cleanup()
        }

        aggregatedItems.clear()
    }

    /**
     * Get aggregation statistics for monitoring.
     * Thread-safe via synchronized block to prevent concurrent modification.
     */
    @Synchronized
    fun getStats(): AggregationStats {
        val totalMerges = aggregatedItems.values.sumOf { it.mergeCount - 1 }
        val avgMergesPerItem =
            if (aggregatedItems.isNotEmpty()) {
                totalMerges.toFloat() / aggregatedItems.size
            } else {
                0f
            }

        return AggregationStats(
            totalItems = aggregatedItems.size,
            totalMerges = totalMerges,
            averageMergesPerItem = avgMergesPerItem,
        )
    }

    /**
     * Log detailed similarity breakdown for debugging.
     * Uses the SimilarityScorer to get detailed component scores.
     */
    private fun logSimilarityBreakdown(
        detection: ScannedItem,
        item: AggregatedItem,
        finalScore: Float,
    ) {
        val breakdown = similarityScorer.getSimilarityBreakdown(detection, item)
        val detectionLabel = detection.labelText ?: ""
        val itemLabel = item.labelText

        logger.d(TAG, "    Similarity breakdown:")
        logger.d(TAG, "      - Category match: ${breakdown.categoryMatch} (${detection.category} vs ${item.category})")
        logger.d(TAG, "      - Label similarity: ${breakdown.labelSimilarity} ('$detectionLabel' vs '$itemLabel')")
        logger.d(TAG, "      - Size similarity: ${breakdown.sizeSimilarity}")
        logger.d(TAG, "      - Distance similarity: ${breakdown.distanceSimilarity}")
        logger.d(TAG, "      - Final weighted score: $finalScore")
    }

    /**
     * Updates the enrichment status for an aggregated item.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param status The new enrichment layer status
     */
    @Synchronized
    fun updateEnrichmentStatus(
        aggregatedId: String,
        status: com.scanium.shared.core.models.items.EnrichmentLayerStatus,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.enrichmentStatus = status
        }
    }

    /**
     * Updates the summary text for an aggregated item.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param summaryText The new summary text
     * @param userEdited Whether the user manually edited the text
     */
    @Synchronized
    fun updateSummaryText(
        aggregatedId: String,
        summaryText: String,
        userEdited: Boolean,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.attributesSummaryText = summaryText
            item.summaryTextUserEdited = userEdited
        }
    }

    /**
     * Adds a photo to an aggregated item.
     * Thread-safe via synchronized block to prevent concurrent modification.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param photo The photo to add
     */
    @Synchronized
    fun addPhotoToItem(
        aggregatedId: String,
        photo: com.scanium.shared.core.models.items.ItemPhoto,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.additionalPhotos = item.additionalPhotos + photo
        }
    }

    /**
     * Remove multiple photos from an aggregated item by their IDs.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param photoIds Set of photo IDs to remove
     */
    @Synchronized
    fun removePhotosFromItem(
        aggregatedId: String,
        photoIds: Set<String>,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.additionalPhotos = item.additionalPhotos.filter { it.id !in photoIds }
        }
    }

    /**
     * Update export assistant fields for an aggregated item.
     */
    fun updateExportFields(
        aggregatedId: String,
        exportTitle: String?,
        exportDescription: String?,
        exportBullets: List<String>,
        exportGeneratedAt: Long,
        exportFromCache: Boolean,
        exportModel: String?,
        exportConfidenceTier: String?,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.exportTitle = exportTitle
            item.exportDescription = exportDescription
            item.exportBullets = exportBullets
            item.exportGeneratedAt = exportGeneratedAt
            item.exportFromCache = exportFromCache
            item.exportModel = exportModel
            item.exportConfidenceTier = exportConfidenceTier
        }
    }

    /**
     * Update completeness evaluation fields for an aggregated item.
     */
    fun updateCompleteness(
        aggregatedId: String,
        completenessScore: Int,
        missingAttributes: List<String>,
        isReadyForListing: Boolean,
        lastEnrichedAt: Long?,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            item.completenessScore = completenessScore
            item.missingAttributes = missingAttributes
            item.isReadyForListing = isReadyForListing
            if (lastEnrichedAt != null) {
                item.lastEnrichedAt = lastEnrichedAt
            }
        }
    }

    /**
     * Add a captured shot type to an aggregated item.
     */
    fun addCapturedShotType(
        aggregatedId: String,
        shotType: String,
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            if (shotType !in item.capturedShotTypes) {
                item.capturedShotTypes = item.capturedShotTypes + shotType
            }
        }
    }
}

/**
 * Configuration parameters for item aggregation.
 */
data class AggregationConfig(
    /** Minimum similarity score (0-1) for merging detections */
    val similarityThreshold: Float = 0.6f,
    /** Maximum normalized center distance ratio (0-1) for considering items similar */
    val maxCenterDistanceRatio: Float = 0.25f,
    /** Maximum size difference ratio (0-1) for considering items similar */
    val maxSizeDifferenceRatio: Float = 0.5f,
    /** If true, category must match exactly for merging */
    val categoryMatchRequired: Boolean = true,
    /** If true, label text must be present and similar for merging */
    val labelMatchRequired: Boolean = false,
    /** Weights for similarity scoring */
    val weights: SimilarityWeights = SimilarityWeights(),
)

/**
 * Weights for combining different similarity factors.
 *
 * These weights determine the relative importance of each factor in the
 * final similarity score. Higher weight = more important.
 */
data class SimilarityWeights(
    val categoryWeight: Float = 0.3f,
// 30% - Category must match
    val labelWeight: Float = 0.25f,
// 25% - Label similarity
    val sizeWeight: Float = 0.20f,
// 20% - Bounding box size
    val distanceWeight: Float = 0.25f,
// 25% - Spatial proximity
)

/**
 * Statistics for monitoring aggregation performance.
 */
data class AggregationStats(
    val totalItems: Int,
    val totalMerges: Int,
    val averageMergesPerItem: Float,
)
