package com.scanium.app.pricing

import com.scanium.app.BuildConfig
import com.scanium.app.network.security.RequestSigner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

@Serializable
data class PricingV4Request(
    val itemId: String,
    val brand: String,
    val productType: String,
    val model: String? = null,
    val condition: String,
    val countryCode: String,
    val preferredMarketplaces: List<String> = emptyList(),
    val variantAttributes: Map<String, String> = emptyMap(),
    val completeness: List<String> = emptyList(),
    val identifier: String? = null,
)

@Serializable
data class PricingV4Response(
    val success: Boolean,
    val pricing: PricingV4PricingDto? = null,
    val cached: Boolean? = null,
    val processingTimeMs: Long? = null,
    val error: PricingV4ErrorDto? = null,
)

@Serializable
data class PricingV4PricingDto(
    val status: String,
    val countryCode: String,
    val sources: List<PricingV4SourceDto> = emptyList(),
    val totalListingsAnalyzed: Int = 0,
    val timeWindowDays: Int = 0,
    val range: PricingV4RangeDto? = null,
    val sampleListings: List<PricingV4SampleListingDto> = emptyList(),
    val confidence: String? = null,
    val fallbackReason: String? = null,
)

@Serializable
data class PricingV4SourceDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val listingCount: Int,
    val searchUrl: String? = null,
)

@Serializable
data class PricingV4SampleListingDto(
    val title: String,
    val price: Double,
    val currency: String,
    val condition: String? = null,
    val url: String? = null,
    val marketplace: String,
)

@Serializable
data class PricingV4RangeDto(
    val low: Double,
    val median: Double,
    val high: Double,
    val currency: String,
)

@Serializable
data class PricingV4ErrorDto(
    val code: String,
    val message: String? = null,
    val resetAt: String? = null,
)

@Serializable
private data class PricingV4ErrorResponseDto(
    val error: PricingV4ErrorDto? = null,
)

class PricingV4Api(
    private val client: OkHttpClient,
    private val json: Json = DEFAULT_JSON,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun estimatePrice(
        endpoint: String,
        requestPayload: PricingV4Request,
        apiKey: String,
        correlationId: String,
        deviceId: String? = null,
        authToken: String? = null,
    ): PricingV4Response =
        withContext(ioDispatcher) {
            val payloadJson = json.encodeToString(PricingV4Request.serializer(), requestPayload)
            val requestBody = payloadJson.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder =
                Request
                    .Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header("X-API-Key", apiKey)
                    .header("X-Scanium-Correlation-Id", correlationId)
                    .header("X-Client", "Scanium-Android")
                    .header("X-App-Version", BuildConfig.VERSION_NAME)

            RequestSigner.addSignatureHeaders(
                builder = requestBuilder,
                apiKey = apiKey,
                requestBody = payloadJson,
            )

            if (!deviceId.isNullOrBlank()) {
                requestBuilder.header("X-Scanium-Device-Id", deviceId)
            }

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $authToken")
            }

            val httpRequest = requestBuilder.build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        return@use json.decodeFromString(PricingV4Response.serializer(), responseBody)
                    }

                    val errorDetails =
                        responseBody
                            ?.let {
                                runCatching {
                                    json.decodeFromString(PricingV4ErrorResponseDto.serializer(), it)
                                }.getOrNull()
                            }?.error

                    throw PricingV4Exception(
                        errorCode = errorDetails?.code ?: httpErrorCode(response.code),
                        userMessage = errorDetails?.message ?: httpErrorMessage(response.code),
                        retryable = isRetryableStatus(response.code),
                        retryAfterSeconds = parseRetryAfterSeconds(response.header("Retry-After"), errorDetails?.resetAt),
                        httpStatus = response.code,
                    )
                }
            } catch (error: PricingV4Exception) {
                throw error
            } catch (error: SocketTimeoutException) {
                throw PricingV4Exception(
                    errorCode = "TIMEOUT",
                    userMessage = "Pricing request timed out",
                    retryable = true,
                    cause = error,
                )
            } catch (error: java.net.UnknownHostException) {
                throw PricingV4Exception(
                    errorCode = "NETWORK_UNREACHABLE",
                    userMessage = "Unable to reach pricing service",
                    retryable = true,
                    cause = error,
                )
            } catch (error: java.net.ConnectException) {
                throw PricingV4Exception(
                    errorCode = "NETWORK_UNREACHABLE",
                    userMessage = "Unable to connect to pricing service",
                    retryable = true,
                    cause = error,
                )
            } catch (error: IOException) {
                throw PricingV4Exception(
                    errorCode = "NETWORK_ERROR",
                    userMessage = "Network error contacting pricing service",
                    retryable = true,
                    cause = error,
                )
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DEFAULT_JSON =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
                isLenient = true
            }

        private fun httpErrorCode(statusCode: Int): String =
            when (statusCode) {
                400 -> "INVALID_REQUEST"
                401 -> "UNAUTHORIZED"
                403 -> "FORBIDDEN"
                404 -> "NOT_FOUND"
                429 -> "RATE_LIMITED"
                504 -> "TIMEOUT"
                in 500..599 -> "SERVER_ERROR"
                else -> "HTTP_$statusCode"
            }

        private fun httpErrorMessage(statusCode: Int): String =
            when (statusCode) {
                400 -> "Pricing request invalid"
                401 -> "Not authorized to use pricing"
                403 -> "Access to pricing denied"
                404 -> "Pricing endpoint not found"
                429 -> "Pricing rate limit exceeded"
                504 -> "Pricing request timed out"
                in 500..599 -> "Pricing service unavailable"
                else -> "Pricing service error"
            }

        private fun isRetryableStatus(statusCode: Int): Boolean =
            statusCode == 429 || statusCode == 504 || statusCode in 500..599

        private fun parseRetryAfterSeconds(
            retryAfterHeader: String?,
            resetAt: String?,
        ): Int? {
            retryAfterHeader?.toIntOrNull()?.let { return it }
            if (resetAt.isNullOrBlank()) return null
            val resetTime =
                runCatching {
                    java.time.Instant.parse(resetAt).toEpochMilli()
                }.getOrNull() ?: return null
            return ((resetTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        }
    }
}
