package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device classifier backed by ML Kit Image Labeling.
 */
class OnDeviceClassifier(
    confidenceThreshold: Float = 0.5f,
    private val labelerClient: ImageLabelerClient = MlKitImageLabeler(confidenceThreshold)
) : ItemClassifier {
    companion object {
        private const val TAG = "OnDeviceClassifier"
    }

    override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? = withContext(Dispatchers.Default) {
        runCatching {
            val labels = labelerClient.label(input.bitmap)

            val bestLabel = labels.maxByOrNull { it.confidence }

            if (bestLabel == null) {
                Log.d(TAG, "Image labeling returned no labels for ${input.aggregatedId}")
                return@withContext ClassificationResult(
                    label = null,
                    confidence = 0f,
                    category = ItemCategory.UNKNOWN,
                    mode = ClassificationMode.ON_DEVICE
                )
            }

            val category = ItemCategory.fromClassifierLabel(bestLabel.text)
            Log.d(TAG, "On-device classification label=${bestLabel.text}, confidence=${bestLabel.confidence}")

            ClassificationResult(
                label = bestLabel.text,
                confidence = bestLabel.confidence,
                category = category,
                mode = ClassificationMode.ON_DEVICE
            )
        }.onFailure { error ->
            Log.w(TAG, "On-device classification failed", error)
        }.getOrNull()
    }

    interface ImageLabelerClient {
        suspend fun label(bitmap: Bitmap): List<LabelResult>
    }

    data class LabelResult(
        val text: String,
        val confidence: Float
    )

    private class MlKitImageLabeler(
        confidenceThreshold: Float
    ) : ImageLabelerClient {
        private val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build()
        )

        override suspend fun label(bitmap: Bitmap): List<LabelResult> {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            return labels.map { LabelResult(it.text, it.confidence) }
        }
    }
}
