package com.scanium.app.ml.classification

import android.util.Log
import com.scanium.app.aggregation.AggregatedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Dispatches classification requests to the active classifier mode and caches results.
 */
class ClassificationOrchestrator(
    private val modeFlow: StateFlow<ClassificationMode>,
    private val onDeviceClassifier: ItemClassifier,
    private val cloudClassifier: ItemClassifier,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ClassificationOrchestrator"
    }

    private val cache = mutableMapOf<String, ClassificationResult>()
    private val pendingRequests = mutableSetOf<String>()
    private val failedRequests = mutableSetOf<String>()

    fun hasResult(aggregatedId: String): Boolean = cache.containsKey(aggregatedId)

    fun shouldClassify(aggregatedId: String): Boolean {
        return !cache.containsKey(aggregatedId) &&
            !pendingRequests.contains(aggregatedId) &&
            !failedRequests.contains(aggregatedId)
    }

    fun reset() {
        cache.clear()
        pendingRequests.clear()
        failedRequests.clear()
    }

    fun classify(items: List<AggregatedItem>, onResult: (AggregatedItem, ClassificationResult) -> Unit) {
        items.filter { it.thumbnail != null && shouldClassify(it.aggregatedId) }
            .forEach { item ->
                val aggregatedId = item.aggregatedId
                pendingRequests.add(aggregatedId)
                scope.launch {
                    try {
                        val mode = modeFlow.value
                        val classifier = if (mode == ClassificationMode.CLOUD) cloudClassifier else onDeviceClassifier
                        Log.d(TAG, "Classifying $aggregatedId with mode=$mode")
                        val result = item.thumbnail?.let { classifier.classifySingle(it) }
                        if (result != null) {
                            cache[aggregatedId] = result
                            onResult(item, result)
                        } else {
                            Log.w(TAG, "Classification returned null for $aggregatedId; caching failure")
                            failedRequests.add(aggregatedId)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Classification failed for $aggregatedId", t)
                        failedRequests.add(aggregatedId)
                    } finally {
                        pendingRequests.remove(aggregatedId)
                    }
                }
            }
    }
}
