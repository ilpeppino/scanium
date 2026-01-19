package com.scanium.android.platform.adapters

import com.scanium.app.ml.classification.ClassificationInput
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.model.resolveBytes
import com.scanium.app.platform.toBitmap
import com.scanium.shared.core.models.classification.ClassificationResult
import com.scanium.shared.core.models.classification.ClassificationSource
import com.scanium.shared.core.models.classification.ClassificationStatus
import com.scanium.shared.core.models.classification.Classifier
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.app.ml.ItemCategory as AndroidItemCategory
import com.scanium.app.ml.classification.ClassificationMode as AndroidClassificationMode
import com.scanium.app.ml.classification.ClassificationStatus as AndroidClassificationStatus
import com.scanium.shared.core.models.ml.ItemCategory as SharedItemCategory

/**
 * Adapter that bridges Android ItemClassifier to portable Classifier interface.
 *
 * This allows Android-specific implementations (CloudClassifier, OnDeviceClassifier)
 * to work with the platform-agnostic ClassificationOrchestrator.
 *
 * @param androidClassifier Android-specific classifier implementation
 * @param source Classification source (CLOUD or ON_DEVICE)
 */
class ClassifierAdapter(
    private val androidClassifier: ItemClassifier,
    private val source: ClassificationSource,
) : Classifier {
    override suspend fun classify(
        thumbnail: ImageRef,
        hint: String?,
        domainPackId: String,
    ): ClassificationResult {
        // Convert ImageRef to Bitmap
        val bytes =
            thumbnail.resolveBytes()
                ?: return ClassificationResult(
                    domainCategoryId = null,
                    confidence = 0f,
                    source = source,
                    label = null,
                    status = ClassificationStatus.FAILED,
                    errorMessage = "Unsupported ImageRef type: ${thumbnail::class.simpleName}",
                )

        val bitmap = bytes.toBitmap()

        // Create Android ClassificationInput
        val input =
            ClassificationInput(
                aggregatedId = "temp_id",
// Not used by classifiers
                bitmap = bitmap,
                boundingBox = null,
            )

        // Call Android classifier
        val androidResult = androidClassifier.classifySingle(input)

        // Convert result to shared type
        return if (androidResult != null) {
            convertAndroidResult(androidResult)
        } else {
            // Classifier returned null (not configured or skipped)
            ClassificationResult(
                domainCategoryId = null,
                confidence = 0f,
                source = source,
                label = null,
                status = ClassificationStatus.SKIPPED,
                errorMessage = "Classifier not configured",
            )
        }
    }

    override suspend fun isAvailable(): Boolean {
        // Android classifiers return null when not configured
        // We can't check availability without making a request, so assume available
        return true
    }

    /**
     * Convert Android ClassificationResult to shared ClassificationResult.
     */
    private fun convertAndroidResult(androidResult: com.scanium.app.ml.classification.ClassificationResult): ClassificationResult =
        ClassificationResult(
            domainCategoryId = androidResult.domainCategoryId,
            confidence = androidResult.confidence,
            source = androidResult.mode.toSharedSource(),
            label = androidResult.label,
            itemCategory = androidResult.category.toSharedItemCategory(),
            attributes = androidResult.attributes ?: emptyMap(),
            requestId = androidResult.requestId,
            latencyMs = null,
            status = androidResult.status.toSharedStatus(),
            errorMessage = androidResult.errorMessage,
        )
}

/**
 * Convert Android ClassificationMode to shared ClassificationSource.
 */
private fun AndroidClassificationMode.toSharedSource(): ClassificationSource =
    when (this) {
        AndroidClassificationMode.CLOUD -> ClassificationSource.CLOUD
        AndroidClassificationMode.ON_DEVICE -> ClassificationSource.ON_DEVICE
    }

/**
 * Convert Android ClassificationStatus to shared ClassificationStatus.
 */
private fun AndroidClassificationStatus.toSharedStatus(): ClassificationStatus =
    when (this) {
        AndroidClassificationStatus.PENDING -> ClassificationStatus.SUCCESS

        // Map PENDING to SUCCESS for now
        AndroidClassificationStatus.SUCCESS -> ClassificationStatus.SUCCESS

        AndroidClassificationStatus.FAILED -> ClassificationStatus.FAILED
    }

/**
 * Convert Android ItemCategory to shared ItemCategory.
 * Since AndroidItemCategory is a typealias for SharedItemCategory, they're the same type.
 */
private fun AndroidItemCategory.toSharedItemCategory(): SharedItemCategory = this
