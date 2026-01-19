package com.scanium.app.ml.classification

import android.graphics.Bitmap
import com.scanium.app.NormalizedRect

/**
 * Contract for enhanced item classifiers.
 */
interface ItemClassifier {
    suspend fun classifySingle(input: ClassificationInput): ClassificationResult?
}

/**
 * No-op classifier used when classification is disabled or unavailable.
 */
object NoopClassifier : ItemClassifier {
    override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? = null
}

/**
 * Metadata provided to classifiers alongside the bitmap being evaluated.
 *
 * @param aggregatedId Stable identifier for the aggregated item
 * @param bitmap Cropped bitmap that will be uploaded/classified
 * @param boundingBox Normalized bounding box associated with the crop (if available)
 */
data class ClassificationInput(
    val aggregatedId: String,
    val bitmap: Bitmap,
    val boundingBox: NormalizedRect? = null,
)
