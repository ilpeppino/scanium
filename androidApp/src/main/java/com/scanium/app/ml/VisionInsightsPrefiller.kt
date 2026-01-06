package com.scanium.app.ml

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.logging.ScaniumLog
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
 * This class is responsible for:
 * - Extracting vision insights from captured images immediately after scan
 * - Applying the results (OCR, brand, colors) to items for instant prefill
 * - Managing extraction jobs to avoid duplicates
 *
 * Usage:
 * After capturing items with high-res images, call [extractAndApply] to
 * trigger immediate prefill. This happens in parallel with (and before)
 * full classification, giving users instant feedback.
 */
@Singleton
class VisionInsightsPrefiller @Inject constructor(
    private val visionInsightsRepository: VisionInsightsRepository,
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
                Log.d(TAG, "Extraction already in flight for item $itemId")
                return
            }

            // Limit concurrent extractions
            if (inFlightExtractions.size >= MAX_CONCURRENT_EXTRACTIONS) {
                Log.w(TAG, "Too many concurrent extractions, skipping item $itemId")
                return
            }

            inFlightExtractions.add(itemId)
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                ScaniumLog.d(TAG, "Starting vision insights extraction for item $itemId")
                val startTime = System.currentTimeMillis()

                // Load image from URI
                val bitmap = loadBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    ScaniumLog.w(TAG, "Failed to load image for item $itemId")
                    return@launch
                }

                // Extract vision insights
                val result = visionInsightsRepository.extractInsights(bitmap, itemId)

                result.onSuccess { insights ->
                    val latencyMs = System.currentTimeMillis() - startTime

                    // Apply insights to the item
                    withContext(Dispatchers.Main) {
                        stateManager.applyVisionInsights(
                            aggregatedId = itemId,
                            visionAttributes = insights.visionAttributes,
                            suggestedLabel = insights.suggestedLabel,
                            categoryHint = insights.categoryHint,
                        )
                    }

                    ScaniumLog.i(
                        TAG,
                        "Vision insights applied to item $itemId in ${latencyMs}ms: " +
                            "label=${insights.suggestedLabel} category=${insights.categoryHint} " +
                            "ocrSnippets=${insights.visionAttributes.ocrText?.take(50)} " +
                            "brand=${insights.visionAttributes.primaryBrand}"
                    )
                }

                result.onFailure { error ->
                    val latencyMs = System.currentTimeMillis() - startTime
                    if (error is VisionInsightsException) {
                        ScaniumLog.w(
                            TAG,
                            "Vision insights extraction failed for item $itemId in ${latencyMs}ms: " +
                                "${error.errorCode} - ${error.userMessage} (retryable=${error.retryable})"
                        )
                    } else {
                        ScaniumLog.e(TAG, "Vision insights extraction error for item $itemId", error)
                    }
                    // Graceful degradation - item remains unchanged
                }
            } catch (e: Exception) {
                ScaniumLog.e(TAG, "Unexpected error in vision insights extraction for item $itemId", e)
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
