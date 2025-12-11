package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Cloud-based classifier using a configurable HTTPS endpoint.
 *
 * The endpoint is expected to accept a JSON payload containing a base64 image
 * and return a top-1 label with confidence. Failures gracefully return null so
 * the pipeline can fall back to other classifiers.
 */
class CloudClassifier : ItemClassifier {
    companion object {
        private const val TAG = "CloudClassifier"
    }

    override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.CLOUD_CLASSIFIER_URL
        if (endpoint.isBlank()) {
            Log.d(TAG, "Cloud endpoint not configured; skipping")
            return@withContext null
        }

        val payload = JSONObject().apply {
            put("image", bitmap.toBase64())
        }

        return@withContext runCatching {
            val url = URL(endpoint)
            (url.openConnection() as? HttpURLConnection)?.run {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 8_000
                setRequestProperty("Content-Type", "application/json")
                if (BuildConfig.CLOUD_CLASSIFIER_API_KEY.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.CLOUD_CLASSIFIER_API_KEY}")
                }

                outputStream.use { os ->
                    os.write(payload.toString().toByteArray())
                }

                val responseText = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val label = json.optString("label", null)
                val confidence = json.optDouble("confidence", 0.0).toFloat()
                val category = ItemCategory.fromClassifierLabel(label)
                val accepted = confidence >= 0.5f

                if (accepted) {
                    Log.d(TAG, "Cloud classification label=$label confidence=$confidence")
                    ClassificationResult(
                        label = label,
                        confidence = confidence,
                        category = category,
                        mode = ClassificationMode.CLOUD
                    )
                } else {
                    Log.w(TAG, "Cloud response below threshold: $label@$confidence")
                    null
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Cloud classification failed", error)
        }.getOrNull()
    }
}

private fun Bitmap.toBase64(): String {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
