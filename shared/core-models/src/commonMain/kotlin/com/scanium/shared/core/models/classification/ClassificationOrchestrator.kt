package com.scanium.shared.core.models.classification

import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Platform-agnostic classification orchestrator.
 *
 * Dispatches classification requests to the active classifier with concurrency control and retry.
 *
 * ## Features
 * - **Bounded queue**: Max 2 concurrent classification requests
 * - **Retry logic**: Exponential backoff with jitter for retryable errors
 * - **Caching**: Avoid duplicate uploads for the same item
 * - **Status tracking**: PENDING, SUCCESS, FAILED per item
 *
 * ## Retry Policy
 * - Max retries: 3 (total 4 attempts)
 * - Base delay: 2 seconds
 * - Max delay: 16 seconds
 * - Jitter: ±25%
 * - Retryable errors: Network I/O errors, HTTP 408/429/5xx
 * - Non-retryable errors: HTTP 400/401/403/404
 *
 * @param modeFlow StateFlow of current classification mode
 * @param onDeviceClassifier On-device classifier implementation
 * @param cloudClassifier Cloud classifier implementation
 * @param scope Coroutine scope for async operations
 * @param logger Platform-specific logger implementation
 * @param maxConcurrency Maximum concurrent classification requests (default: 2)
 * @param maxRetries Maximum retry attempts (default: 3)
 */
class ClassificationOrchestrator(
    private val modeFlow: StateFlow<ClassificationMode>,
    private val onDeviceClassifier: Classifier,
    private val cloudClassifier: Classifier,
    private val scope: CoroutineScope,
    private val logger: Logger = ConsoleLogger(),
    private val maxConcurrency: Int = 2,
    private val maxRetries: Int = 3,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) }
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
    fun hasResult(itemId: String): Boolean = cache.containsKey(itemId)

    /**
     * Check if item should be classified (not cached, not pending, not permanently failed).
     */
    fun shouldClassify(itemId: String): Boolean {
        return !cache.containsKey(itemId) &&
            !pendingRequests.contains(itemId) &&
            !permanentlyFailedRequests.contains(itemId)
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
     * Classify a single item asynchronously.
     *
     * @param itemId Unique item identifier
     * @param thumbnail Portable image reference
     * @param boundingBox Optional bounding box
     * @param onResult Callback when classification completes (success or failure)
     */
    fun classify(
        itemId: String,
        thumbnail: ImageRef,
        boundingBox: NormalizedRect? = null,
        onResult: (String, ClassificationResult) -> Unit
    ) {
        if (!shouldClassify(itemId)) {
            logger.d(TAG, "Skipping classification for $itemId (already processed)")
            return
        }

        classifyWithRetry(itemId, thumbnail, boundingBox, onResult)
    }

    /**
     * Retry classification for a specific item (e.g., user taps "Retry" button).
     *
     * Clears failed status and retry count, then reclassifies.
     *
     * @param itemId Item ID to retry
     * @param thumbnail Image reference
     * @param boundingBox Optional bounding box
     * @param onResult Callback when classification completes
     */
    fun retry(
        itemId: String,
        thumbnail: ImageRef,
        boundingBox: NormalizedRect? = null,
        onResult: (String, ClassificationResult) -> Unit
    ) {
        logger.i(TAG, "Manual retry requested for $itemId")
        permanentlyFailedRequests.remove(itemId)
        retryAttempts.remove(itemId)
        cache.remove(itemId)
        classifyWithRetry(itemId, thumbnail, boundingBox, onResult)
    }

    /**
     * Classify an item with automatic retry on transient failures.
     */
    private fun classifyWithRetry(
        itemId: String,
        thumbnail: ImageRef,
        boundingBox: NormalizedRect?,
        onResult: (String, ClassificationResult) -> Unit
    ) {
        pendingRequests.add(itemId)

        scope.launch {
            // [METRICS] Start classification turnaround measurement
            val classificationStartTime = System.currentTimeMillis()

            try {
                // Acquire semaphore (wait if 2 requests already in flight)
                concurrencySemaphore.acquire()

                val mode = modeFlow.value
                val classifier = when (mode) {
                    ClassificationMode.CLOUD -> cloudClassifier
                    ClassificationMode.ON_DEVICE -> onDeviceClassifier
                    ClassificationMode.DISABLED -> null
                }

                if (classifier == null) {
                    logger.w(TAG, "Classification disabled for $itemId")
                    permanentlyFailedRequests.add(itemId)
                    return@launch
                }

                logger.d(TAG, "Classifying $itemId with mode=$mode")

                // Execute classification with retry
                val result = classifyWithExponentialBackoff(itemId, thumbnail, classifier)

                // [METRICS] Calculate classification turnaround
                val turnaroundMs = System.currentTimeMillis() - classificationStartTime
                logger.i(TAG, "[METRICS] Classification turnaround for $itemId: ${turnaroundMs}ms (mode=$mode, status=${result.status})")

                when (result.status) {
                    ClassificationStatus.SUCCESS -> {
                        // Success - cache and notify
                        cache[itemId] = result
                        onResult(itemId, result)
                        logger.i(TAG, "Classification succeeded for $itemId")
                    }

                    ClassificationStatus.FAILED -> {
                        if (isRetryableError(result)) {
                            // Retryable error but max retries exceeded
                            logger.w(TAG, "Classification failed after retries for $itemId: ${result.errorMessage}")
                        } else {
                            // Non-retryable error
                            logger.e(TAG, "Classification permanently failed for $itemId: ${result.errorMessage}")
                            permanentlyFailedRequests.add(itemId)
                        }
                        cache[itemId] = result
                        onResult(itemId, result)
                    }

                    ClassificationStatus.SKIPPED -> {
                        logger.w(TAG, "Classification skipped for $itemId")
                        permanentlyFailedRequests.add(itemId)
                    }
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Unexpected error classifying $itemId", t)
                permanentlyFailedRequests.add(itemId)

                // [METRICS] Log error turnaround
                val turnaroundMs = System.currentTimeMillis() - classificationStartTime
                logger.i(TAG, "[METRICS] Classification error turnaround for $itemId: ${turnaroundMs}ms (error)")
            } finally {
                pendingRequests.remove(itemId)
                concurrencySemaphore.release()
            }
        }
    }

    /**
     * Execute classification with exponential backoff retry.
     *
     * @return ClassificationResult (success or final failure)
     */
    private suspend fun classifyWithExponentialBackoff(
        itemId: String,
        thumbnail: ImageRef,
        classifier: Classifier
    ): ClassificationResult {
        var attempt = retryAttempts.getOrDefault(itemId, 0)

        while (attempt <= maxRetries) {
            if (attempt > 0) {
                val delayMs = calculateBackoffDelay(attempt)
                logger.d(TAG, "Retry attempt $attempt for $itemId after ${delayMs}ms")
                delayProvider(delayMs)
            }

            val result = classifier.classify(
                thumbnail = thumbnail,
                hint = null,
                domainPackId = DEFAULT_DOMAIN_PACK_ID
            )

            when {
                result.status == ClassificationStatus.SUCCESS -> {
                    // Success
                    retryAttempts.remove(itemId)
                    return result
                }

                result.status == ClassificationStatus.FAILED && !isRetryableError(result) -> {
                    // Non-retryable error - don't retry
                    retryAttempts.remove(itemId)
                    return result
                }

                result.status == ClassificationStatus.FAILED && attempt < maxRetries -> {
                    // Retryable error and retries remaining - retry
                    attempt++
                    retryAttempts[itemId] = attempt
                    logger.w(TAG, "Retryable error for $itemId, will retry (attempt $attempt/$maxRetries)")
                }

                else -> {
                    // Retryable error but max retries exceeded
                    retryAttempts.remove(itemId)
                    return result
                }
            }
        }

        // Should not reach here, but return last failure
        return ClassificationResult(
            domainCategoryId = null,
            confidence = 0f,
            source = modeFlow.value.toSource(),
            label = null,
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
     * Retryable errors:
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

/**
 * Convert ClassificationMode to ClassificationSource.
 */
private fun ClassificationMode.toSource(): ClassificationSource = when (this) {
    ClassificationMode.CLOUD -> ClassificationSource.CLOUD
    ClassificationMode.ON_DEVICE -> ClassificationSource.ON_DEVICE
    ClassificationMode.DISABLED -> ClassificationSource.FALLBACK
}
