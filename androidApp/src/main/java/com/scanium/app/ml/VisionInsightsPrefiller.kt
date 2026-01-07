package com.scanium.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.logging.ScaniumLog
import com.scanium.shared.core.models.items.VisionAttributes
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
 * This class implements a TWO-LAYER extraction pipeline:
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
 * This ensures users ALWAYS see something immediately, even if:
 * - Network is unavailable
 * - Cloud service is slow or fails
 * - API key is missing
 *
 * Usage:
 * After capturing items with high-res images, call [extractAndApply] to
 * trigger immediate prefill. Local results appear instantly, cloud results
 * are merged when available.
 */
@Singleton
class VisionInsightsPrefiller @Inject constructor(
    private val visionInsightsRepository: VisionInsightsRepository,
    private val localVisionExtractor: LocalVisionExtractor,
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
     * 1. Loads the image from the provided URI
     * 2. Calls the vision insights backend
     * 3. Applies the results to the item via ItemsStateManager
     *
     * The extraction runs asynchronously and does not block.
     * If extraction fails, the item remains unchanged (graceful degradation).
     *
     * @param context Android context for content resolver
     * @param scope Coroutine scope for the extraction
     * @param stateManager ItemsStateManager to apply results
     * @param itemId The item ID to update
     * @param imageUri URI to the high-resolution image
     */
    fun extractAndApply(
        context: Context,
        scope: CoroutineScope,
        stateManager: ItemsStateManager,
        itemId: String,
        imageUri: Uri,
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

        Log.i(TAG, "SCAN_ENRICH: Starting extraction for item $itemId")

        val job = scope.launch(Dispatchers.IO) {
            try {
                // Load image from URI
                val bitmap = loadBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "SCAN_ENRICH: Failed to load image from URI for item $itemId")
                    return@launch
                }

                // LAYER A: LOCAL EXTRACTION (fast, always available)
                var localApplied = false
                try {
                    val localResult = localVisionExtractor.extract(bitmap)
                    Log.i(TAG, "SCAN_ENRICH: Local extraction complete - OCR=${localResult.ocrSuccess}, Colors=${localResult.colors.size}")

                    if (localResult.ocrSuccess || localResult.colorSuccess) {
                        val localVisionAttributes = localVisionExtractor.toVisionAttributes(localResult)
                        withContext(Dispatchers.Main) {
                            stateManager.applyVisionInsights(
                                aggregatedId = itemId,
                                visionAttributes = localVisionAttributes,
                                suggestedLabel = localResult.suggestedLabel,
                                categoryHint = null,
                            )
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
                    Log.i(TAG, "SCAN_ENRICH: Cloud extraction complete - brand=${insights.visionAttributes.primaryBrand}, itemType=${insights.itemType}")
                    withContext(Dispatchers.Main) {
                        stateManager.applyVisionInsights(
                            aggregatedId = itemId,
                            visionAttributes = insights.visionAttributes,
                            suggestedLabel = insights.suggestedLabel,
                            categoryHint = insights.categoryHint,
                        )
                    }
                    Log.i(TAG, "SCAN_ENRICH: Cloud results applied (Layer B)")
                }

                cloudResult.onFailure { error ->
                    if (!localApplied) {
                        Log.w(TAG, "SCAN_ENRICH: Vision extraction failed for item $itemId: ${error.message}")
                    } else {
                        Log.i(TAG, "SCAN_ENRICH: Cloud failed but local results available for item $itemId")
                    }
                }
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

    private fun loadBitmapFromUri(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bitmap from URI: $uri", e)
            null
        }
    }
}
