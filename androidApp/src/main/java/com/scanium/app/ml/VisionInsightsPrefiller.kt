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
        // DIAG: Entry point logging
        Log.w(TAG, "╔════════════════════════════════════════════════════════════════")
        Log.w(TAG, "║ VISION PREFILL: extractAndApply() CALLED")
        Log.w(TAG, "║ itemId=$itemId")
        Log.w(TAG, "║ imageUri=$imageUri")
        Log.w(TAG, "╚════════════════════════════════════════════════════════════════")

        // Check if already extracting for this item
        synchronized(inFlightExtractions) {
            if (inFlightExtractions.contains(itemId)) {
                Log.w(TAG, "DIAG: Extraction already in flight for item $itemId - SKIPPING")
                return
            }

            // Limit concurrent extractions
            if (inFlightExtractions.size >= MAX_CONCURRENT_EXTRACTIONS) {
                Log.w(TAG, "DIAG: Too many concurrent extractions (${inFlightExtractions.size}), skipping item $itemId")
                return
            }

            inFlightExtractions.add(itemId)
            Log.w(TAG, "DIAG: Added $itemId to inFlightExtractions (total=${inFlightExtractions.size})")
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                Log.w(TAG, "DIAG: Starting TWO-LAYER vision extraction for item $itemId")
                val startTime = System.currentTimeMillis()

                // Load image from URI
                Log.w(TAG, "DIAG: [1/5] Loading bitmap from URI: $imageUri")
                val bitmap = loadBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "DIAG: ❌ FAILED to load bitmap from URI for item $itemId")
                    return@launch
                }
                Log.w(TAG, "DIAG: ✓ Bitmap loaded: ${bitmap.width}x${bitmap.height} for item $itemId")

                // ═══════════════════════════════════════════════════════════════
                // LAYER A: LOCAL EXTRACTION (fast, always available)
                // ═══════════════════════════════════════════════════════════════
                Log.w(TAG, "DIAG: [2/5] Running LAYER A - Local extraction (ML Kit + Palette)...")
                var localApplied = false
                try {
                    val localResult = localVisionExtractor.extract(bitmap)

                    if (localResult.ocrSuccess || localResult.colorSuccess) {
                        val localVisionAttributes = localVisionExtractor.toVisionAttributes(localResult)

                        Log.w(TAG, "DIAG: [3/5] Applying LOCAL results immediately...")
                        withContext(Dispatchers.Main) {
                            stateManager.applyVisionInsights(
                                aggregatedId = itemId,
                                visionAttributes = localVisionAttributes,
                                suggestedLabel = localResult.suggestedLabel,
                                categoryHint = null, // Local doesn't determine category
                            )
                        }
                        localApplied = true

                        Log.w(TAG, "╔════════════════════════════════════════════════════════════════")
                        Log.w(TAG, "║ LAYER A (LOCAL) APPLIED for item $itemId")
                        Log.w(TAG, "║ latency=${localResult.extractionTimeMs}ms")
                        Log.w(TAG, "║ ocrText=${localResult.ocrText?.take(50)}")
                        Log.w(TAG, "║ colors=${localResult.colors.map { it.name }}")
                        Log.w(TAG, "║ suggestedLabel=${localResult.suggestedLabel}")
                        Log.w(TAG, "╚════════════════════════════════════════════════════════════════")
                    } else {
                        Log.w(TAG, "DIAG: Local extraction produced no useful data")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DIAG: Layer A (local) extraction failed", e)
                }

                // ═══════════════════════════════════════════════════════════════
                // LAYER B: CLOUD EXTRACTION (higher accuracy, logo/brand detection)
                // ═══════════════════════════════════════════════════════════════
                Log.w(TAG, "DIAG: [4/5] Running LAYER B - Cloud extraction...")
                val cloudResult = visionInsightsRepository.extractInsights(bitmap, itemId)

                cloudResult.onSuccess { insights ->
                    val latencyMs = System.currentTimeMillis() - startTime
                    Log.w(TAG, "DIAG: ✓ Cloud insights received in ${latencyMs}ms")

                    // Apply cloud results (overrides/enhances local)
                    Log.w(TAG, "DIAG: [5/5] Applying CLOUD results (merging with local)...")
                    withContext(Dispatchers.Main) {
                        stateManager.applyVisionInsights(
                            aggregatedId = itemId,
                            visionAttributes = insights.visionAttributes,
                            suggestedLabel = insights.suggestedLabel,
                            categoryHint = insights.categoryHint,
                        )
                    }

                    Log.w(TAG, "╔════════════════════════════════════════════════════════════════")
                    Log.w(TAG, "║ LAYER B (CLOUD) APPLIED for item $itemId")
                    Log.w(TAG, "║ totalLatency=${latencyMs}ms")
                    Log.w(TAG, "║ suggestedLabel=${insights.suggestedLabel}")
                    Log.w(TAG, "║ categoryHint=${insights.categoryHint}")
                    Log.w(TAG, "║ brand=${insights.visionAttributes.primaryBrand}")
                    Log.w(TAG, "║ ocrText=${insights.visionAttributes.ocrText?.take(50)}")
                    Log.w(TAG, "║ logos=${insights.visionAttributes.logos.map { it.name }}")
                    Log.w(TAG, "╚════════════════════════════════════════════════════════════════")
                }

                cloudResult.onFailure { error ->
                    val latencyMs = System.currentTimeMillis() - startTime
                    Log.w(TAG, "╔════════════════════════════════════════════════════════════════")
                    Log.w(TAG, "║ LAYER B (CLOUD) FAILED for item $itemId")
                    Log.w(TAG, "║ latency=${latencyMs}ms")
                    if (error is VisionInsightsException) {
                        Log.w(TAG, "║ errorCode=${error.errorCode}")
                        Log.w(TAG, "║ userMessage=${error.userMessage}")
                    } else {
                        Log.w(TAG, "║ error=${error.message}")
                    }
                    if (localApplied) {
                        Log.w(TAG, "║ ✓ Local results are still applied (graceful degradation)")
                    } else {
                        Log.w(TAG, "║ ⚠ No vision data available for this item")
                    }
                    Log.w(TAG, "╚════════════════════════════════════════════════════════════════")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DIAG: ❌ EXCEPTION in vision extraction for item $itemId", e)
                Log.e(TAG, "DIAG: Exception message: ${e.message}")
            } finally {
                synchronized(inFlightExtractions) {
                    inFlightExtractions.remove(itemId)
                    extractionJobs.remove(itemId)
                    Log.w(TAG, "DIAG: Removed $itemId from inFlightExtractions (remaining=${inFlightExtractions.size})")
                }
            }
        }

        synchronized(inFlightExtractions) {
            extractionJobs[itemId] = job
        }
        Log.w(TAG, "DIAG: Extraction job launched for item $itemId")
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
