package com.scanium.app.items

import android.graphics.Bitmap
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.CloudClassifier
import com.scanium.app.platform.toImageRefJpeg
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HypothesisSelectionFlowTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun whenMultiHypothesisResultReceived_thenItemIsPersistedImmediately() =
        runTest(dispatcher) {
            val settingsRepository =
                mockk<SettingsRepository>(relaxed = true).also {
                    every { it.openItemListAfterScanFlow } returns flowOf(false)
                    every { it.smartMergeSuggestionsEnabledFlow } returns flowOf(false)
                    every { it.devForceHypothesisSelectionFlow } returns flowOf(false)
                }

            val cloudClassifier = mockk<CloudClassifier>(relaxed = true)
            val result =
                com.scanium.app.classification.hypothesis.MultiHypothesisResult(
                    hypotheses =
                        listOf(
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-1",
                                categoryName = "Vintage Lamp",
                                explanation = "Top match",
                                confidence = 0.92f,
                                confidenceBand = "HIGH",
                            ),
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-2",
                                categoryName = "Desk Lamp",
                                explanation = "Similar shape",
                                confidence = 0.71f,
                                confidenceBand = "MED",
                            ),
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-3",
                                categoryName = "Table Light",
                                explanation = "Similar materials",
                                confidence = 0.58f,
                                confidenceBand = "LOW",
                            ),
                        ),
                    globalConfidence = 0.92f,
                    needsRefinement = false,
                    requestId = "req-1",
                )

            coEvery { cloudClassifier.classifyMultiHypothesis(any()) } returns result

            val itemsViewModel =
                createTestItemsViewModel(
                    classificationMode = MutableStateFlow(ClassificationMode.CLOUD),
                    cloudClassifier = cloudClassifier,
                    settingsRepository = settingsRepository,
                    workerDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            val imageRef = bitmap.toImageRefJpeg()
            val rawDetection =
                RawDetection(
                    boundingBox = NormalizedRect(0.1f, 0.1f, 0.6f, 0.6f),
                    confidence = 0.9f,
                    onDeviceLabel = "Lamp",
                    onDeviceCategory = ItemCategory.HOME_GOOD,
                    trackingId = "track-1",
                    frameSharpness = 0.9f,
                    captureType = CaptureType.SINGLE_SHOT,
                    thumbnailRef = imageRef,
                )

            itemsViewModel.onDetectionReady(rawDetection)
            dispatcher.scheduler.advanceUntilIdle()

            val selectionState = itemsViewModel.hypothesisSelectionState.value
            assertTrue(selectionState is com.scanium.app.classification.hypothesis.HypothesisSelectionState.Showing)

            val items = itemsViewModel.awaitItems(dispatcher)
            assertEquals(1, items.size)
            assertEquals("PENDING", items.first().classificationStatus)
        }

    @Test
    fun whenHypothesisConfirmed_thenItemIsCreatedWithSelectedLabel() =
        runTest(dispatcher) {
            val settingsRepository =
                mockk<SettingsRepository>(relaxed = true).also {
                    every { it.openItemListAfterScanFlow } returns flowOf(false)
                    every { it.smartMergeSuggestionsEnabledFlow } returns flowOf(false)
                    every { it.devForceHypothesisSelectionFlow } returns flowOf(false)
                }

            val cloudClassifier = mockk<CloudClassifier>(relaxed = true)
            val result =
                com.scanium.app.classification.hypothesis.MultiHypothesisResult(
                    hypotheses =
                        listOf(
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-1",
                                categoryName = "Vintage Lamp",
                                explanation = "Top match",
                                confidence = 0.92f,
                                confidenceBand = "HIGH",
                            ),
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-2",
                                categoryName = "Desk Lamp",
                                explanation = "Similar shape",
                                confidence = 0.71f,
                                confidenceBand = "MED",
                            ),
                            com.scanium.app.classification.hypothesis.ClassificationHypothesis(
                                categoryId = "cat-3",
                                categoryName = "Table Light",
                                explanation = "Similar materials",
                                confidence = 0.58f,
                                confidenceBand = "LOW",
                            ),
                        ),
                    globalConfidence = 0.92f,
                    needsRefinement = false,
                    requestId = "req-2",
                )

            coEvery { cloudClassifier.classifyMultiHypothesis(any()) } returns result

            val itemsViewModel =
                createTestItemsViewModel(
                    classificationMode = MutableStateFlow(ClassificationMode.CLOUD),
                    cloudClassifier = cloudClassifier,
                    settingsRepository = settingsRepository,
                    workerDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            val imageRef = bitmap.toImageRefJpeg()
            val rawDetection =
                RawDetection(
                    boundingBox = NormalizedRect(0.1f, 0.1f, 0.6f, 0.6f),
                    confidence = 0.9f,
                    onDeviceLabel = "Lamp",
                    onDeviceCategory = ItemCategory.HOME_GOOD,
                    trackingId = "track-2",
                    frameSharpness = 0.9f,
                    captureType = CaptureType.SINGLE_SHOT,
                    thumbnailRef = imageRef,
                )

            itemsViewModel.onDetectionReady(rawDetection)
            dispatcher.scheduler.advanceUntilIdle()

            val selectionState = itemsViewModel.hypothesisSelectionState.value
            assertTrue(selectionState is com.scanium.app.classification.hypothesis.HypothesisSelectionState.Showing)

            val showing = selectionState as com.scanium.app.classification.hypothesis.HypothesisSelectionState.Showing
            itemsViewModel.confirmPendingDetection(showing.itemId, result.hypotheses.first())
            dispatcher.scheduler.advanceUntilIdle()

            val items = itemsViewModel.awaitItems(dispatcher)
            assertEquals(1, items.size)
            assertEquals("Vintage Lamp", items.first().labelText)
            assertEquals("CONFIRMED", items.first().classificationStatus)
        }
}
