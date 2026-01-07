package com.scanium.app.items

import android.content.Context
import android.net.Uri
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.enrichment.EnrichmentRepository
import com.scanium.app.ml.CropBasedEnricher
import com.scanium.app.ml.LocalVisionExtractor
import com.scanium.app.ml.VisionInsightsPrefiller
import com.scanium.app.ml.VisionInsightsRepository
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassificationThumbnailProvider
import com.scanium.app.ml.classification.NoopClassifier
import com.scanium.telemetry.facade.Telemetry
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A no-op VisionInsightsPrefiller for testing.
 * Does nothing when called - no network requests, no state updates.
 */
object NoopVisionInsightsPrefiller {
    fun create(): VisionInsightsPrefiller {
        val mockVisionRepository = mockk<VisionInsightsRepository>(relaxed = true)
        val mockLocalExtractor = mockk<LocalVisionExtractor>(relaxed = true)
        val mockEnrichmentRepository = mockk<EnrichmentRepository>(relaxed = true)
        return VisionInsightsPrefiller(mockVisionRepository, mockLocalExtractor, mockEnrichmentRepository)
    }
}

/**
 * A no-op CropBasedEnricher for testing.
 * Does nothing when called - no enrichment operations.
 */
object NoopCropBasedEnricher {
    fun create(): CropBasedEnricher {
        val mockVisionRepository = mockk<VisionInsightsRepository>(relaxed = true)
        val mockLocalExtractor = mockk<LocalVisionExtractor>(relaxed = true)
        val mockEnrichmentRepository = mockk<EnrichmentRepository>(relaxed = true)
        return CropBasedEnricher(mockVisionRepository, mockLocalExtractor, mockEnrichmentRepository)
    }
}

/**
 * Helper for constructing [ItemsViewModel] instances in unit tests.
 *
 * The production ViewModel relies on several injected dependencies, so tests
 * use this factory to supply default no-op implementations unless specific
 * collaborators need to be overridden.
 */
fun createTestItemsViewModel(
    classificationMode: StateFlow<ClassificationMode> = MutableStateFlow(ClassificationMode.ON_DEVICE),
    cloudClassificationEnabled: StateFlow<Boolean> = MutableStateFlow(false),
    onDeviceClassifier: ItemClassifier = NoopClassifier,
    cloudClassifier: ItemClassifier = NoopClassifier,
    itemsStore: ScannedItemStore = NoopScannedItemStore,
    stableItemCropper: ClassificationThumbnailProvider = NoopClassificationThumbnailProvider,
    visionInsightsPrefiller: VisionInsightsPrefiller = NoopVisionInsightsPrefiller.create(),
    cropBasedEnricher: CropBasedEnricher = NoopCropBasedEnricher.create(),
    telemetry: Telemetry? = null,
    workerDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    mainDispatcher: CoroutineDispatcher = workerDispatcher,
): ItemsViewModel {
    return ItemsViewModel(
        classificationMode = classificationMode,
        cloudClassificationEnabled = cloudClassificationEnabled,
        onDeviceClassifier = onDeviceClassifier,
        cloudClassifier = cloudClassifier,
        itemsStore = itemsStore,
        stableItemCropper = stableItemCropper,
        visionInsightsPrefiller = visionInsightsPrefiller,
        cropBasedEnricher = cropBasedEnricher,
        telemetry = telemetry,
        workerDispatcher = workerDispatcher,
        mainDispatcher = mainDispatcher,
    )
}

suspend fun ItemsViewModel.awaitItems(dispatcher: TestDispatcher): List<ScannedItem> {
    dispatcher.scheduler.advanceUntilIdle()
    return items.first()
}
