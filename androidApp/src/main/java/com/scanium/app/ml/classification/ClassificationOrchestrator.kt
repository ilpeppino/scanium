package com.scanium.app.ml.classification

import android.util.Log
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.model.ImageRef
import com.scanium.app.platform.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Dispatches classification requests to the active classifier with concurrency control and retry.
 *
 * ***REMOVED******REMOVED*** Features
 * - **Bounded queue**: Max 2 concurrent classification requests
 * - **Retry logic**: Exponential backoff with jitter for retryable errors
 * - **Caching**: Avoid duplicate uploads for the same item
 * - **Status tracking**: PENDING, SUCCESS, FAILED per item
 *
 * ***REMOVED******REMOVED*** Retry Policy
 * - Max retries: 3 (total 4 attempts)
 * - Base delay: 2 seconds
 * - Max delay: 16 seconds
 * - Jitter: ±25%
 * - Retryable errors: Network I/O errors, HTTP 408/429/5xx
 * - Non-retryable errors: HTTP 400/401/403/404
 *
 * ***REMOVED******REMOVED*** Future Enhancement: ON-DEVICE Classifier
 * When implementing on-device CLIP:
 * 1. Check mode: if ON_DEVICE, call onDeviceClassifier instead of cloudClassifier
 * 2. On-device should be synchronous (no retry needed, no network)
 * 3. Cache results same way as cloud
 *
 * Example:
 * ```kotlin
 * val classifier = if (mode == ClassificationMode.CLOUD) {
 *     cloudClassifier
 * } else {
 *     onDeviceClassifier // Future: real CLIP model
 * }
 * ```
 */
class ClassificationOrchestrator(
    private val modeFlow: StateFlow<ClassificationMode>,
    private val onDeviceClassifier: ItemClassifier,
    private val cloudClassifier: ItemClassifier,
    private val scope: CoroutineScope,
    private val maxConcurrency: Int = 2,
    private val maxRetries: Int = 3
) {
    companion object {
        private const val TAG = "ClassificationOrchestrator"
        private const val BASE_DELAY_MS = 2000L
        private const val MAX_DELAY_MS = 16000L
        private const val JITTER_FACTOR = 0.25 // ±25%
    }

    // Concurrency control: max 2 classification requests at a time
    private val concurrencySemaphore = Semaphore(maxConcurrency)

    // Cache successful results
    private val cache = mutableMapOf<String, ClassificationResult>()

    // Track pending requests
    private val pendingRequests = mutableSetOf<String>()

    // Track failed requests (non-retryable errors)
    private val permanentlyFailedRequests = mutableSetOf<String>()

    // Track retry attempts per item
    private val retryAttempts = mutableMapOf<String, Int>()

    /**
     * Check if classification result exists for this item.
     */
    fun hasResult(aggregatedId: String): Boolean = cache.containsKey(aggregatedId)

    /**
     * Check if item should be classified (not cached, not pending, not permanently failed).
     */
    fun shouldClassify(aggregatedId: String): Boolean {
        return !cache.containsKey(aggregatedId) &&
            !pendingRequests.contains(aggregatedId) &&
            !permanentlyFailedRequests.contains(aggregatedId)
    }

    /**
     * Reset all state (call when starting new scan session).
     */
    fun reset() {
        cache.clear()
        pendingRequests.clear()
        permanentlyFailedRequests.clear()
        retryAttempts.clear()
    }

    /**
     * Classify a list of items asynchronously.
     *
     * @param items Items to classify
     * @param onResult Callback when classification completes (success or failure)
     */
    fun classify(
        items: List<AggregatedItem>,
        onResult: (AggregatedItem, ClassificationResult) -> Unit
    ) {
        items.filter { it.thumbnail != null && shouldClassify(it.aggregatedId) }
            .forEach { item ->
                classifyWithRetry(item, onResult)
            }
    }

    /**
     * Retry classification for a specific item (e.g., user taps "Retry" button).
     *
     * Clears failed status and retry count, then reclassifies.
     *
     * @param aggregatedId Item ID to retry
     * @param item The AggregatedItem to classify
     * @param onResult Callback when classification completes
     */
    fun retry(
        aggregatedId: String,
        item: AggregatedItem,
        onResult: (AggregatedItem, ClassificationResult) -> Unit
    ) {
        Log.i(TAG, "Manual retry requested for $aggregatedId")
        permanentlyFailedRequests.remove(aggregatedId)
        retryAttempts.remove(aggregatedId)
        cache.remove(aggregatedId)
        classifyWithRetry(item, onResult)
    }

    /**
     * Classify an item with automatic retry on transient failures.
     */
    private fun classifyWithRetry(
        item: AggregatedItem,
        onResult: (AggregatedItem, ClassificationResult) -> Unit
    ) {
        val aggregatedId = item.aggregatedId
        pendingRequests.add(aggregatedId)

        scope.launch {
            try {
                // Acquire semaphore (wait if 2 requests already in flight)
                concurrencySemaphore.acquire()

                val mode = modeFlow.value
                val classifier = if (mode == ClassificationMode.CLOUD) {
                    cloudClassifier
                } else {
                    // Future: When implementing on-device CLIP, use real classifier here
                    // For now, on-device mode uses placeholder
                    onDeviceClassifier
                }

                Log.d(TAG, "Classifying $aggregatedId with mode=$mode")

                // Execute classification with retry
                val result = classifyWithExponentialBackoff(item, classifier)

                when {
                    result == null -> {
                        // Classifier returned null (not configured or skipped)
                        Log.w(TAG, "Classification returned null for $aggregatedId")
                        permanentlyFailedRequests.add(aggregatedId)
                    }

                    result.status == ClassificationStatus.SUCCESS -> {
                        // Success - cache and notify
                        cache[aggregatedId] = result
                        onResult(item, result)
                        Log.i(TAG, "Classification succeeded for $aggregatedId")
                    }

                    result.status == ClassificationStatus.FAILED && isRetryableError(result) -> {
                        // Retryable error but max retries exceeded
                        Log.w(TAG, "Classification failed after retries for $aggregatedId: ${result.errorMessage}")
                        cache[aggregatedId] = result
                        onResult(item, result)
                    }

                    else -> {
                        // Non-retryable error
                        Log.e(TAG, "Classification permanently failed for $aggregatedId: ${result.errorMessage}")
                        permanentlyFailedRequests.add(aggregatedId)
                        cache[aggregatedId] = result
                        onResult(item, result)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error classifying $aggregatedId", t)
                permanentlyFailedRequests.add(aggregatedId)
            } finally {
                pendingRequests.remove(aggregatedId)
                concurrencySemaphore.release()
            }
        }
    }

    /**
     * Execute classification with exponential backoff retry.
     *
     * @return ClassificationResult (success or final failure), or null if skipped
     */
    private suspend fun classifyWithExponentialBackoff(
        item: AggregatedItem,
        classifier: ItemClassifier
    ): ClassificationResult? {
        val aggregatedId = item.aggregatedId
        var attempt = retryAttempts.getOrDefault(aggregatedId, 0)

        while (attempt <= maxRetries) {
            if (attempt > 0) {
                val delayMs = calculateBackoffDelay(attempt)
                Log.d(TAG, "Retry attempt $attempt for $aggregatedId after ${delayMs}ms")
                delay(delayMs)
            }

            val result = when (val thumbnail = item.thumbnail) {
                is ImageRef.Bytes -> classifier.classifySingle(thumbnail.toBitmap())
                else -> null
            }

            when {
                result == null -> {
                    // Classifier skipped (not configured)
                    return null
                }

                result.status == ClassificationStatus.SUCCESS -> {
                    // Success
                    retryAttempts.remove(aggregatedId)
                    return result
                }

                result.status == ClassificationStatus.FAILED && !isRetryableError(result) -> {
                    // Non-retryable error - don't retry
                    retryAttempts.remove(aggregatedId)
                    return result
                }

                result.status == ClassificationStatus.FAILED && attempt < maxRetries -> {
                    // Retryable error and retries remaining - retry
                    attempt++
                    retryAttempts[aggregatedId] = attempt
                    Log.w(TAG, "Retryable error for $aggregatedId, will retry (attempt $attempt/$maxRetries)")
                }

                else -> {
                    // Retryable error but max retries exceeded
                    retryAttempts.remove(aggregatedId)
                    return result
                }
            }
        }

        // Should not reach here, but return last failure
        return ClassificationResult(
            label = null,
            confidence = 0f,
            category = com.scanium.app.ml.ItemCategory.UNKNOWN,
            mode = modeFlow.value,
            status = ClassificationStatus.FAILED,
            errorMessage = "Max retries exceeded"
        )
    }

    /**
     * Calculate exponential backoff delay with jitter.
     *
     * Formula: min(BASE_DELAY * 2^(attempt-1) * (1 ± JITTER), MAX_DELAY)
     *
     * Example delays (with jitter range):
     * - Attempt 1: 1.5-2.5s
     * - Attempt 2: 3.0-5.0s
     * - Attempt 3: 6.0-10.0s
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = (BASE_DELAY_MS * 2.0.pow(attempt - 1)).toLong()
        val jitter = 1.0 + (Random.nextDouble() * 2 - 1) * JITTER_FACTOR
        val delayWithJitter = (exponentialDelay * jitter).toLong()
        return min(delayWithJitter, MAX_DELAY_MS)
    }

    /**
     * Check if error is retryable.
     *
     * Retryable errors (from CloudClassifier):
     * - "Server error (HTTP 408/429/5xx)"
     * - "Request timeout"
     * - "No network connection"
     * - "Network error: ..."
     *
     * Non-retryable errors:
     * - "Classification failed (HTTP 400/401/403/404)"
     */
    private fun isRetryableError(result: ClassificationResult): Boolean {
        val errorMessage = result.errorMessage ?: return false
        return errorMessage.contains("Server error") ||
               errorMessage.contains("timeout", ignoreCase = true) ||
               errorMessage.contains("network", ignoreCase = true)
    }
}
