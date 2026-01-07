package com.scanium.app.items

import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper for creating [ItemsViewModel] instances inside instrumentation tests where
 * Hilt injection isn't available.
 */
private object NoopVisionInsightsPrefiller {
    fun create(): VisionInsightsPrefiller {
        val mockRepository = mockk<VisionInsightsRepository>(relaxed = true)
        val mockLocalExtractor = mockk<LocalVisionExtractor>(relaxed = true)
        return VisionInsightsPrefiller(mockRepository, mockLocalExtractor)
    }
}

fun createAndroidTestItemsViewModel(
    classificationMode: StateFlow<ClassificationMode> = MutableStateFlow(ClassificationMode.ON_DEVICE),
    cloudClassificationEnabled: StateFlow<Boolean> = MutableStateFlow(false),
    onDeviceClassifier: ItemClassifier = NoopClassifier,
    cloudClassifier: ItemClassifier = NoopClassifier,
    itemsStore: ScannedItemStore = NoopScannedItemStore,
    stableItemCropper: ClassificationThumbnailProvider = NoopClassificationThumbnailProvider,
    visionInsightsPrefiller: VisionInsightsPrefiller = NoopVisionInsightsPrefiller.create(),
    telemetry: Telemetry? = null,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
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
        telemetry = telemetry,
        workerDispatcher = workerDispatcher,
        mainDispatcher = mainDispatcher,
    )
}
