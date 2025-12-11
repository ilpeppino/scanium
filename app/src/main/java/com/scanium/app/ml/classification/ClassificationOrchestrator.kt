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

    fun hasResult(aggregatedId: String): Boolean = cache.containsKey(aggregatedId)

    fun reset() {
        cache.clear()
    }

    fun classify(items: List<AggregatedItem>, onResult: (AggregatedItem, ClassificationResult) -> Unit) {
        items.filter { it.thumbnail != null && !cache.containsKey(it.aggregatedId) }
            .forEach { item ->
                scope.launch {
                    val mode = modeFlow.value
                    val classifier = if (mode == ClassificationMode.CLOUD) cloudClassifier else onDeviceClassifier
                    Log.d(TAG, "Classifying ${item.aggregatedId} with mode=$mode")
                    val result = item.thumbnail?.let { classifier.classifySingle(it) }
                    if (result != null) {
                        cache[item.aggregatedId] = result
                        onResult(item, result)
                    }
                }
            }
    }
}
