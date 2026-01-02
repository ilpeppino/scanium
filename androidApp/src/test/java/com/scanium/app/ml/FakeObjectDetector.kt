package com.scanium.app.ml

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.ml.LabelWithConfidence
import com.scanium.core.models.ml.RawDetection

/**
 * Fake ObjectDetector for testing purposes.
 * Provides deterministic detection results without requiring ML Kit infrastructure.
 *
 * This fake allows testing the candidate tracking pipeline without actual ML Kit dependencies.
 */
class FakeObjectDetector {
    /**
     * Simulates detection of a single object with configurable properties.
     */
    fun detectSingleObject(
        trackingId: String,
        confidence: Float,
        category: ItemCategory,
        categoryLabel: String,
        boundingBox: NormalizedRect = defaultBoundingBox(),
    ): RawDetection {
        return RawDetection(
            trackingId = trackingId,
            bboxNorm = boundingBox,
            labels =
                listOf(
                    LabelWithConfidence(
                        text = categoryLabel,
                        confidence = confidence,
                        index = 0,
                    ),
                ),
        )
    }

    /**
     * Simulates detection of multiple objects in a single frame.
     */
    fun detectMultipleObjects(detections: List<DetectionSpec>): List<RawDetection> =
        detections.map { spec ->
            RawDetection(
                trackingId = spec.trackingId,
                bboxNorm = spec.boundingBox,
                labels =
                    listOf(
                        LabelWithConfidence(
                            text = spec.categoryLabel,
                            confidence = spec.confidence,
                            index = 0,
                        ),
                    ),
            )
        }

    /**
     * Simulates a detection with no results (empty frame).
     */
    fun detectNothing(): List<RawDetection> {
        return emptyList()
    }

    /**
     * Simulates a detection with multiple labels for the same object.
     */
    fun detectWithMultipleLabels(
        trackingId: String,
        labels: List<LabelSpec>,
        boundingBox: NormalizedRect = defaultBoundingBox(),
    ): RawDetection =
        RawDetection(
            trackingId = trackingId,
            bboxNorm = boundingBox,
            labels =
                labels.mapIndexed { index, spec ->
                    LabelWithConfidence(
                        text = spec.text,
                        confidence = spec.confidence,
                        index = index,
                    )
                },
        )
}

/**
 * Specification for a single detection.
 */
data class DetectionSpec(
    val trackingId: String,
    val confidence: Float,
    val category: ItemCategory,
    val categoryLabel: String,
    val boundingBox: NormalizedRect = defaultBoundingBox(),
)

/**
 * Specification for a label.
 */
data class LabelSpec(
    val text: String,
    val confidence: Float,
)

private fun defaultBoundingBox(): NormalizedRect =
    NormalizedRect(
        left = 0f,
        top = 0f,
        right = 0.1f,
        bottom = 0.1f,
    )

// Note: LabelWithConfidence is defined in RawDetection.kt
