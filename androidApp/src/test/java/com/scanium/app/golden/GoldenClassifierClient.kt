package com.scanium.app.golden

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.ml.classification.CloudClassificationResponse
import com.scanium.app.ml.classification.EnrichedAttributesResponse
import com.scanium.app.ml.classification.VisionAttributesResponse
import com.scanium.app.network.security.RequestSigner
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

sealed class GoldenClassificationOutcome {
    data class Success(val result: GoldenClassificationResult) : GoldenClassificationOutcome()

    data class Error(val message: String, val statusCode: Int? = null) : GoldenClassificationOutcome()
}

data class GoldenClassificationResult(
    val domainCategoryId: String?,
    val confidence: Float,
    val label: String?,
    val attributes: Map<String, String>,
    val enrichedAttributes: Map<String, String>,
    val visionAttributes: VisionAttributesResponse?,
    val requestId: String?,
)

class GoldenClassifierClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val domainPackId: String = "home_resale",
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun classify(imageFile: File): GoldenClassificationOutcome {
        val endpoint = "${baseUrl.trimEnd('/')}/v1/classify?enrichAttributes=true"
        val imageBytes = imageFile.readBytes()
        val contentType = contentTypeFor(imageFile).toMediaType()

        val requestBody =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    imageFile.name,
                    imageBytes.toRequestBody(contentType),
                )
                .addFormDataPart("domainPackId", domainPackId)
                .build()

        val correlationId = CorrelationIds.currentClassificationSessionId()
        val requestBuilder =
            Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("X-Scanium-Correlation-Id", correlationId)
                .header("X-Client", "Scanium-Android-Test")
                .header("X-App-Version", BuildConfig.VERSION_NAME)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("X-API-Key", apiKey)
            RequestSigner.addSignatureHeaders(
                builder = requestBuilder,
                apiKey = apiKey,
                params = mapOf("domainPackId" to domainPackId),
                binaryContentSize = imageBytes.size.toLong(),
            )
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            return GoldenClassificationOutcome.Error(
                message = "HTTP ${response.code}: ${body.take(500)}",
                statusCode = response.code,
            )
        }

        val parsed =
            runCatching { json.decodeFromString(CloudClassificationResponse.serializer(), body) }
                .getOrElse { error ->
                    return GoldenClassificationOutcome.Error(
                        message = "Failed to parse response: ${error.message}",
                    )
                }

        return GoldenClassificationOutcome.Success(
            GoldenClassificationResult(
                domainCategoryId = parsed.domainCategoryId,
                confidence = parsed.confidence ?: 0f,
                label = parsed.label,
                attributes = parsed.attributes.orEmpty(),
                enrichedAttributes = flattenEnriched(parsed.enrichedAttributes),
                visionAttributes = parsed.visionAttributes,
                requestId = parsed.requestId,
            ),
        )
    }

    private fun flattenEnriched(enriched: EnrichedAttributesResponse?): Map<String, String> {
        if (enriched == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        enriched.brand?.let { result["brand"] = it.value }
        enriched.model?.let { result["model"] = it.value }
        enriched.color?.let { result["color"] = it.value }
        enriched.secondaryColor?.let { result["secondaryColor"] = it.value }
        enriched.material?.let { result["material"] = it.value }
        return result
    }

    private fun contentTypeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}

object GoldenClassifierConfig {
    fun baseUrl(): String {
        return firstNonBlank(
            System.getenv("SCANIUM_BASE_URL"),
            System.getenv("SCANIUM_API_BASE_URL"),
            System.getProperty("SCANIUM_BASE_URL"),
            System.getProperty("SCANIUM_API_BASE_URL"),
            BuildConfig.SCANIUM_API_BASE_URL,
        ).orEmpty()
    }

    fun apiKey(): String {
        return firstNonBlank(
            System.getenv("SCANIUM_API_KEY"),
            System.getProperty("SCANIUM_API_KEY"),
            BuildConfig.SCANIUM_API_KEY,
        ).orEmpty()
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }
}
