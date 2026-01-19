package com.scanium.app.camera

import com.google.common.truth.Truth.assertThat
import com.scanium.app.AggregatedItem
import com.scanium.app.ItemCategory
import com.scanium.app.NormalizedRect
import com.scanium.app.ml.DetectionResult
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayTrackMapperTest {
    @Test
    fun whenAggregatedMatchReadyUsesClassification() {
        val aggregated =
            AggregatedItem(
                aggregatedId = "agg1",
                category = ItemCategory.UNKNOWN,
                labelText = "",
                boundingBox = NormalizedRect(0f, 0f, 0.3f, 0.3f),
                thumbnail = null,
                maxConfidence = 0.2f,
                averageConfidence = 0.2f,
                priceRange = 0.0 to 0.0,
            ).apply {
                enhancedCategory = ItemCategory.HOME_GOOD
                enhancedLabelText = "Sofa"
                classificationConfidence = 0.8f
                sourceDetectionIds.add("42")
            }

        val detection =
            DetectionResult(
                bboxNorm = NormalizedRect(0f, 0f, 0.3f, 0.3f),
                category = ItemCategory.UNKNOWN,
                priceRange = 0.0 to 0.0,
                confidence = 0.1f,
                trackingId = 42,
                priceEstimationStatus = PriceEstimationStatus.Estimating,
            )

        val result =
            mapOverlayTracks(
                detections = listOf(detection),
                aggregatedItems = listOf(aggregated),
                readyConfidenceThreshold = 0.5f,
            ).first()

        assertThat(result.label).isEqualTo("Sofa")
        assertThat(result.isReady).isTrue()
        assertThat(result.confidence).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun whenNoClassificationShowsScanningLabel() {
        val detection =
            DetectionResult(
                bboxNorm = NormalizedRect(0.1f, 0.1f, 0.4f, 0.4f),
                category = ItemCategory.UNKNOWN,
                priceRange = 0.0 to 0.0,
                confidence = 0.2f,
                trackingId = 7,
            )

        val result =
            mapOverlayTracks(
                detections = listOf(detection),
                aggregatedItems = emptyList(),
                readyConfidenceThreshold = 0.5f,
            ).first()

        assertThat(result.label).isEqualTo("Scanning…")
        assertThat(result.isReady).isFalse()
    }

    @Test
    fun whenTrackingIdNotMappedStaysPending() {
        val aggregated =
            AggregatedItem(
                aggregatedId = "agg-other",
                category = ItemCategory.HOME_GOOD,
                labelText = "Book",
                boundingBox = NormalizedRect(0f, 0f, 0.2f, 0.2f),
                thumbnail = null,
                maxConfidence = 0.9f,
                averageConfidence = 0.9f,
                priceRange = 1.0 to 2.0,
                priceEstimationStatus = PriceEstimationStatus.Ready(PriceRange(Money(1.0), Money(2.0))),
            )

        val detection =
            DetectionResult(
                bboxNorm = NormalizedRect(0.2f, 0.2f, 0.5f, 0.5f),
                category = ItemCategory.UNKNOWN,
                priceRange = 0.0 to 0.0,
                confidence = 0.4f,
                trackingId = 99,
            )

        val result =
            mapOverlayTracks(
                detections = listOf(detection),
                aggregatedItems = listOf(aggregated),
                readyConfidenceThreshold = 0.7f,
            ).first()

        assertThat(result.aggregatedId).isNull()
        assertThat(result.isReady).isFalse()
    }
}
