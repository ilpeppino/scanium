package com.scanium.app.items

import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassificationThumbnailProvider
import com.scanium.app.ml.classification.NoopClassifier
import com.scanium.telemetry.facade.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher

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
    telemetry: Telemetry? = null,
    workerDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    mainDispatcher: CoroutineDispatcher = workerDispatcher
): ItemsViewModel {
    return ItemsViewModel(
        classificationMode = classificationMode,
        cloudClassificationEnabled = cloudClassificationEnabled,
        onDeviceClassifier = onDeviceClassifier,
        cloudClassifier = cloudClassifier,
        itemsStore = itemsStore,
        stableItemCropper = stableItemCropper,
        telemetry = telemetry,
        workerDispatcher = workerDispatcher,
        mainDispatcher = mainDispatcher
    )
}

suspend fun ItemsViewModel.awaitItems(dispatcher: TestDispatcher): List<ScannedItem> {
    dispatcher.scheduler.advanceUntilIdle()
    return items.first()
}
