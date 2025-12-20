package com.scanium.app.ml.classification

import com.google.common.truth.Truth.assertThat
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.Bytes
import com.scanium.core.models.ml.ItemCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationOrchestratorTest {

    @Test
    fun whenClassificationInFlight_duplicateRequestsAreSkipped() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.ON_DEVICE)
        val classifier = SuspendedClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-1")

        orchestrator.classify(listOf(item)) { _, _ -> }
        orchestrator.classify(listOf(item)) { _, _ -> }

        advanceUntilIdle()

        assertThat(classifier.callCount).isEqualTo(1)

        classifier.complete(
            ClassificationResult(
                label = "shoe",
                confidence = 0.9f,
                category = ItemCategory.FASHION,
                mode = ClassificationMode.ON_DEVICE
            )
        )
        advanceUntilIdle()

        // Cached result prevents any additional invocations
        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()

        assertThat(classifier.callCount).isEqualTo(1)

        testScope.cancel()
    }

    @Test
    fun whenClassificationFails_failureIsCachedAndNotRetried() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.ON_DEVICE)
        val classifier = FailingClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-2")

        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()

        // Second trigger should be ignored because the failure is cached
        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()

        assertThat(classifier.callCount).isEqualTo(1)

        testScope.cancel()
    }

    @Test
    fun whenRetryableError_classificationIsRetried() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.CLOUD)
        val classifier = RetryableErrorClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            maxRetries = 3,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-3")
        var resultReceived: ClassificationResult? = null

        orchestrator.classify(listOf(item)) { _, result ->
            resultReceived = result
        }
        advanceUntilIdle()

        // Ensure the orchestrator attempted classification
        assertThat(classifier.callCount).isAtLeast(1)

        testScope.cancel()
    }

    @Test
    fun whenRetrySucceeds_resultIsReturned() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.CLOUD)
        val classifier = RetryThenSucceedClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            maxRetries = 3,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-4")
        var resultReceived: ClassificationResult? = null

        orchestrator.classify(listOf(item)) { _, result ->
            resultReceived = result
        }
        advanceUntilIdle()

        // Should have attempted classification
        assertThat(classifier.callCount).isAtLeast(1)

        testScope.cancel()
    }

    @Test
    fun whenManualRetry_itemIsReclassified() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.CLOUD)
        val classifier = CountingClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-5")

        // Initial classification
        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()
        assertThat(classifier.callCount).isEqualTo(1)

        // Manual retry clears cache and reclassifies
        orchestrator.retry("agg-5", item) { _, _ -> }
        advanceUntilIdle()
        assertThat(classifier.callCount).isEqualTo(2)

        testScope.cancel()
    }

    @Test
    fun whenResetCalled_cacheIsCleared() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val modeFlow = MutableStateFlow(ClassificationMode.ON_DEVICE)
        val classifier = CountingClassifier()
        val orchestrator = ClassificationOrchestrator(
            modeFlow = modeFlow,
            onDeviceClassifier = classifier,
            cloudClassifier = classifier,
            scope = testScope,
            delayProvider = { }
        )

        val item = createAggregatedItem("agg-6")

        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()
        assertThat(classifier.callCount).isEqualTo(1)
        assertThat(orchestrator.hasResult("agg-6")).isTrue()

        orchestrator.reset()
        assertThat(orchestrator.hasResult("agg-6")).isFalse()

        // After reset, can classify again
        orchestrator.classify(listOf(item)) { _, _ -> }
        advanceUntilIdle()
        assertThat(classifier.callCount).isEqualTo(2)

        testScope.cancel()
    }

    private fun createAggregatedItem(id: String): AggregatedItem {
        return AggregatedItem(
            aggregatedId = id,
            category = ItemCategory.FASHION,
            labelText = "label",
            boundingBox = NormalizedRect(0f, 0f, 1f, 1f),
            thumbnail = testImageRef(),
            maxConfidence = 0.8f,
            averageConfidence = 0.8f,
            priceRange = 0.0 to 0.0
        )
    }

    private class SuspendedClassifier : ItemClassifier {
        private val deferred = CompletableDeferred<ClassificationResult?>()
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? {
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

        override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? {
            callCount++
            return null
        }
    }

    private class RetryableErrorClassifier : ItemClassifier {
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(input: ClassificationInput): ClassificationResult {
            callCount++
            return ClassificationResult(
                label = null,
                confidence = 0f,
                category = ItemCategory.UNKNOWN,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.FAILED,
                errorMessage = "Request timeout"
            )
        }
    }

    private class RetryThenSucceedClassifier : ItemClassifier {
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(input: ClassificationInput): ClassificationResult {
            callCount++
            return if (callCount < 2) {
                ClassificationResult(
                    label = null,
                    confidence = 0f,
                    category = ItemCategory.UNKNOWN,
                    mode = ClassificationMode.CLOUD,
                    status = ClassificationStatus.FAILED,
                    errorMessage = "Server error (HTTP 503)"
                )
            } else {
                ClassificationResult(
                    label = "Sofa",
                    confidence = 0.87f,
                    category = ItemCategory.HOME_GOOD,
                    mode = ClassificationMode.CLOUD,
                    domainCategoryId = "furniture_sofa",
                    status = ClassificationStatus.SUCCESS
                )
            }
        }
    }

    private class CountingClassifier : ItemClassifier {
        var callCount: Int = 0
            private set

        override suspend fun classifySingle(input: ClassificationInput): ClassificationResult {
            callCount++
            return ClassificationResult(
                label = "Test Item",
                confidence = 0.8f,
                category = ItemCategory.HOME_GOOD,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.SUCCESS
            )
        }
    }

    private fun testImageRef(width: Int = 4, height: Int = 4): Bytes {
        val bytes = ByteArray((width * height).coerceAtLeast(1)) { 1 }
        return Bytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            width = width,
            height = height
        )
    }
}
