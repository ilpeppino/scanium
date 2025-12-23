package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.ImageRef
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.pricing.PriceRange
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Session-level aggregation engine that merges similar detections into stable items.
 *
 * This implementation is platform-neutral and relies only on portable models.
 */
class ItemAggregator(
    private val config: AggregationConfig = AggregationConfig(),
    private val logger: Logger = Logger.NONE,
    private val mergePolicy: SpatialTemporalMergePolicy = SpatialTemporalMergePolicy()
) {
    companion object {
        private const val TAG = "ItemAggregator"
        private var idCounter = 0L
    }

    private val aggregatedItems = mutableMapOf<String, AggregatedItem>()
    private var dynamicThreshold: Float? = null

    // PHASE 4: Lightweight candidate metadata cache for spatial-temporal deduplication
    // Maps aggregatedId -> CandidateMetadata for fast merge decisions
    private val candidateMetadataCache = mutableMapOf<String, SpatialTemporalMergePolicy.CandidateMetadata>()

    fun updateSimilarityThreshold(threshold: Float?) {
        val oldThreshold = getCurrentSimilarityThreshold()
        dynamicThreshold = threshold?.coerceIn(0f, 1f)
        val newThreshold = getCurrentSimilarityThreshold()
        logger.w(TAG, "Similarity threshold updated: $oldThreshold → $newThreshold (dynamic=${dynamicThreshold != null})")
    }

    fun getCurrentSimilarityThreshold(): Float = dynamicThreshold ?: config.similarityThreshold

    fun processDetection(detection: ScannedItem): AggregatedItem {
        val currentThreshold = getCurrentSimilarityThreshold()
        logger.i(TAG, ">>> processDetection id=${detection.aggregatedId}, category=${detection.category}, confidence=${detection.confidence}")

        val (bestMatch, bestSimilarity) = findBestMatch(detection)

        if (bestMatch != null && bestSimilarity >= currentThreshold) {
            logger.w(TAG, "✓ MERGE detection ${detection.aggregatedId} → ${bestMatch.aggregatedId} (similarity=$bestSimilarity)")
            bestMatch.merge(detection)
            // Update candidate metadata cache
            updateCandidateMetadata(bestMatch)
            return bestMatch
        }

        // PHASE 4: Fallback to spatial-temporal merge policy when regular similarity fails
        // This handles tracker ID churn and cases where the full similarity calculation is too strict
        val timestampMs = nowMillis()
        val categoryId = detection.category.ordinal
        val spatialMatch = findSpatialTemporalMatch(
            detection.boundingBox,
            timestampMs,
            categoryId
        )

        if (spatialMatch != null) {
            logger.w(TAG, "✓ SPATIAL-TEMPORAL MERGE detection ${detection.aggregatedId} → ${spatialMatch.aggregatedId} (tracker ID churn)")
            spatialMatch.merge(detection)
            // Update candidate metadata cache
            updateCandidateMetadata(spatialMatch)
            return spatialMatch
        }

        val newItem = createAggregatedItem(detection)
        aggregatedItems[newItem.aggregatedId] = newItem
        // Add to candidate metadata cache
        updateCandidateMetadata(newItem)

        if (bestMatch != null) {
            logger.w(TAG, "✗ CREATE NEW (similarity $bestSimilarity < threshold $currentThreshold)")
            logSimilarityBreakdown(detection, bestMatch, bestSimilarity)
        } else {
            logger.i(TAG, "✗ CREATE NEW (no existing items)")
        }

        return newItem
    }

    fun processDetections(detections: List<ScannedItem>): List<AggregatedItem> =
        detections.map { processDetection(it) }

    fun getAggregatedItems(): List<AggregatedItem> = aggregatedItems.values.toList()

    fun getScannedItems(): List<ScannedItem> = aggregatedItems.values.map { it.toScannedItem() }

    fun updatePriceEstimation(
        aggregatedId: String,
        status: PriceEstimationStatus,
        priceRange: PriceRange? = null
    ) {
        aggregatedItems[aggregatedId]?.updatePriceEstimation(status, priceRange)
    }

    fun removeStaleItems(maxAgeMs: Long): Int {
        val now = nowMillis()
        val stale = aggregatedItems.values.filter { item ->
            now - item.lastSeenTimestamp >= maxAgeMs
        }

        stale.forEach { item ->
            logger.d(TAG, "Removing stale item ${item.aggregatedId}")
            aggregatedItems.remove(item.aggregatedId)
            candidateMetadataCache.remove(item.aggregatedId)
        }

        return stale.size
    }

    fun removeItem(aggregatedId: String) {
        aggregatedItems.remove(aggregatedId)
        candidateMetadataCache.remove(aggregatedId)
    }

    fun reset() {
        logger.i(TAG, "Resetting aggregator (clearing ${aggregatedItems.size} items)")
        aggregatedItems.clear()
        candidateMetadataCache.clear()
    }

    /**
     * PHASE 4: Finds a spatial-temporal match using the merge policy.
     * This is a lightweight fallback for when tracker IDs churn.
     */
    private fun findSpatialTemporalMatch(
        bbox: NormalizedRect,
        timestampMs: Long,
        categoryId: Int
    ): AggregatedItem? {
        // Quick path: if no cached metadata, no matches possible
        if (candidateMetadataCache.isEmpty()) return null

        // Build list of candidate metadata for matching
        val candidates = candidateMetadataCache.values.toList()

        // Find best match using the merge policy
        val matchResult = mergePolicy.findBestMatch(bbox, timestampMs, categoryId, candidates)
            ?: return null

        val (bestIndex, score) = matchResult

        // Get the corresponding aggregated item ID from the cache
        val matchedMetadata = candidates[bestIndex]
        return aggregatedItems.values.firstOrNull { item ->
            val cachedMetadata = candidateMetadataCache[item.aggregatedId]
            cachedMetadata == matchedMetadata
        }
    }

    /**
     * PHASE 4: Updates the candidate metadata cache for an aggregated item.
     * Stores lightweight metadata for fast spatial-temporal matching.
     */
    private fun updateCandidateMetadata(item: AggregatedItem) {
        val metadata = mergePolicy.createCandidateMetadata(
            bbox = item.boundingBox,
            timestampMs = item.lastSeenTimestamp,
            categoryId = item.category.ordinal
        )
        candidateMetadataCache[item.aggregatedId] = metadata
    }

    fun getStats(): AggregationStats {
        val totalMerges = aggregatedItems.values.sumOf { it.mergeCount - 1 }
        val avgMergesPerItem = if (aggregatedItems.isNotEmpty()) {
            totalMerges.toFloat() / aggregatedItems.size
        } else {
            0f
        }

        return AggregationStats(
            totalItems = aggregatedItems.size,
            totalMerges = totalMerges,
            averageMergesPerItem = avgMergesPerItem
        )
    }

    fun applyEnhancedClassification(
        aggregatedId: String,
        category: ItemCategory?,
        label: String?,
        priceRange: Pair<Double, Double>?,
        classificationConfidence: Float? = null
    ) {
        aggregatedItems[aggregatedId]?.let { item ->
            category?.let { item.enhancedCategory = it }
            label?.let { item.enhancedLabelText = it }
            priceRange?.let { item.enhancedPriceRange = it }
            classificationConfidence?.let { item.classificationConfidence = it }
        }
    }

    private fun findBestMatch(detection: ScannedItem): Pair<AggregatedItem?, Float> {
        if (aggregatedItems.isEmpty()) return null to 0f

        var bestMatch: AggregatedItem? = null
        var bestSimilarity = 0f

        for (item in aggregatedItems.values) {
            val similarity = calculateSimilarity(detection, item)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = item
            }
        }

        return bestMatch to bestSimilarity
    }

    private fun calculateSimilarity(detection: ScannedItem, item: AggregatedItem): Float {
        // Hard filter: category must match if required
        if (config.categoryMatchRequired && detection.category != item.category) {
            return 0f
        }

        val detectionBox = detection.boundingBox
        val itemBox = item.boundingBox

        var labelScore = 0f
        var sizeScore = 0f
        var distanceScore = 0f

        val detectionLabel = detection.labelText
        val itemLabel = item.labelText

        if (config.labelMatchRequired && (detectionLabel.isEmpty() || itemLabel.isEmpty())) {
            return 0f
        }

        if (detectionLabel.isNotEmpty() && itemLabel.isNotEmpty()) {
            labelScore = calculateLabelSimilarity(detectionLabel, itemLabel)
        }

        val detectionArea = detectionBox.area
        val itemArea = itemBox.area

        if (detectionArea > 0.0001f && itemArea > 0.0001f) {
            val sizeRatio = minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)
            val sizeDiff = abs(1f - sizeRatio)
            if (sizeDiff > config.maxSizeDifferenceRatio) {
                return 0f
            }
            sizeScore = sizeRatio
        }

        val detectionCenter = detectionBox.center()
        val itemCenter = itemBox.center()
        val dx = detectionCenter.first - itemCenter.first
        val dy = detectionCenter.second - itemCenter.second
        val distance = sqrt(dx * dx + dy * dy)
        val normalizedDistance = distance / sqrt(2f)

        if (normalizedDistance > config.maxCenterDistanceRatio) {
            return 0f
        }
        distanceScore = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)

        val weights = config.weights
        val totalWeight = weights.categoryWeight + weights.labelWeight + weights.sizeWeight + weights.distanceWeight
        if (totalWeight == 0f) return 0f

        val categoryScore = if (detection.category == item.category) 1f else 0f
        val weightedScore = (
            categoryScore * weights.categoryWeight +
                labelScore * weights.labelWeight +
                sizeScore * weights.sizeWeight +
                distanceScore * weights.distanceWeight
            ) / totalWeight

        return weightedScore.coerceIn(0f, 1f)
    }

    private fun calculateLabelSimilarity(label1: String, label2: String): Float {
        if (label1.isEmpty() || label2.isEmpty()) return 0f
        if (label1.equals(label2, ignoreCase = true)) return 1f

        val s1 = label1.lowercase().trim()
        val s2 = label2.lowercase().trim()
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)

        return if (maxLen > 0) 1f - (distance.toFloat() / maxLen) else 0f
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[len1][len2]
    }

    private fun createAggregatedItem(detection: ScannedItem): AggregatedItem {
        val aggregatedId = newAggregatedId()
        return AggregatedItem(
            aggregatedId = aggregatedId,
            category = detection.category,
            labelText = detection.labelText,
            boundingBox = detection.boundingBox,
            thumbnail = detection.thumbnail,
            maxConfidence = detection.confidence,
            averageConfidence = detection.confidence,
            priceRange = detection.priceRange,
            mergeCount = detection.mergeCount,
            firstSeenTimestamp = detection.timestampMs,
            lastSeenTimestamp = detection.timestampMs,
            sourceDetectionIds = detection.sourceDetectionIds.toMutableSet().ifEmpty { mutableSetOf(detection.id) }
        )
    }

    private fun logSimilarityBreakdown(
        detection: ScannedItem,
        item: AggregatedItem,
        finalScore: Float
    ) {
        val labelSim = if (detection.labelText.isNotEmpty() && item.labelText.isNotEmpty()) {
            calculateLabelSimilarity(detection.labelText, item.labelText)
        } else {
            0f
        }

        val detectionArea = detection.boundingBox.area
        val itemArea = item.boundingBox.area
        val sizeSim = if (detectionArea > 0.0001f && itemArea > 0.0001f) {
            minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)
        } else {
            0f
        }

        val detectionCenter = detection.boundingBox.center()
        val itemCenter = item.boundingBox.center()
        val dx = detectionCenter.first - itemCenter.first
        val dy = detectionCenter.second - itemCenter.second
        val distance = sqrt(dx * dx + dy * dy)
        val normalizedDistance = distance / sqrt(2f)
        val distanceSim = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)

        logger.d(TAG, "Similarity breakdown for ${item.aggregatedId}:")
        logger.d(TAG, "  Category match: ${detection.category == item.category}")
        logger.d(TAG, "  Label similarity: $labelSim ('${detection.labelText}' vs '${item.labelText}')")
        logger.d(TAG, "  Size similarity: $sizeSim")
        logger.d(TAG, "  Distance similarity: $distanceSim")
        logger.d(TAG, "  Final weighted score: $finalScore")
    }

    private fun newAggregatedId(): String {
        idCounter++
        return "agg_${idCounter}_${Random.nextInt(10_000)}"
    }
}

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
    val firstSeenTimestamp: Long = nowMillis(),
    var lastSeenTimestamp: Long = nowMillis(),
    val sourceDetectionIds: MutableSet<String> = mutableSetOf(),
    var dominantColor: Int? = null,
    var enhancedCategory: ItemCategory? = null,
    var enhancedLabelText: String? = null,
    var enhancedPriceRange: Pair<Double, Double>? = null,
    var classificationConfidence: Float? = null,
    var classificationStatus: String = "NOT_STARTED",
    var domainCategoryId: String? = null,
    var classificationErrorMessage: String? = null,
    var classificationRequestId: String? = null
) {
    fun merge(detection: ScannedItem) {
        mergeCount++
        sourceDetectionIds.add(detection.id)

        averageConfidence = ((averageConfidence * (mergeCount - 1)) + detection.confidence) / mergeCount

        if (detection.confidence > maxConfidence) {
            maxConfidence = detection.confidence
            labelText = detection.labelText
            detection.thumbnail?.let { thumbnail = it }
        }

        boundingBox = detection.boundingBox
        val newMin = minOf(priceRange.first, detection.priceRange.first)
        val newMax = maxOf(priceRange.second, detection.priceRange.second)
        priceRange = newMin to newMax
        if (detection.estimatedPriceRange != null) {
            estimatedPriceRange = detection.estimatedPriceRange
            priceEstimationStatus = detection.priceEstimationStatus
        }
        lastSeenTimestamp = detection.timestampMs
    }

    fun getCenterPoint(): Pair<Float, Float> = boundingBox.center()

    fun getBoxArea(): Float = boundingBox.area

    fun isStale(maxAgeMs: Long): Boolean = nowMillis() - lastSeenTimestamp >= maxAgeMs

    fun toScannedItem(): ScannedItem {
        return ScannedItem(
            aggregatedId = aggregatedId,
            category = enhancedCategory ?: category,
            labelText = enhancedLabelText ?: labelText,
            boundingBox = boundingBox,
            priceRange = enhancedPriceRange ?: priceRange,
            estimatedPriceRange = estimatedPriceRange,
            priceEstimationStatus = priceEstimationStatus,
            confidence = maxConfidence,
            thumbnail = thumbnail,
            timestampMs = lastSeenTimestamp,
            mergeCount = mergeCount,
            averageConfidence = averageConfidence,
            sourceDetectionIds = sourceDetectionIds.toMutableSet(),
            enhancedCategory = enhancedCategory,
            enhancedLabelText = enhancedLabelText,
            enhancedPriceRange = enhancedPriceRange,
            classificationStatus = classificationStatus,
            domainCategoryId = domainCategoryId,
            classificationErrorMessage = classificationErrorMessage,
            classificationRequestId = classificationRequestId
        )
    }

    fun updatePriceEstimation(status: PriceEstimationStatus, priceRange: PriceRange?) {
        priceEstimationStatus = status
        priceRange?.let { range ->
            estimatedPriceRange = range
            this.priceRange = range.toPair()
        }
    }
}

data class AggregationConfig(
    val similarityThreshold: Float = 0.6f,
    val maxCenterDistanceRatio: Float = 0.25f,
    val maxSizeDifferenceRatio: Float = 0.5f,
    val categoryMatchRequired: Boolean = true,
    val labelMatchRequired: Boolean = false,
    val weights: SimilarityWeights = SimilarityWeights()
)

data class SimilarityWeights(
    val categoryWeight: Float = 0.3f,
    val labelWeight: Float = 0.25f,
    val sizeWeight: Float = 0.2f,
    val distanceWeight: Float = 0.25f
)

data class AggregationStats(
    val totalItems: Int,
    val totalMerges: Int,
    val averageMergesPerItem: Float
)

object AggregationPresets {
    val BALANCED = AggregationConfig(
        similarityThreshold = 0.6f,
        maxCenterDistanceRatio = 0.25f,
        maxSizeDifferenceRatio = 0.5f,
        categoryMatchRequired = true,
        labelMatchRequired = false,
        weights = SimilarityWeights(
            categoryWeight = 0.3f,
            labelWeight = 0.25f,
            sizeWeight = 0.20f,
            distanceWeight = 0.25f
        )
    )

    val STRICT = AggregationConfig(
        similarityThreshold = 0.75f,
        maxCenterDistanceRatio = 0.15f,
        maxSizeDifferenceRatio = 0.3f,
        categoryMatchRequired = true,
        labelMatchRequired = true,
        weights = SimilarityWeights(
            categoryWeight = 0.35f,
            labelWeight = 0.35f,
            sizeWeight = 0.15f,
            distanceWeight = 0.15f
        )
    )

    val LOOSE = AggregationConfig(
        similarityThreshold = 0.5f,
        maxCenterDistanceRatio = 0.35f,
        maxSizeDifferenceRatio = 0.7f,
        categoryMatchRequired = true,
        labelMatchRequired = false,
        weights = SimilarityWeights(
            categoryWeight = 0.35f,
            labelWeight = 0.2f,
            sizeWeight = 0.15f,
            distanceWeight = 0.3f
        )
    )

    val REALTIME = AggregationConfig(
        similarityThreshold = 0.55f,
        maxCenterDistanceRatio = 0.30f,
        maxSizeDifferenceRatio = 0.6f,
        categoryMatchRequired = true,
        labelMatchRequired = false,
        weights = SimilarityWeights(
            categoryWeight = 0.4f,
            labelWeight = 0.15f,
            sizeWeight = 0.20f,
            distanceWeight = 0.25f
        )
    )

    val LABEL_FOCUSED = AggregationConfig(
        similarityThreshold = 0.65f,
        maxCenterDistanceRatio = 0.4f,
        maxSizeDifferenceRatio = 0.6f,
        categoryMatchRequired = true,
        labelMatchRequired = true,
        weights = SimilarityWeights(
            categoryWeight = 0.25f,
            labelWeight = 0.45f,
            sizeWeight = 0.15f,
            distanceWeight = 0.15f
        )
    )

    val SPATIAL_FOCUSED = AggregationConfig(
        similarityThreshold = 0.6f,
        maxCenterDistanceRatio = 0.20f,
        maxSizeDifferenceRatio = 0.4f,
        categoryMatchRequired = true,
        labelMatchRequired = false,
        weights = SimilarityWeights(
            categoryWeight = 0.3f,
            labelWeight = 0.1f,
            sizeWeight = 0.3f,
            distanceWeight = 0.3f
        )
    )

    fun getPreset(name: String): AggregationConfig {
        return when (name.uppercase()) {
            "BALANCED" -> BALANCED
            "STRICT" -> STRICT
            "LOOSE" -> LOOSE
            "REALTIME" -> REALTIME
            "LABEL_FOCUSED" -> LABEL_FOCUSED
            "SPATIAL_FOCUSED" -> SPATIAL_FOCUSED
            else -> BALANCED
        }
    }

    fun getPresetNames(): List<String> = listOf(
        "BALANCED",
        "STRICT",
        "LOOSE",
        "REALTIME",
        "LABEL_FOCUSED",
        "SPATIAL_FOCUSED"
    )
}

private fun NormalizedRect.center(): Pair<Float, Float> {
    return Pair(
        (left + right) / 2f,
        (top + bottom) / 2f
    )
}

private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
