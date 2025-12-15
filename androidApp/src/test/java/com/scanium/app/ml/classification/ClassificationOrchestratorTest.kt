package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationOrchestratorTest {

    @Test
    fun whenClassificationInFlight_duplicateRequestsAreSkipped() = runTest {
        val modeFlow = MutableStateFlow(ClassificationMode.ON_DEVICE)
        val classifier = SuspendedClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = this
        )

        val item = createAggregatedItem("agg-1")

        orchestrator.classify(listOf(item)) { _, _ -> }
        orchestrator.classify(listOf(item)) { _, _ -> }

        runCurrent()

        assertThat(classifier.callCount).isEqualTo(1)

        classifier.complete(
            ClassificationResult(
                label = "shoe",
                confidence = 0.9f,
                category = ItemCategory.FASHION,
                mode = ClassificationMode.ON_DEVICE
            )
        )
        runCurrent()

        // Cached result prevents any additional invocations
        orchestrator.classify(listOf(item)) { _, _ -> }
        runCurrent()

        assertThat(classifier.callCount).isEqualTo(1)
    }

    @Test
    fun whenClassificationFails_failureIsCachedAndNotRetried() = runTest {
        val modeFlow = MutableStateFlow(ClassificationMode.ON_DEVICE)
        val classifier = FailingClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = this
        )

        val item = createAggregatedItem("agg-2")

        orchestrator.classify(listOf(item)) { _, _ -> }
        runCurrent()

        // Second trigger should be ignored because the failure is cached
        orchestrator.classify(listOf(item)) { _, _ -> }
        runCurrent()

        assertThat(classifier.callCount).isEqualTo(1)
    }

    private fun createAggregatedItem(id: String): AggregatedItem {
        return AggregatedItem(
            aggregatedId = id,
            category = ItemCategory.FASHION,
            labelText = "label",
            boundingBox = RectF(0f, 0f, 1f, 1f),
            thumbnail = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
            maxConfidence = 0.8f,
            averageConfidence = 0.8f,
            priceRange = 0.0 to 0.0
        )
    }

    private class SuspendedClassifier : ItemClassifier {
        private val deferred = CompletableDeferred<ClassificationResult?>()
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? {
            callCount++
            return deferred.await()
        }

        fun complete(result: ClassificationResult?) {
            if (!deferred.isCompleted) {
                deferred.complete(result)
            }
        }
    }

    private class FailingClassifier : ItemClassifier {
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? {
            callCount++
            return null
        }
    }
}
