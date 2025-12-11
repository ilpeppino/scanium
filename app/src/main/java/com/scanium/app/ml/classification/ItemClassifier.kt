package com.scanium.app.ml.classification

import android.graphics.Bitmap

/**
 * Contract for enhanced item classifiers.
 */
interface ItemClassifier {
    suspend fun classifySingle(bitmap: Bitmap): ClassificationResult?
}

/**
 * No-op classifier used when classification is disabled or unavailable.
 */
object NoopClassifier : ItemClassifier {
    override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? = null
}
