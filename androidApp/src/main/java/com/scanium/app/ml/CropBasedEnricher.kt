package com.scanium.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.scanium.app.NormalizedRect
import com.scanium.app.enrichment.EnrichmentRepository
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.LayerState
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import com.scanium.shared.core.models.items.VisionLabel
import com.scanium.shared.core.models.items.VisionLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates Vision enrichment on cropped bounding boxes for multi-object captures.
 *
 * Implements the crop-with-fallback strategy:
 * 1. Crop the bounding box from high-res image
 * 2. Run Vision on the CROP first
 * 3. If weak results: expand crop with padding (+25%) OR run Vision on full image
 *
 * This ensures that logos/text on individual items are detected even when
 * the full image contains multiple objects with mixed visual elements.
 */
@Singleton
class CropBasedEnricher
    @Inject
    constructor(
        private val visionInsightsRepository: VisionInsightsRepository,
        private val localVisionExtractor: LocalVisionExtractor,
        private val enrichmentRepository: EnrichmentRepository,
    ) {
        companion object {
            private const val TAG = "CropBasedEnricher"
            private const val MIN_CROP_SIZE_PX = 100
            private const val WEAK_RESULT_THRESHOLD = 0.3f
            private const val PADDING_RATIO = 0.25f // +25% padding for expanded crop
            private const val MAX_CONCURRENT_ENRICHMENTS = 3
        }

        // Track in-flight enrichments to avoid duplicates
        private val inFlightEnrichments = mutableSetOf<String>()
        private val enrichmentJobs = mutableMapOf<String, Job>()

        /**
         * Enrich a single detected object from a multi-object scan.
         *
         * @param context Android context for content resolver
         * @param scope Coroutine scope for the extraction
         * @param fullImage The full high-resolution captured image
         * @param boundingBox Normalized bounding box of the detected object
         * @param itemId The item ID to apply results to
         * @param stateManager For applying enrichment results
         */
        fun enrichFromCrop(
            context: Context,
            scope: CoroutineScope,
            fullImage: Bitmap,
            boundingBox: NormalizedRect,
            itemId: String,
            stateManager: ItemsStateManager,
        ) {
            // Check if already enriching for this item
            synchronized(inFlightEnrichments) {
                if (inFlightEnrichments.contains(itemId)) {
                    Log.d(TAG, "CROP_ENRICH: Skipping - already enriching for $itemId")
                    return
                }
                if (inFlightEnrichments.size >= MAX_CONCURRENT_ENRICHMENTS) {
                    Log.d(TAG, "CROP_ENRICH: Skipping - max concurrent enrichments reached")
                    return
                }
                inFlightEnrichments.add(itemId)
            }

            Log.i(TAG, "CROP_ENRICH: Starting crop-based enrichment for item $itemId")

            val job =
                scope.launch(Dispatchers.IO) {
                    try {
                        // Update enrichment status to IN_PROGRESS
                        updateEnrichmentStatus(stateManager, itemId) { status ->
                            status.copy(layerA = LayerState.IN_PROGRESS)
                        }
                        // Step 1: Crop the bounding box
                        val crop = cropBoundingBox(fullImage, boundingBox)
                        Log.d(TAG, "CROP_ENRICH: Cropped to ${crop.width}x${crop.height}")

                        // Step 2: Check if crop is too small
                        val bitmapToProcess =
                            if (crop.width < MIN_CROP_SIZE_PX || crop.height < MIN_CROP_SIZE_PX) {
                                Log.w(TAG, "CROP_ENRICH: Crop too small, using expanded crop")
                                cropWithPadding(fullImage, boundingBox, PADDING_RATIO)
                            } else {
                                crop
                            }

                        // Step 3: Run Layer A (local) on crop first
                        var localApplied = false
                        var resultsWeak = false
                        try {
                            val localResult = localVisionExtractor.extract(bitmapToProcess)
                            Log.i(
                                TAG,
                                "CROP_ENRICH: Local extraction complete - OCR=${localResult.ocrSuccess}, Colors=${localResult.colors.size}",
                            )

                            resultsWeak = isResultWeak(localResult)

                            if (localResult.ocrSuccess || localResult.colorSuccess) {
                                val localVisionAttributes = localVisionExtractor.toVisionAttributes(localResult)
                                withContext(Dispatchers.Main) {
                                    stateManager.applyVisionInsights(
                                        aggregatedId = itemId,
                                        visionAttributes = localVisionAttributes,
                                        suggestedLabel = localResult.suggestedLabel,
                                        categoryHint = null,
                                    )
                                    updateEnrichmentStatus(stateManager, itemId) { status ->
                                        status.copy(layerA = LayerState.COMPLETED)
                                    }
                                }
                                localApplied = true
                                Log.i(TAG, "CROP_ENRICH: Local results applied (Layer A)")
                            } else {
                                updateEnrichmentStatus(stateManager, itemId) { status ->
                                    status.copy(layerA = LayerState.COMPLETED)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "CROP_ENRICH: Local extraction failed", e)
                            updateEnrichmentStatus(stateManager, itemId) { status ->
                                status.copy(layerA = LayerState.FAILED)
                            }
                        }

                        // Step 4: If results weak on crop, try expanded crop or full image
                        val finalBitmap =
                            if (resultsWeak && bitmapToProcess == crop) {
                                Log.i(TAG, "CROP_ENRICH: Weak results on crop, trying expanded crop")
                                cropWithPadding(fullImage, boundingBox, PADDING_RATIO)
                            } else {
                                bitmapToProcess
                            }

                        // Step 5: Run Layer B (cloud) on best image
                        updateEnrichmentStatus(stateManager, itemId) { status ->
                            status.copy(layerB = LayerState.IN_PROGRESS)
                        }

                        Log.i(TAG, "CROP_ENRICH: Starting cloud extraction (Layer B)")
                        val cloudResult = visionInsightsRepository.extractInsights(finalBitmap, itemId)

                        cloudResult.onSuccess { insights ->
                            Log.i(
                                TAG,
                                "CROP_ENRICH: Cloud extraction complete - brand=${insights.visionAttributes.primaryBrand}, itemType=${insights.itemType}",
                            )
                            withContext(Dispatchers.Main) {
                                stateManager.applyVisionInsights(
                                    aggregatedId = itemId,
                                    visionAttributes = insights.visionAttributes,
                                    suggestedLabel = insights.suggestedLabel,
                                    categoryHint = insights.categoryHint,
                                )
                                updateEnrichmentStatus(stateManager, itemId) { status ->
                                    status.copy(layerB = LayerState.COMPLETED)
                                }
                            }
                            Log.i(TAG, "CROP_ENRICH: Cloud results applied (Layer B)")
                        }

                        cloudResult.onFailure { error ->
                            if (!localApplied) {
                                Log.w(TAG, "CROP_ENRICH: Cloud extraction failed for item $itemId: ${error.message}")
                            } else {
                                Log.i(TAG, "CROP_ENRICH: Cloud failed but local results available for item $itemId")
                            }
                            updateEnrichmentStatus(stateManager, itemId) { status ->
                                status.copy(layerB = LayerState.FAILED)
                            }
                        }

                        // Step 6: Run Layer C (enrichment) on best image
                        runEnrichmentLayer(finalBitmap, itemId, stateManager)

                        // Clean up crops if different from original
                        if (crop != fullImage) {
                            crop.recycle()
                        }
                        if (bitmapToProcess != crop && bitmapToProcess != fullImage) {
                            bitmapToProcess.recycle()
                        }
                        if (finalBitmap != bitmapToProcess && finalBitmap != fullImage) {
                            finalBitmap.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "CROP_ENRICH: Crop-based enrichment failed for item $itemId", e)
                        updateEnrichmentStatus(stateManager, itemId) { status ->
                            status.copy(
                                layerA = if (status.layerA == LayerState.IN_PROGRESS) LayerState.FAILED else status.layerA,
                                layerB = if (status.layerB == LayerState.IN_PROGRESS) LayerState.FAILED else status.layerB,
                                layerC = if (status.layerC == LayerState.IN_PROGRESS) LayerState.FAILED else status.layerC,
                            )
                        }
                    } finally {
                        synchronized(inFlightEnrichments) {
                            inFlightEnrichments.remove(itemId)
                            enrichmentJobs.remove(itemId)
                        }
                    }
                }

            synchronized(inFlightEnrichments) {
                enrichmentJobs[itemId] = job
            }
        }

        /**
         * Enrich from a URI (for backward compatibility with existing flow).
         */
        fun enrichFromUri(
            context: Context,
            scope: CoroutineScope,
            imageUri: Uri,
            boundingBox: NormalizedRect?,
            itemId: String,
            stateManager: ItemsStateManager,
        ) {
            scope.launch(Dispatchers.IO) {
                val bitmap = loadBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "CROP_ENRICH: Failed to load image from URI for item $itemId")
                    return@launch
                }

                if (boundingBox != null) {
                    // Use crop-based enrichment
                    enrichFromCrop(context, scope, bitmap, boundingBox, itemId, stateManager)
                } else {
                    // No bounding box, use full image (shouldn't happen in multi-object flow)
                    Log.w(TAG, "CROP_ENRICH: No bounding box, using full image for item $itemId")
                    enrichFromCrop(
                        context,
                        scope,
                        bitmap,
                        NormalizedRect(0f, 0f, 1f, 1f), // Full image as bbox
                        itemId,
                        stateManager,
                    )
                }
            }
        }

        /**
         * Cancel any pending enrichment for an item.
         */
        fun cancelEnrichment(itemId: String) {
            synchronized(inFlightEnrichments) {
                enrichmentJobs[itemId]?.cancel()
                enrichmentJobs.remove(itemId)
                inFlightEnrichments.remove(itemId)
            }
        }

        /**
         * Cancel all pending enrichments.
         */
        fun cancelAll() {
            synchronized(inFlightEnrichments) {
                enrichmentJobs.values.forEach { it.cancel() }
                enrichmentJobs.clear()
                inFlightEnrichments.clear()
            }
        }

        /**
         * Check if enrichment is in progress for an item.
         */
        fun isEnriching(itemId: String): Boolean {
            synchronized(inFlightEnrichments) {
                return inFlightEnrichments.contains(itemId)
            }
        }

        private fun cropBoundingBox(
            fullImage: Bitmap,
            bbox: NormalizedRect,
        ): Bitmap {
            val left = (bbox.left * fullImage.width).toInt().coerceIn(0, fullImage.width - 1)
            val top = (bbox.top * fullImage.height).toInt().coerceIn(0, fullImage.height - 1)
            val right = (bbox.right * fullImage.width).toInt().coerceIn(left + 1, fullImage.width)
            val bottom = (bbox.bottom * fullImage.height).toInt().coerceIn(top + 1, fullImage.height)

            val cropWidth = right - left
            val cropHeight = bottom - top
            val bboxArea = (bbox.right - bbox.left) * (bbox.bottom - bbox.top)

            Log.d(
                TAG,
                "CROP_ENRICH: Cropping bbox (${bbox.left}, ${bbox.top}, ${bbox.right}, ${bbox.bottom}) " +
                    "area=${(bboxArea * 100).toInt()}% to ${cropWidth}x$cropHeight from ${fullImage.width}x${fullImage.height}",
            )

            return Bitmap.createBitmap(fullImage, left, top, cropWidth, cropHeight)
        }

        private fun cropWithPadding(
            fullImage: Bitmap,
            bbox: NormalizedRect,
            padding: Float,
        ): Bitmap {
            val bboxWidth = bbox.right - bbox.left
            val bboxHeight = bbox.bottom - bbox.top

            // Adaptive padding: reduce padding for larger objects to avoid capturing too much background
            // Calculate bbox area ratio to determine appropriate padding
            val bboxArea = bboxWidth * bboxHeight
            val adaptivePadding =
                when {
                    bboxArea > 0.50f -> padding * 0.2f // Large objects (>50%): 5% padding (was 25%)
                    bboxArea > 0.35f -> padding * 0.4f // Medium-large: 10% padding
                    bboxArea > 0.20f -> padding * 0.6f // Medium: 15% padding
                    else -> padding // Small objects: full 25% padding
                }

            Log.d(
                TAG,
                "CROP_ENRICH: Adaptive padding for bbox area=${(bboxArea * 100).toInt()}%: " +
                    "using ${(adaptivePadding * 100).toInt()}% padding (base=${(padding * 100).toInt()}%)",
            )

            val padX = bboxWidth * adaptivePadding
            val padY = bboxHeight * adaptivePadding

            val left = ((bbox.left - padX) * fullImage.width).toInt().coerceIn(0, fullImage.width - 1)
            val top = ((bbox.top - padY) * fullImage.height).toInt().coerceIn(0, fullImage.height - 1)
            val right = ((bbox.right + padX) * fullImage.width).toInt().coerceIn(left + 1, fullImage.width)
            val bottom = ((bbox.bottom + padY) * fullImage.height).toInt().coerceIn(top + 1, fullImage.height)

            return Bitmap.createBitmap(fullImage, left, top, right - left, bottom - top)
        }

        private fun isResultWeak(result: LocalVisionResult): Boolean {
            // Weak if: no OCR text AND no colors detected with confidence > threshold
            val hasGoodOcr = result.ocrSuccess && !result.ocrText.isNullOrBlank()
            val hasGoodColors = result.colors.any { it.score > WEAK_RESULT_THRESHOLD }
            return !hasGoodOcr && !hasGoodColors
        }

        private fun loadBitmapFromUri(
            context: Context,
            uri: Uri,
        ): Bitmap? =
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load bitmap from URI: $uri", e)
                null
            }

        private suspend fun updateEnrichmentStatus(
            stateManager: ItemsStateManager,
            itemId: String,
            transform: (EnrichmentLayerStatus) -> EnrichmentLayerStatus,
        ) {
            withContext(Dispatchers.Main) {
                stateManager.updateEnrichmentStatus(itemId, transform)
            }
        }

        /**
         * Run Layer C: Full enrichment with draft generation.
         */
        private suspend fun runEnrichmentLayer(
            bitmap: Bitmap,
            itemId: String,
            stateManager: ItemsStateManager,
        ) {
            Log.i(TAG, "CROP_ENRICH: Starting enrichment (Layer C) for item $itemId")

            updateEnrichmentStatus(stateManager, itemId) { status ->
                status.copy(layerC = LayerState.IN_PROGRESS)
            }

            val result =
                enrichmentRepository.enrichItem(
                    bitmap = bitmap,
                    itemId = itemId,
                    onProgress = { status ->
                        Log.d(TAG, "CROP_ENRICH: Enrichment progress for $itemId: stage=${status.stage}")
                    },
                )

            result.onSuccess { status ->
                Log.i(TAG, "CROP_ENRICH: Enrichment complete for $itemId - stage=${status.stage}, hasDraft=${status.draft != null}")

                // Apply enrichment results to the item
                withContext(Dispatchers.Main) {
                    applyEnrichmentResults(stateManager, itemId, status)
                    updateEnrichmentStatus(stateManager, itemId) { s ->
                        s.copy(layerC = LayerState.COMPLETED)
                    }
                }
                Log.i(TAG, "CROP_ENRICH: Enrichment results applied (Layer C)")
            }

            result.onFailure { error ->
                // Enrichment failure is non-critical - item still has Layer A/B results
                Log.w(TAG, "CROP_ENRICH: Enrichment failed for $itemId: ${error.message}")
                updateEnrichmentStatus(stateManager, itemId) { status ->
                    status.copy(layerC = LayerState.FAILED)
                }
            }
        }

        /**
         * Apply enrichment results to update item attributes and label.
         */
        private fun applyEnrichmentResults(
            stateManager: ItemsStateManager,
            itemId: String,
            status: com.scanium.app.enrichment.EnrichmentStatus,
        ) {
            // Build VisionAttributes from enrichment vision facts
            val visionFacts = status.visionFacts
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
                        itemType = null,
                    )
                } else {
                    null
                }

            // Build attributes map from normalized attributes
            val attributesMap = mutableMapOf<String, ItemAttribute>()
            status.normalizedAttributes?.forEach { attr ->
                val confidence =
                    when (attr.confidence) {
                        "HIGH" -> 0.9f
                        "MED" -> 0.7f
                        "LOW" -> 0.5f
                        else -> 0.5f
                    }
                attributesMap[attr.key] =
                    ItemAttribute(
                        value = attr.value,
                        confidence = confidence,
                        source = "enrichment-${attr.source.lowercase()}",
                    )
            }

            // Use draft title as suggested label if available
            val suggestedLabel = status.draft?.title?.takeIf { it.isNotBlank() }

            // Compute refined category based on enrichment attributes and logos
            val refinedCategory =
                if (attributesMap.isNotEmpty() || visionAttributes?.logos?.isNotEmpty() == true) {
                    val currentItem = stateManager.getItem(itemId)
                    if (currentItem != null) {
                        val brand = attributesMap["brand"]?.value
                        val brandConfidence = attributesMap["brand"]?.confidence ?: 0f
                        val itemType = attributesMap["itemType"]?.value
                        val itemTypeConfidence = attributesMap["itemType"]?.confidence ?: 0f

                        // Check for high-confidence logo detection (overrides other signals)
                        val logoCategory =
                            visionAttributes?.logos?.maxByOrNull { it.score }?.let { logo ->
                                com.scanium.app.ml.detector.CategoryResolver.getCategoryFromBrand(
                                    brand = logo.name,
                                    confidence = logo.score,
                                )
                            }

                        // Use logo override if available, otherwise use multi-signal refinement
                        logoCategory ?: com.scanium.app.ml.detector.CategoryResolver.refineCategoryWithEnrichment(
                            initialCategory = currentItem.category,
                            mlKitLabels = currentItem.mlKitLabels,
                            enrichmentBrand = brand,
                            enrichmentItemType = itemType,
                            brandConfidence = brandConfidence,
                            itemTypeConfidence = itemTypeConfidence,
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

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
                        category = refinedCategory, // Use refined category from enrichment
                        label = suggestedLabel,
                        priceRange = null,
                        classificationConfidence = null,
                        attributes = attributesMap,
                        visionAttributes = visionAttributes,
                        isFromBackend = true,
                    )
                }

                Log.i(
                    TAG,
                    "CROP_ENRICH: Applied enrichment - label=$suggestedLabel, " +
                        "refinedCategory=$refinedCategory, attrs=${attributesMap.keys}",
                )
            }
        }
    }
