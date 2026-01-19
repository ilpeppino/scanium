package com.scanium.app.classification

import com.scanium.app.ItemCategory
import com.scanium.shared.core.models.classification.ClassificationResult
import com.scanium.shared.core.models.classification.ClassificationSource
import com.scanium.shared.core.models.classification.ClassificationStatus
import com.scanium.shared.core.models.classification.Classifier
import com.scanium.shared.core.models.model.ImageRef

/**
 * Lightweight mock classifier for tests and offline builds.
 * Returns the provided canned result; defaults to UNKNOWN fallback.
 */
class MockClassifier(
    private val cannedResult: ClassificationResult =
        ClassificationResult(
            domainCategoryId = null,
            confidence = 0f,
            source = ClassificationSource.FALLBACK,
            label = null,
            itemCategory = ItemCategory.UNKNOWN,
            attributes = emptyMap(),
            requestId = "mock",
            latencyMs = 0,
            status = ClassificationStatus.SUCCESS,
            errorMessage = null,
        ),
) : Classifier {
    override suspend fun classify(
        thumbnail: ImageRef,
        hint: String?,
        domainPackId: String,
    ): ClassificationResult = cannedResult
}
