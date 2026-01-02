package com.scanium.app.items.classification

import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.items.CloudClassificationAlert
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.ItemCategory
import com.scanium.app.ml.PricingEngine
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationOrchestrator
import com.scanium.app.ml.classification.ClassificationResult
import com.scanium.app.ml.classification.ClassificationStatus
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.CloudCallGate
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassificationThumbnailProvider
import com.scanium.app.ml.classification.NoopClassifier
import com.scanium.app.pricing.PriceEstimationRepository
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceEstimatorProvider
import com.scanium.shared.core.models.pricing.providers.MockPriceEstimatorProvider
import com.scanium.telemetry.facade.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates classification operations for scanned items.
 *
 * This class is responsible for:
 * - Classification orchestration (triggering, filtering, batching)
 * - Retry logic for failed classifications
 * - Cloud call gating (stability check, duplicate detection)
 * - Thumbnail preparation for classification
 * - Price estimation coordination
 * - Cloud classification alerts
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * Classification operations are performed on background dispatchers.
 * The cloud call gate handles its own synchronization.
 *
 * @param scope Coroutine scope for async operations
 * @param stateManager Reference to the items state manager
 * @param classificationMode StateFlow of current classification mode
 * @param cloudClassificationEnabled Flow indicating if cloud classification is enabled
 *        (combines user preferences, remote config, and entitlements via FeatureFlagRepository)
 * @param onDeviceClassifier On-device classifier implementation
 * @param cloudClassifier Cloud classifier implementation
 * @param stableItemCropper Provider for preparing classification thumbnails
 * @param priceEstimatorProvider Provider for price estimation
 * @param telemetry Telemetry facade for instrumentation
 * @param workerDispatcher Background dispatcher for heavy operations
 * @param mainDispatcher Main dispatcher for UI updates
 */
class ItemClassificationCoordinator(
    private val scope: CoroutineScope,
    private val stateManager: ItemsStateManager,
    classificationMode: StateFlow<ClassificationMode> = MutableStateFlow(ClassificationMode.ON_DEVICE),
    cloudClassificationEnabled: StateFlow<Boolean>? = null,
    onDeviceClassifier: ItemClassifier = NoopClassifier,
    cloudClassifier: ItemClassifier = NoopClassifier,
    private val stableItemCropper: ClassificationThumbnailProvider = NoopClassificationThumbnailProvider,
    priceEstimatorProvider: PriceEstimatorProvider? = null,
    telemetry: Telemetry? = null,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    companion object {
        private const val TAG = "ItemClassificationCoord"
    }

    private val classificationModeFlow = classificationMode

    // Effective mode: fall back to ON_DEVICE if cloud classification is not enabled
    // cloudClassificationEnabled combines user prefs, remote config, and entitlements
    private val effectiveClassificationMode =
        if (cloudClassificationEnabled != null) {
            combine(
                classificationModeFlow,
                cloudClassificationEnabled,
            ) { mode, cloudEnabled ->
                if (cloudEnabled) mode else ClassificationMode.ON_DEVICE
            }.stateIn(scope, SharingStarted.Eagerly, ClassificationMode.ON_DEVICE)
        } else {
            classificationModeFlow
        }

    private val classificationOrchestrator =
        ClassificationOrchestrator(
            modeFlow = effectiveClassificationMode,
            onDeviceClassifier = onDeviceClassifier,
            cloudClassifier = cloudClassifier,
            scope = scope,
            telemetry = telemetry,
        )

    private val priceEstimationRepository =
        PriceEstimationRepository(
            provider = priceEstimatorProvider ?: MockPriceEstimatorProvider(),
            scope = scope,
        )

    private val cloudCallGate =
        CloudCallGate(
            isCloudMode = { classificationModeFlow.value == ClassificationMode.CLOUD },
        )

    private val priceStatusJobs = mutableMapOf<String, Job>()
    private val notifiedCloudErrorItems = mutableSetOf<String>()

    // Alert flow for cloud classification errors
    private val _cloudClassificationAlerts =
        MutableSharedFlow<CloudClassificationAlert>(
            extraBufferCapacity = 5,
        )
    val cloudClassificationAlerts = _cloudClassificationAlerts.asSharedFlow()

    /**
     * Trigger enhanced classification for eligible items.
     * Filters items based on stability, cooldown, and duplicate content.
     */
    fun triggerEnhancedClassification() {
        scope.launch(mainDispatcher) {
            // Stage 1: Preliminary filter (check orchestrator + gate cooldown/stability)
            val candidates =
                withContext(workerDispatcher) {
                    stateManager.getAggregatedItems()
                        .filter {
                            (it.thumbnail != null || it.fullImageUri != null) &&
                                classificationOrchestrator.shouldClassify(it.aggregatedId) &&
                                cloudCallGate.canClassify(it, null)
                        }
                }

            if (candidates.isEmpty()) return@launch

            // Stage 2: Prepare thumbnails (I/O)
            val preparedItems = prepareThumbnailsForClassification(candidates)
            if (preparedItems.isEmpty()) return@launch

            // Stage 3: Duplicate content filter (Hash check)
            val itemsToClassify =
                preparedItems.filter { item ->
                    cloudCallGate.canClassify(item, item.thumbnail)
                }

            if (itemsToClassify.isEmpty()) return@launch

            // Mark items as PENDING
            val pendingIds = itemsToClassify.map { it.aggregatedId }
            stateManager.markClassificationPending(pendingIds)

            // Notify gate of successful trigger
            itemsToClassify.forEach {
                cloudCallGate.onClassificationTriggered(it, it.thumbnail)
            }

            // Update UI
            withContext(mainDispatcher) {
                stateManager.refreshItemsFromAggregator()
            }

            classificationOrchestrator.classify(itemsToClassify) { aggregatedItem, result ->
                handleClassificationResult(aggregatedItem, result)
            }
        }
    }

    /**
     * Retry classification for a failed item.
     *
     * @param itemId The aggregated item ID to retry
     */
    fun retryClassification(itemId: String) {
        val aggregatedItems = stateManager.getAggregatedItems()
        val item = aggregatedItems.find { it.aggregatedId == itemId }

        if (item == null) {
            Log.w(TAG, "Cannot retry classification: item $itemId not found")
            return
        }

        Log.i(TAG, "Retrying classification for item $itemId")
        notifiedCloudErrorItems.remove(itemId)

        // Mark as pending
        stateManager.markClassificationPending(listOf(itemId))

        // Update UI
        scope.launch(mainDispatcher) {
            stateManager.refreshItemsFromAggregator()
        }

        // Trigger retry via orchestrator
        scope.launch(workerDispatcher) {
            val preparedItems = prepareThumbnailsForClassification(listOf(item))
            if (preparedItems.isEmpty()) {
                Log.w(TAG, "Retry classification aborted: no thumbnail available")
                return@launch
            }

            val preparedItem = preparedItems.first()
            classificationOrchestrator.retry(itemId, preparedItem) { aggregatedItem, result ->
                handleClassificationResult(aggregatedItem, result)
            }
        }
    }

    /**
     * Synchronize price estimations for current items.
     */
    fun syncPriceEstimations(scannedItems: List<ScannedItem>) {
        val activeIds = scannedItems.map { it.id }.toSet()
        priceStatusJobs.keys.filterNot { activeIds.contains(it) }.forEach { id ->
            priceStatusJobs.remove(id)?.cancel()
            priceEstimationRepository.cancel(id)
        }

        scannedItems.forEach { item ->
            if (item.category != ItemCategory.UNKNOWN) {
                observePriceStatus(item.id)
                priceEstimationRepository.ensureEstimation(item)
            }
        }
    }

    /**
     * Cancel price estimation for an item.
     */
    fun cancelPriceEstimation(itemId: String) {
        priceStatusJobs.remove(itemId)?.cancel()
        priceEstimationRepository.cancel(itemId)
    }

    /**
     * Cancel all price estimations.
     */
    fun cancelAllPriceEstimations() {
        priceStatusJobs.values.forEach { it.cancel() }
        priceStatusJobs.clear()
    }

    /**
     * Reset classification state (call when starting new session).
     */
    fun reset() {
        classificationOrchestrator.reset()
        cloudCallGate.reset()
        notifiedCloudErrorItems.clear()
        val sessionId = CorrelationIds.startNewClassificationSession()
        ScaniumLog.i(TAG, "Classification session reset correlationId=$sessionId")
    }

    /**
     * Start a new classification session.
     */
    fun startNewSession(): String {
        val sessionId = CorrelationIds.startNewClassificationSession()
        ScaniumLog.i(TAG, "Classification session started correlationId=$sessionId")
        return sessionId
    }

    // ==================== Internal Methods ====================

    private suspend fun prepareThumbnailsForClassification(items: List<AggregatedItem>): List<AggregatedItem> {
        if (items.isEmpty()) return emptyList()

        return withContext(workerDispatcher) {
            items.mapNotNull { aggregatedItem ->
                val fallbackThumbnail = resolveCachedThumbnail(aggregatedItem)
                val preparedThumbnail =
                    runCatching {
                        stableItemCropper.prepare(aggregatedItem)
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to prepare stable thumbnail for ${aggregatedItem.aggregatedId}", error)
                    }.getOrNull()

                val resolvedPrepared = resolveCacheKey(preparedThumbnail)
                val thumbnailToUse = resolvedPrepared ?: fallbackThumbnail
                if (thumbnailToUse == null) {
                    null
                } else {
                    if (thumbnailToUse is ImageRef.Bytes) {
                        ThumbnailCache.put(aggregatedItem.aggregatedId, thumbnailToUse)
                    }
                    stateManager.updateThumbnail(aggregatedItem.aggregatedId, thumbnailToUse)
                    aggregatedItem
                }
            }
        }
    }

    private fun handleClassificationResult(
        aggregatedItem: AggregatedItem,
        result: ClassificationResult,
    ) {
        val sessionId = CorrelationIds.currentClassificationSessionId()
        val shouldOverrideCategory =
            result.category != ItemCategory.UNKNOWN &&
                (result.confidence >= aggregatedItem.maxConfidence || aggregatedItem.category == ItemCategory.UNKNOWN)

        val categoryOverride =
            if (shouldOverrideCategory) {
                result.category
            } else {
                aggregatedItem.enhancedCategory ?: aggregatedItem.category
            }

        val labelOverride = result.label?.takeUnless { it.isBlank() } ?: aggregatedItem.labelText
        val priceCategory = if (result.category != ItemCategory.UNKNOWN) result.category else aggregatedItem.category
        val boxArea = aggregatedItem.boundingBox.area
        val priceRange = PricingEngine.generatePriceRange(priceCategory, boxArea)

        stateManager.applyEnhancedClassification(
            aggregatedId = aggregatedItem.aggregatedId,
            category = categoryOverride,
            label = labelOverride,
            priceRange = priceRange,
            classificationConfidence = result.confidence,
        )

        stateManager.updateClassificationStatus(
            aggregatedId = aggregatedItem.aggregatedId,
            status = result.status.name,
            domainCategoryId = result.domainCategoryId,
            errorMessage = result.errorMessage,
            requestId = result.requestId,
        )

        if (result.status == ClassificationStatus.FAILED && result.mode == ClassificationMode.CLOUD) {
            val shouldNotify = result.errorMessage?.contains("using on-device labels", ignoreCase = true) == true
            if (shouldNotify && notifiedCloudErrorItems.add(aggregatedItem.aggregatedId)) {
                scope.launch {
                    _cloudClassificationAlerts.emit(
                        CloudClassificationAlert(
                            itemId = aggregatedItem.aggregatedId,
                            message = "Cloud classification unavailable. Using on-device labels.",
                        ),
                    )
                }
            }
        } else {
            notifiedCloudErrorItems.remove(aggregatedItem.aggregatedId)
        }

        scope.launch(mainDispatcher) {
            stateManager.refreshItemsFromAggregator()

            if (BuildConfig.DEBUG) {
                ScaniumLog.d(
                    TAG,
                    "Classification result item=${aggregatedItem.aggregatedId} session=$sessionId mode=${result.mode} status=${result.status} confidence=${result.confidence} requestId=${result.requestId}",
                )
            }
        }
    }

    private fun observePriceStatus(itemId: String) {
        if (priceStatusJobs.containsKey(itemId)) return

        val statusFlow = priceEstimationRepository.observeStatus(itemId)
        priceStatusJobs[itemId] =
            scope.launch {
                statusFlow.collect { status ->
                    stateManager.updatePriceEstimation(
                        aggregatedId = itemId,
                        status = status,
                        priceRange = (status as? PriceEstimationStatus.Ready)?.priceRange,
                    )
                    stateManager.refreshItemsFromAggregator()
                }
            }
    }

    private fun resolveCachedThumbnail(item: AggregatedItem): ImageRef? {
        return resolveCacheKey(item.thumbnail)
    }

    private fun resolveCacheKey(thumbnail: ImageRef?): ImageRef? {
        return when (thumbnail) {
            is ImageRef.CacheKey -> ThumbnailCache.get(thumbnail.key)
            else -> thumbnail
        }
    }
}
