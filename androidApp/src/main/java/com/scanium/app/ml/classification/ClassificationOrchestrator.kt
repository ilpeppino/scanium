package com.scanium.app.ml.classification

import android.os.Trace
import com.scanium.android.platform.adapters.AndroidLogger
import com.scanium.android.platform.adapters.ClassifierAdapter
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.shared.core.models.classification.ClassificationSource
import com.scanium.shared.core.models.classification.Classifier
import com.scanium.telemetry.facade.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.scanium.shared.core.models.classification.ClassificationOrchestrator as SharedOrchestrator

/**
 * Android wrapper for platform-agnostic ClassificationOrchestrator.
 *
 * This class adapts the shared ClassificationOrchestrator to work with Android-specific
 * types (AggregatedItem, ItemClassifier) while delegating all logic to the shared module.
 *
 * ## Architecture
 * - Delegates to shared/core-models ClassificationOrchestrator
 * - Adapts Android ItemClassifier to portable Classifier interface
 * - Converts between Android and shared ClassificationResult types
 * - Maintains Android-facing API for backward compatibility
 *
 * @param modeFlow StateFlow of current classification mode
 * @param onDeviceClassifier Android on-device classifier implementation
 * @param cloudClassifier Android cloud classifier implementation
 * @param scope Coroutine scope for async operations
 * @param maxConcurrency Maximum concurrent classification requests (default: 2)
 * @param maxRetries Maximum retry attempts (default: 3)
 * @param telemetry Telemetry facade for instrumentation
 */
class ClassificationOrchestrator(
    modeFlow: StateFlow<ClassificationMode>,
    onDeviceClassifier: ItemClassifier,
    cloudClassifier: ItemClassifier,
    private val scope: CoroutineScope,
    maxConcurrency: Int = 2,
    maxRetries: Int = 3,
    delayProvider: suspend (Long) -> Unit = { delay(it) },
    telemetry: Telemetry? = null,
) {
    // Adapt Android classifiers to portable Classifier interface
    private val portableOnDeviceClassifier: Classifier =
        ClassifierAdapter(
            androidClassifier = onDeviceClassifier,
            source = ClassificationSource.ON_DEVICE,
        )

    private val portableCloudClassifier: Classifier =
        ClassifierAdapter(
            androidClassifier = cloudClassifier,
            source = ClassificationSource.CLOUD,
        )

    // Map Android ClassificationMode to shared ClassificationMode
    private val portableModeFlow: StateFlow<com.scanium.shared.core.models.classification.ClassificationMode> =
        modeFlow.map { androidMode ->
            when (androidMode) {
                ClassificationMode.CLOUD -> com.scanium.shared.core.models.classification.ClassificationMode.CLOUD
                ClassificationMode.ON_DEVICE -> com.scanium.shared.core.models.classification.ClassificationMode.ON_DEVICE
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue =
                when (modeFlow.value) {
                    ClassificationMode.CLOUD -> com.scanium.shared.core.models.classification.ClassificationMode.CLOUD
                    ClassificationMode.ON_DEVICE -> com.scanium.shared.core.models.classification.ClassificationMode.ON_DEVICE
                },
        )

    // Delegate to shared orchestrator
    private val sharedOrchestrator =
        SharedOrchestrator(
            modeFlow = portableModeFlow,
            onDeviceClassifier = portableOnDeviceClassifier,
            cloudClassifier = portableCloudClassifier,
            scope = scope,
            logger = AndroidLogger(),
            maxConcurrency = maxConcurrency,
            maxRetries = maxRetries,
            delayProvider = delayProvider,
            telemetry = telemetry,
        )

    /**
     * Check if classification result exists for this item.
     */
    fun hasResult(aggregatedId: String): Boolean = sharedOrchestrator.hasResult(aggregatedId)

    /**
     * Check if item should be classified (not cached, not pending, not permanently failed).
     */
    fun shouldClassify(aggregatedId: String): Boolean = sharedOrchestrator.shouldClassify(aggregatedId)

    /**
     * Reset all state (call when starting new scan session).
     */
    fun reset() = sharedOrchestrator.reset()

    /**
     * Classify a list of items asynchronously.
     *
     * @param items Items to classify
     * @param onResult Callback when classification completes (success or failure)
     */
    fun classify(
        items: List<AggregatedItem>,
        onResult: (AggregatedItem, ClassificationResult) -> Unit,
    ) {
        items.filter { it.thumbnail != null && shouldClassify(it.aggregatedId) }
            .forEach { item ->
                val thumbnail = item.thumbnail ?: return@forEach

                // Add trace marker for profiling
                Trace.beginSection("ClassificationOrchestrator.classify")

                // Track metrics
                val startTime = System.currentTimeMillis()
                ClassificationMetrics.recordStart()

                // Classify using shared orchestrator
                sharedOrchestrator.classify(
                    itemId = item.aggregatedId,
                    thumbnail = thumbnail,
                    boundingBox = item.boundingBox,
                ) { itemId, sharedResult ->
                    // Convert shared ClassificationResult to Android ClassificationResult
                    val androidResult = convertToAndroidResult(sharedResult)

                    // Update metrics
                    val latency = System.currentTimeMillis() - startTime
                    val isSuccess = sharedResult.status == com.scanium.shared.core.models.classification.ClassificationStatus.SUCCESS
                    ClassificationMetrics.recordComplete(latency, isSuccess)

                    onResult(item, androidResult)
                    Trace.endSection()
                }
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
        onResult: (AggregatedItem, ClassificationResult) -> Unit,
    ) {
        val thumbnail = item.thumbnail ?: return

        // Add trace marker for profiling
        Trace.beginSection("ClassificationOrchestrator.retry")

        // Track metrics
        val startTime = System.currentTimeMillis()
        ClassificationMetrics.recordStart()

        sharedOrchestrator.retry(
            itemId = aggregatedId,
            thumbnail = thumbnail,
            boundingBox = item.boundingBox,
        ) { itemId, sharedResult ->
            val androidResult = convertToAndroidResult(sharedResult)

            // Update metrics
            val latency = System.currentTimeMillis() - startTime
            val isSuccess = sharedResult.status == com.scanium.shared.core.models.classification.ClassificationStatus.SUCCESS
            ClassificationMetrics.recordComplete(latency, isSuccess)

            onResult(item, androidResult)
            Trace.endSection()
        }
    }

    /**
     * Convert shared ClassificationResult to Android ClassificationResult.
     */
    private fun convertToAndroidResult(
        sharedResult: com.scanium.shared.core.models.classification.ClassificationResult,
    ): ClassificationResult {
        return ClassificationResult(
            label = sharedResult.label,
            confidence = sharedResult.confidence,
            category = convertToAndroidCategory(sharedResult.itemCategory),
            mode = convertToAndroidMode(sharedResult.source),
            domainCategoryId = sharedResult.domainCategoryId,
            attributes = sharedResult.attributes.takeIf { it.isNotEmpty() },
            status = convertToAndroidStatus(sharedResult.status),
            errorMessage = sharedResult.errorMessage,
            requestId = sharedResult.requestId,
        )
    }

    /**
     * Convert shared ItemCategory to Android ItemCategory.
     * Since com.scanium.app.ml.ItemCategory is a typealias for SharedItemCategory, they're the same type.
     */
    private fun convertToAndroidCategory(sharedCategory: com.scanium.shared.core.models.ml.ItemCategory): com.scanium.app.ml.ItemCategory {
        return sharedCategory
    }

    /**
     * Convert shared ClassificationSource to Android ClassificationMode.
     */
    private fun convertToAndroidMode(source: ClassificationSource): ClassificationMode {
        return when (source) {
            ClassificationSource.CLOUD -> ClassificationMode.CLOUD
            ClassificationSource.ON_DEVICE, ClassificationSource.FALLBACK -> ClassificationMode.ON_DEVICE
        }
    }

    /**
     * Convert shared ClassificationStatus to Android ClassificationStatus.
     */
    private fun convertToAndroidStatus(
        sharedStatus: com.scanium.shared.core.models.classification.ClassificationStatus,
    ): ClassificationStatus {
        return when (sharedStatus) {
            com.scanium.shared.core.models.classification.ClassificationStatus.SUCCESS -> ClassificationStatus.SUCCESS
            com.scanium.shared.core.models.classification.ClassificationStatus.FAILED -> ClassificationStatus.FAILED
            com.scanium.shared.core.models.classification.ClassificationStatus.SKIPPED -> ClassificationStatus.FAILED
        }
    }
}
