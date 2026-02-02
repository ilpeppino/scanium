package com.scanium.app.camera.detection

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.scanium.app.camera.ScanMode
import com.scanium.shared.core.models.ml.DetectionResult
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DetectionRouterTest {
    private lateinit var router: DetectionRouter
    private val config =
        DetectionRouterConfig(
            enableVerboseLogging = false,
            enableDebugLogging = false,
        )

    @Before
    fun setUp() {
        router = DetectionRouter(config)
    }

    @Test
    fun `startSession resets all counters`() {
        router.tryInvokeObjectDetection(timestampMs = 100L)
        router.tryInvokeObjectDetection(timestampMs = 200L)

        var stats = router.getStats()
        assertThat(stats.totalFrames).isEqualTo(2L)

        router.startSession()

        stats = router.getStats()
        assertThat(stats.totalFrames).isEqualTo(0L)
        assertThat(stats.objectDetections).isEqualTo(0L)
        assertThat(stats.barcodeDetections).isEqualTo(0L)
    }

    @Test
    fun `startSession sets isActive to true`() {
        router.startSession()
        assertThat(router.isActive.value).isTrue()
    }

    @Test
    fun `stopSession sets isActive to false`() {
        router.startSession()
        router.stopSession()
        assertThat(router.isActive.value).isFalse()
    }

    @Test
    fun `routeDetection returns correct detector for OBJECT_DETECTION scan mode`() {
        val (canProceed, detectorType) = router.routeDetection(ScanMode.OBJECT_DETECTION, timestampMs = 0L)

        assertThat(detectorType).isEqualTo(DetectorType.OBJECT)
        assertThat(canProceed).isTrue()
    }

    @Test
    fun `routeDetection returns correct detector for BARCODE scan mode`() {
        val (canProceed, detectorType) = router.routeDetection(ScanMode.BARCODE, timestampMs = 0L)

        assertThat(detectorType).isEqualTo(DetectorType.BARCODE)
        assertThat(canProceed).isTrue()
    }

    @Test
    fun `routeDetection returns correct detector for DOCUMENT_TEXT scan mode`() {
        val (canProceed, detectorType) = router.routeDetection(ScanMode.DOCUMENT_TEXT, timestampMs = 0L)

        assertThat(detectorType).isEqualTo(DetectorType.DOCUMENT)
        assertThat(canProceed).isTrue()
    }

    @Test
    fun `tryInvokeObjectDetection respects throttle interval`() {
        val firstAllowed = router.tryInvokeObjectDetection(timestampMs = 0L)
        assertThat(firstAllowed).isTrue()

        val immediateSecond = router.tryInvokeObjectDetection(timestampMs = 100L)
        assertThat(immediateSecond).isFalse()

        val afterInterval = router.tryInvokeObjectDetection(timestampMs = 400L)
        assertThat(afterInterval).isTrue()
    }

    @Test
    fun `tryInvokeBarcodeDetection respects throttle interval`() {
        val firstAllowed = router.tryInvokeBarcodeDetection(timestampMs = 0L)
        assertThat(firstAllowed).isTrue()

        val immediateSecond = router.tryInvokeBarcodeDetection(timestampMs = 100L)
        assertThat(immediateSecond).isFalse()

        val afterInterval = router.tryInvokeBarcodeDetection(timestampMs = 250L)
        assertThat(afterInterval).isTrue()
    }

    @Test
    fun `tryInvokeBarcodeDetection returns false when disabled`() {
        router.setBarcodeDetectionEnabled(false)

        val result = router.tryInvokeBarcodeDetection(timestampMs = 0L)
        assertThat(result).isFalse()
    }

    @Test
    fun `tryInvokeDocumentDetection respects throttle interval`() {
        val firstAllowed = router.tryInvokeDocumentDetection(timestampMs = 0L)
        assertThat(firstAllowed).isTrue()

        val immediateSecond = router.tryInvokeDocumentDetection(timestampMs = 100L)
        assertThat(immediateSecond).isFalse()

        val afterInterval = router.tryInvokeDocumentDetection(timestampMs = 500L)
        assertThat(afterInterval).isTrue()
    }

    @Test
    fun `tryInvokeDocumentDetection returns false when disabled`() {
        router.setDocumentDetectionEnabled(false)

        val result = router.tryInvokeDocumentDetection(timestampMs = 0L)
        assertThat(result).isFalse()
    }

    @Test
    fun `processBarcodeResults deduplicates by value and format`() {
        router.startSession()

        val item1 = createTestBarcodeItem("1234567890123")
        val item2 = createTestBarcodeItem("1234567890123")

        val (event, uniqueItems) = router.processBarcodeResults(listOf(item1, item2))

        assertThat(uniqueItems).hasSize(1)
        assertThat(event.items).hasSize(1)
        assertThat(event.rawDetectionCount).isEqualTo(2)
        assertThat(event.dedupedCount).isEqualTo(1)
    }

    @Test
    fun `processBarcodeResults skips items with empty barcodeValue`() {
        router.startSession()

        val item1 = createTestBarcodeItem("")
        val item2 = createTestBarcodeItem("1234567890123")

        val (event, uniqueItems) = router.processBarcodeResults(listOf(item1, item2))

        assertThat(uniqueItems).hasSize(1)
        assertThat(event.items).hasSize(1)
    }

    @Test
    fun `processBarcodeResults treats same value with different IDs as duplicate`() {
        router.startSession()

        val item1 = createTestBarcodeItem("1234567890123")
        val item2 = createTestBarcodeItem("1234567890123")

        val (event, uniqueItems) = router.processBarcodeResults(listOf(item1, item2))

        assertThat(uniqueItems).hasSize(1)
    }

    @Test
    fun `processObjectResults creates ObjectDetected event`() {
        val items = listOf(createTestItem())
        val detectionResults = emptyList<DetectionResult>()

        val event = router.processObjectResults(items, detectionResults)

        assertThat(event).isInstanceOf(DetectionEvent.ObjectDetected::class.java)
        assertThat(event.source).isEqualTo(DetectorType.OBJECT)
        assertThat(event.items).isEqualTo(items)
    }

    @Test
    fun `setThrottleInterval updates throttle interval`() {
        router.setThrottleInterval(DetectorType.OBJECT, 1000L)

        val interval = router.getThrottleInterval(DetectorType.OBJECT)
        assertThat(interval).isEqualTo(1000L)
    }

    @Test
    fun `getThrottleInterval returns base interval when adaptive throttling disabled`() {
        router.setAdaptiveThrottlingEnabled(false)

        val interval = router.getThrottleInterval(DetectorType.OBJECT)
        val baseInterval = router.getBaseThrottleInterval(DetectorType.OBJECT)

        assertThat(interval).isEqualTo(baseInterval)
    }

    @Test
    fun `adaptive throttling multiplies base interval`() {
        router.startSession()
        router.setAdaptiveThrottlingEnabled(true)

        val baseInterval = router.getBaseThrottleInterval(DetectorType.OBJECT)
        router.recordFrameProcessingTime(200L)
        router.recordFrameProcessingTime(200L)
        router.recordFrameProcessingTime(200L)
        router.recordFrameProcessingTime(200L)
        router.recordFrameProcessingTime(200L)

        val adjustedInterval = router.getThrottleInterval(DetectorType.OBJECT)

        assertThat(adjustedInterval).isAtLeast(baseInterval)
    }

    @Test
    fun `recordFrameProcessingTime updates adaptive policy`() {
        router.setAdaptiveThrottlingEnabled(true)

        val statsBefore = router.getAdaptiveStats()
        assertThat(statsBefore.totalFramesProcessed).isEqualTo(0L)

        router.recordFrameProcessingTime(100L)

        val statsAfter = router.getAdaptiveStats()
        assertThat(statsAfter.totalFramesProcessed).isEqualTo(1L)
    }

    @Test
    fun `reset clears all router state`() {
        router.startSession()
        router.tryInvokeObjectDetection(timestampMs = 0L)
        router.tryInvokeBarcodeDetection(timestampMs = 0L)

        var stats = router.getStats()
        assertThat(stats.totalFrames).isGreaterThan(0L)

        router.reset()

        stats = router.getStats()
        assertThat(stats.totalFrames).isEqualTo(0L)
        assertThat(router.lastEvent.value).isNull()
    }

    @Test
    fun `createThrottledEvent creates Throttled event`() {
        val event = router.createThrottledEvent(DetectorType.OBJECT, ThrottleReason.INTERVAL_NOT_MET)

        assertThat(event).isInstanceOf(DetectionEvent.Throttled::class.java)
        assertThat(event.source).isEqualTo(DetectorType.OBJECT)
        assertThat(event.reason).isEqualTo(ThrottleReason.INTERVAL_NOT_MET)
    }

    @Test
    fun `processDocumentResults creates DocumentDetected event`() {
        val items = listOf(createTestItem())

        val event = router.processDocumentResults(items)

        assertThat(event).isInstanceOf(DetectionEvent.DocumentDetected::class.java)
        assertThat(event.source).isEqualTo(DetectorType.DOCUMENT)
        assertThat(event.items).isEqualTo(items)
    }

    @Test
    fun `getAdaptiveStats returns statistics`() {
        router.setAdaptiveThrottlingEnabled(true)
        router.recordFrameProcessingTime(100L)

        val stats = router.getAdaptiveStats()

        assertThat(stats.isEnabled).isTrue()
        assertThat(stats.totalFramesProcessed).isEqualTo(1L)
    }

    private fun createTestBarcodeItem(value: String) =
        com.scanium.shared.core.models.items.ScannedItem<Uri>(
            category = ItemCategory.BARCODE,
            labelText = "Barcode",
            boundingBox = com.scanium.shared.core.models.model.NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
            priceRange = 0.0 to 0.0,
            barcodeValue = value,
        )

    private fun createTestItem() =
        com.scanium.shared.core.models.items.ScannedItem<Uri>(
            category = ItemCategory.FASHION,
            labelText = "Test Item",
            boundingBox = com.scanium.shared.core.models.model.NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
            priceRange = 0.0 to 0.0,
        )
}
