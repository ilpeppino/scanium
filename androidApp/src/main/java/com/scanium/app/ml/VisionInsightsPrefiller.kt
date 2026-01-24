package com.scanium.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.scanium.app.enrichment.EnrichmentRepository
import com.scanium.app.enrichment.EnrichmentStatus
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.model.toBitmap
import com.scanium.app.quality.AttributeCompletenessEvaluator
import com.scanium.app.quality.EnrichmentPolicy
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import com.scanium.shared.core.models.items.VisionLabel
import com.scanium.shared.core.models.items.VisionLogo
import com.scanium.shared.core.models.model.ImageRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates immediate vision insights extraction for scanned items.
 *
 * This class implements a THREE-LAYER extraction pipeline:
 *
 * Layer A (Local, Fast, Always Available):
 * - Uses ML Kit Text Recognition for OCR
 * - Uses Android Palette API for color extraction
 * - Runs entirely on-device (~100-200ms)
 * - Results applied IMMEDIATELY for instant feedback
 *
 * Layer B (Cloud, Higher Accuracy):
 * - Calls /v1/vision/insights backend endpoint
 * - Provides logo/brand detection, better categorization
 * - Results merged when available (cloud takes precedence)
 *
 * Layer C (Enrichment, Full Draft Generation):
 * - Calls /v1/items/enrich backend endpoint
 * - Performs Google Vision extraction + attribute normalization + LLM draft
 * - Provides title/description drafts for listings
 * - Results merged when available (~5-15s)
 *
 * This ensures users ALWAYS see something immediately, even if:
 * - Network is unavailable
 * - Cloud service is slow or fails
 * - API key is missing
 *
 * Usage:
 * After capturing items with high-res images, call [extractAndApply] to
 * trigger immediate prefill. Local results appear instantly, cloud results
 * are merged when available, and enrichment drafts arrive later.
 */
@Singleton
class VisionInsightsPrefiller
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val visionInsightsRepository: VisionInsightsRepository,
        private val localVisionExtractor: LocalVisionExtractor,
        private val enrichmentRepository: EnrichmentRepository,
        private val enrichmentPolicy: EnrichmentPolicy,
    ) {
        companion object {
            private const val TAG = "VisionInsightsPrefiller"
            private const val MAX_CONCURRENT_EXTRACTIONS = 3
        }

        // Track in-flight extractions to avoid duplicates
        private val inFlightExtractions = mutableSetOf<String>()
        private val extractionJobs = mutableMapOf<String, Job>()

        /**
         * Extract vision insights from an item's image and apply them.
         *
         * This method:
         * 1. Loads the image from the thumbnail (preferred) or URI fallback
         * 2. Calls the vision insights backend
         * 3. Applies the results to the item via ItemsStateManager
         *
         * IMPORTANT: When multiple items are detected in the same frame, each item
         * should use its own thumbnail (cropped to bounding box) for extraction.
         * Using the full-frame image would apply the same brand/label to all items.
         *
         * The extraction runs asynchronously and does not block.
         * If extraction fails, the item remains unchanged (graceful degradation).
         *
         * @param context Android context for content resolver
         * @param scope Coroutine scope for the extraction
         * @param stateManager ItemsStateManager to apply results
         * @param itemId The item ID to update
         * @param imageUri URI to the high-resolution image (fallback if no thumbnail)
         * @param thumbnail Optional thumbnail cropped to the item's bounding box (preferred)
         */
        fun extractAndApply(
            context: Context,
            scope: CoroutineScope,
            stateManager: ItemsStateManager,
            itemId: String,
            imageUri: Uri?,
            thumbnail: ImageRef? = null,
        ) {
            // Check if already extracting for this item
            synchronized(inFlightExtractions) {
                if (inFlightExtractions.contains(itemId)) {
                    Log.d(TAG, "SCAN_ENRICH: Skipping - already extracting for $itemId")
                    return
                }
                if (inFlightExtractions.size >= MAX_CONCURRENT_EXTRACTIONS) {
                    Log.d(TAG, "SCAN_ENRICH: Skipping - max concurrent extractions reached")
                    return
                }
                inFlightExtractions.add(itemId)
            }

            Log.i(TAG, "SCAN_ENRICH: Starting extraction for item $itemId (hasThumbnail=${thumbnail != null})")

            val job =
                scope.launch(Dispatchers.IO) {
                    try {
                        // IMPORTANT: Prefer thumbnail over full-frame image for per-item extraction
                        // This ensures each item gets its own brand/label instead of sharing results
                        val bitmap =
                            thumbnail?.toBitmap()
                                ?: imageUri?.let { loadBitmapFromUri(context, it) }
                        if (bitmap == null) {
                            Log.e(TAG, "SCAN_ENRICH: Failed to load image for item $itemId (no thumbnail or URI)")
                            return@launch
                        }
                        Log.i(
                            TAG,
                            "SCAN_ENRICH: Using ${if (thumbnail != null) "thumbnail" else "full image"} for extraction (${bitmap.width}x${bitmap.height})",
                        )

                        // LAYER A: LOCAL EXTRACTION (fast, always available)
                        var localApplied = false
                        try {
                            val localResult = localVisionExtractor.extract(bitmap)
                            Log.i(
                                TAG,
                                "SCAN_ENRICH: Local extraction complete - OCR=${localResult.ocrSuccess}, Colors=${localResult.colors.size}",
                            )

                            if (localResult.ocrSuccess || localResult.colorSuccess) {
                                val localVisionAttributes = localVisionExtractor.toVisionAttributes(localResult)
                                withContext(Dispatchers.Main) {
                                    stateManager.applyVisionInsights(
                                        aggregatedId = itemId,
                                        visionAttributes = localVisionAttributes,
                                        suggestedLabel = localResult.suggestedLabel,
                                        categoryHint = null,
                                    )
                                    // Evaluate completeness after Layer A
                                    evaluateAndUpdateCompleteness(stateManager, itemId)
                                }
                                localApplied = true
                                Log.i(TAG, "SCAN_ENRICH: Local results applied (Layer A)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "SCAN_ENRICH: Local extraction failed", e)
                        }

                        // LAYER B: CLOUD EXTRACTION (higher accuracy, logo/brand detection)
                        Log.i(TAG, "SCAN_ENRICH: Starting cloud extraction (Layer B)")
                        val cloudResult = visionInsightsRepository.extractInsights(bitmap, itemId)

                        cloudResult.onSuccess { insights ->
                            Log.i(
                                TAG,
                                "SCAN_ENRICH: Cloud extraction complete - brand=${insights.visionAttributes.primaryBrand}, itemType=${insights.itemType}",
                            )
                            withContext(Dispatchers.Main) {
                                stateManager.applyVisionInsights(
                                    aggregatedId = itemId,
                                    visionAttributes = insights.visionAttributes,
                                    suggestedLabel = insights.suggestedLabel,
                                    categoryHint = insights.categoryHint,
                                )
                                // Evaluate completeness after Layer B
                                evaluateAndUpdateCompleteness(stateManager, itemId)
                            }
                            Log.i(TAG, "SCAN_ENRICH: Cloud results applied (Layer B)")
                        }

                        cloudResult.onFailure { error ->
                            // Check if this is a quota exceeded error
                            if (error is VisionInsightsException && error.isQuotaExceeded) {
                                Log.w(TAG, "SCAN_ENRICH: Quota exceeded - limit=${error.quotaLimit}, resetAt=${error.quotaResetAt}")
                                // Notify UI about quota exceeded (callback will be added via function parameter)
                                withContext(Dispatchers.Main) {
                                    stateManager.notifyQuotaExceeded(
                                        quotaLimit = error.quotaLimit,
                                        resetTime = error.quotaResetAt,
                                    )
                                }
                            } else {
                                if (!localApplied) {
                                    Log.w(TAG, "SCAN_ENRICH: Vision extraction failed for item $itemId: ${error.message}")
                                } else {
                                    Log.i(TAG, "SCAN_ENRICH: Cloud failed but local results available for item $itemId")
                                }
                            }
                        }

                        // LAYER C: ENRICHMENT (full draft generation, async ~5-15s)
                        // This runs in the background and updates the item when complete
                        runEnrichmentLayer(bitmap, itemId, stateManager)
                    } catch (e: Exception) {
                        Log.e(TAG, "SCAN_ENRICH: Vision extraction failed for item $itemId", e)
                    } finally {
                        synchronized(inFlightExtractions) {
                            inFlightExtractions.remove(itemId)
                            extractionJobs.remove(itemId)
                        }
                    }
                }

            synchronized(inFlightExtractions) {
                extractionJobs[itemId] = job
            }
        }

        /**
         * Extract vision insights using the thumbnail (no external Context needed).
         *
         * This overload uses the injected application context and is intended for
         * the detection flow where a thumbnail is available but no Activity context is.
         *
         * @param scope Coroutine scope for the extraction
         * @param stateManager ItemsStateManager to apply results
         * @param itemId The item ID to update
         * @param thumbnail Thumbnail cropped to the item's bounding box
         */
        fun extractAndApplyFromThumbnail(
            scope: CoroutineScope,
            stateManager: ItemsStateManager,
            itemId: String,
            thumbnail: ImageRef,
        ) {
            extractAndApply(
                context = appContext,
                scope = scope,
                stateManager = stateManager,
                itemId = itemId,
                imageUri = null,
                thumbnail = thumbnail,
            )
        }

        /**
         * Extract vision insights for multiple items.
         *
         * @param context Android context
         * @param scope Coroutine scope
         * @param stateManager ItemsStateManager to apply results
         * @param items List of pairs (itemId, imageUri)
         */
        fun extractAndApplyBatch(
            context: Context,
            scope: CoroutineScope,
            stateManager: ItemsStateManager,
            items: List<Pair<String, Uri>>,
        ) {
            items.forEach { (itemId, imageUri) ->
                extractAndApply(context, scope, stateManager, itemId, imageUri)
            }
        }

        /**
         * Cancel any pending extraction for an item.
         */
        fun cancelExtraction(itemId: String) {
            synchronized(inFlightExtractions) {
                extractionJobs[itemId]?.cancel()
                extractionJobs.remove(itemId)
                inFlightExtractions.remove(itemId)
            }
        }

        /**
         * Cancel all pending extractions.
         */
        fun cancelAll() {
            synchronized(inFlightExtractions) {
                extractionJobs.values.forEach { it.cancel() }
                extractionJobs.clear()
                inFlightExtractions.clear()
            }
        }

        /**
         * Check if extraction is in progress for an item.
         */
        fun isExtracting(itemId: String): Boolean {
            synchronized(inFlightExtractions) {
                return inFlightExtractions.contains(itemId)
            }
        }

        private fun loadBitmapFromUri(
            context: Context,
            uri: Uri,
        ): android.graphics.Bitmap? =
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load bitmap from URI: $uri", e)
                null
            }

        /**
         * Evaluate and update completeness for an item after enrichment.
         *
         * This should be called after each layer completes to recalculate
         * the completeness score based on current attributes.
         */
        private fun evaluateAndUpdateCompleteness(
            stateManager: ItemsStateManager,
            itemId: String,
            recordEnrichment: Boolean = false,
        ) {
            val item = stateManager.getItems().find { it.id == itemId } ?: return

            val result =
                AttributeCompletenessEvaluator.evaluate(
                    category = item.category,
                    attributes = item.attributes,
                )

            Log.i(TAG, "SCAN_ENRICH: Completeness for $itemId: ${result.score}%, missing=${result.missingAttributes.map { it.key }}")

            stateManager.updateCompleteness(
                itemId = itemId,
                completenessScore = result.score,
                missingAttributes = result.missingAttributes.map { it.key },
                isReadyForListing = result.isReadyForListing,
                lastEnrichedAt = if (recordEnrichment) System.currentTimeMillis() else null,
            )

            if (recordEnrichment) {
                enrichmentPolicy.recordEnrichment(itemId)
            }
        }

        /**
         * Run Layer C: Full enrichment with draft generation.
         *
         * This is a longer-running operation (~5-15s) that:
         * 1. Checks enrichment policy (budget, completeness)
         * 2. Submits the image to /v1/items/enrich
         * 3. Polls for completion
         * 4. Applies normalized attributes and draft info to the item
         * 5. Evaluates completeness and records enrichment
         */
        private suspend fun runEnrichmentLayer(
            bitmap: Bitmap,
            itemId: String,
            stateManager: ItemsStateManager,
        ) {
            // Check enrichment policy before proceeding
            val item = stateManager.getItems().find { it.id == itemId }
            if (item != null) {
                val decision =
                    enrichmentPolicy.shouldEnrich(
                        itemId = itemId,
                        category = item.category,
                        attributes = item.attributes,
                        enrichmentStatus = item.enrichmentStatus,
                        lastEnrichedAt = item.lastEnrichedAt,
                    )

                if (!decision.shouldEnrich) {
                    Log.i(TAG, "SCAN_ENRICH: Skipping Layer C for $itemId - ${decision.reason}")
                    return
                }
            }

            Log.i(TAG, "SCAN_ENRICH: Starting enrichment (Layer C) for item $itemId")

            val result =
                enrichmentRepository.enrichItem(
                    bitmap = bitmap,
                    itemId = itemId,
                    onProgress = { status ->
                        Log.d(TAG, "SCAN_ENRICH: Enrichment progress for $itemId: stage=${status.stage}")
                    },
                )

            result.onSuccess { status ->
                Log.i(TAG, "SCAN_ENRICH: Enrichment complete for $itemId - stage=${status.stage}, hasDraft=${status.draft != null}")

                // Apply enrichment results to the item
                withContext(Dispatchers.Main) {
                    applyEnrichmentResults(stateManager, itemId, status)
                    // Evaluate completeness and record enrichment after Layer C
                    evaluateAndUpdateCompleteness(stateManager, itemId, recordEnrichment = true)
                }
                Log.i(TAG, "SCAN_ENRICH: Enrichment results applied (Layer C)")
            }

            result.onFailure { error ->
                // Enrichment failure is non-critical - item still has Layer A/B results
                Log.w(TAG, "SCAN_ENRICH: Enrichment failed for $itemId: ${error.message}")
            }
        }

        /**
         * Apply enrichment results to update item attributes and label.
         */
        private fun applyEnrichmentResults(
            stateManager: ItemsStateManager,
            itemId: String,
            status: EnrichmentStatus,
        ) {
            // Build VisionAttributes from enrichment vision facts
            val visionFacts = status.visionFacts
            // Extract itemType from normalized attributes (backend calls it product_type)
            val enrichmentItemType =
                status.normalizedAttributes
                    ?.find { it.key == "product_type" }
                    ?.value
            val visionAttributes =
                if (visionFacts != null) {
                    VisionAttributes(
                        colors =
                            visionFacts.dominantColors.map { color ->
                                VisionColor(
                                    name = color.name,
                                    hex = color.hex,
                                    score = color.pct / 100f,
                                )
                            },
                        ocrText = visionFacts.ocrSnippets.joinToString("\n").takeIf { it.isNotBlank() },
                        logos =
                            visionFacts.logoHints.map { logo ->
                                VisionLogo(
                                    name = logo.name,
                                    score = logo.confidence,
                                )
                            },
                        labels =
                            visionFacts.labelHints.map { label ->
                                VisionLabel(
                                    name = label,
                                    score = 1.0f,
                                )
                            },
                        brandCandidates = visionFacts.logoHints.map { it.name },
                        modelCandidates = emptyList(),
                        itemType = enrichmentItemType, // Populated from normalized attributes
                    )
                } else if (enrichmentItemType != null) {
                    // Even without vision facts, create minimal attributes with itemType
                    VisionAttributes(itemType = enrichmentItemType)
                } else {
                    null
                }

            // Build attributes map from normalized attributes
            // Map backend keys to Android keys (e.g., product_type -> itemType)
            val attributesMap = mutableMapOf<String, ItemAttribute>()
            status.normalizedAttributes?.forEach { attr ->
                val confidence =
                    when (attr.confidence) {
                        "HIGH" -> 0.9f
                        "MED" -> 0.7f
                        "LOW" -> 0.5f
                        else -> 0.5f
                    }
                // Normalize key names: backend uses product_type, Android uses itemType
                val normalizedKey =
                    when (attr.key) {
                        "product_type" -> "itemType"
                        "secondary_color" -> "secondaryColor"
                        else -> attr.key
                    }
                attributesMap[normalizedKey] =
                    ItemAttribute(
                        value = attr.value,
                        confidence = confidence,
                        source = "enrichment-${attr.source.lowercase()}",
                    )
            }

            // Use draft title as suggested label if available
            val suggestedLabel = status.draft?.title?.takeIf { it.isNotBlank() }

            // Apply to state manager
            if (visionAttributes != null || attributesMap.isNotEmpty() || suggestedLabel != null) {
                stateManager.applyVisionInsights(
                    aggregatedId = itemId,
                    visionAttributes = visionAttributes ?: VisionAttributes.EMPTY,
                    suggestedLabel = suggestedLabel,
                    categoryHint = null,
                )

                // Apply normalized attributes with higher confidence (from enrichment)
                if (attributesMap.isNotEmpty()) {
                    stateManager.applyEnhancedClassification(
                        aggregatedId = itemId,
                        category = null,
                        label = suggestedLabel,
                        priceRange = null,
                        classificationConfidence = null,
                        attributes = attributesMap,
                        visionAttributes = visionAttributes,
                        isFromBackend = true,
                    )
                }

                Log.i(TAG, "SCAN_ENRICH: Applied enrichment - label=$suggestedLabel, attrs=${attributesMap.keys}")
            }
        }
    }
