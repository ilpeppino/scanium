package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Lightweight on-device classifier placeholder.
 *
 * In a production app this would wrap a TFLite/CLIP model. For now we derive
 * simple features (dominant brightness and aspect ratio) to produce stable
 * labels without blocking the main thread.
 */
class OnDeviceClassifier : ItemClassifier {
    companion object {
        private const val TAG = "OnDeviceClassifier"
        private const val SAMPLE_SIZE = 96
    }

    override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? = withContext(Dispatchers.Default) {
        runCatching {
            val resized = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
            try {
                var sum = 0L
                var brightest = 0
                var darkest = 255

                for (y in 0 until resized.height step 4) {
                    for (x in 0 until resized.width step 4) {
                        val pixel = resized.getPixel(x, y)
                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val b = android.graphics.Color.blue(pixel)
                        val luma = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
                        sum += luma
                        brightest = maxOf(brightest, luma)
                        darkest = minOf(darkest, luma)
                    }
                }

                val sampleCount = (resized.height / 4) * (resized.width / 4)
                val avg = sum / sampleCount
                val contrast = brightest - darkest
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                val label = when {
                    contrast < 15 -> "flat surface"
                    avg > 170 -> "bright device"
                    aspectRatio > 1.3f -> "wide object"
                    else -> "generic item"
                }

                val category = ItemCategory.fromClassifierLabel(label)
                val confidence = when (label) {
                    "bright device" -> 0.78f
                    "wide object" -> 0.65f
                    "flat surface" -> 0.55f
                    else -> 0.6f
                }

                Log.d(TAG, "On-device classification label=$label avg=$avg contrast=$contrast aspect=$aspectRatio")
                ClassificationResult(
                    label = label,
                    confidence = confidence,
                    category = category,
                    mode = ClassificationMode.ON_DEVICE
                )
            } finally {
                // Recycle temporary scaled bitmap to avoid memory leak
                // (Only safe for API 24-25; API 26+ uses heap allocation anyway)
                if (resized != bitmap) {
                    resized.recycle()
                }
            }
        }.getOrNull()
    }
}
