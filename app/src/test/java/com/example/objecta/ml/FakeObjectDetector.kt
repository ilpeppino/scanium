package com.example.objecta.ml

import android.graphics.Rect

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
        boundingBox: Rect = Rect(0, 0, 100, 100)
    ): RawDetection {
        return RawDetection(
            trackingId = trackingId,
            boundingBox = boundingBox,
            labels = listOf(
                LabelWithConfidence(
                    text = categoryLabel,
                    confidence = confidence,
                    index = 0
                )
            ),
            thumbnail = null
        )
    }

    /**
     * Simulates detection of multiple objects in a single frame.
     */
    fun detectMultipleObjects(
        detections: List<DetectionSpec>
    ): List<RawDetection> {
        return detections.map { spec ->
            RawDetection(
                trackingId = spec.trackingId,
                boundingBox = spec.boundingBox,
                labels = listOf(
                    LabelWithConfidence(
                        text = spec.categoryLabel,
                        confidence = spec.confidence,
                        index = 0
                    )
                ),
                thumbnail = null
            )
        }
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
        boundingBox: Rect = Rect(0, 0, 100, 100)
    ): RawDetection {
        return RawDetection(
            trackingId = trackingId,
            boundingBox = boundingBox,
            labels = labels.mapIndexed { index, spec ->
                LabelWithConfidence(
                    text = spec.text,
                    confidence = spec.confidence,
                    index = index
                )
            },
            thumbnail = null
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
        val boundingBox: Rect = Rect(0, 0, 100, 100)
    )

    /**
     * Specification for a label.
     */
    data class LabelSpec(
        val text: String,
        val confidence: Float
    )
}

// Note: LabelWithConfidence is defined in RawDetection.kt
