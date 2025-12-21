package com.scanium.app.classification

import com.scanium.shared.core.models.classification.ClassificationMode
import com.scanium.shared.core.models.classification.ClassificationResult
import com.scanium.shared.core.models.classification.ClassificationSource
import com.scanium.shared.core.models.classification.ClassificationStatus
import com.scanium.shared.core.models.classification.Classifier
import com.scanium.shared.core.models.model.ImageRef

/**
 * Safe fallback classifier that never performs network or on-device inference.
 * Returns a skipped result to keep pipelines non-blocking when cloud/on-device
 * classifiers are unavailable.
 */
class NoOpClassifier : Classifier {
    override suspend fun classify(
        thumbnail: ImageRef,
        hint: String?,
        domainPackId: String
    ): ClassificationResult {
        return ClassificationResult(
            domainCategoryId = null,
            confidence = 0f,
            source = ClassificationSource.FALLBACK,
            label = hint,
            attributes = emptyMap(),
            requestId = null,
            latencyMs = 0,
            status = ClassificationStatus.SKIPPED,
            errorMessage = "Classifier disabled"
        )
    }
}
