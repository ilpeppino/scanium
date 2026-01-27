package com.scanium.app.pricing

import com.scanium.app.BuildConfig
import com.scanium.app.network.security.RequestSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException

@Serializable
data class VariantField(
    val key: String,
    val label: String,
    val type: String,
    val options: List<String>? = null,
    val required: Boolean = false,
)

@Serializable
data class VariantSchema(
    val fields: List<VariantField> = emptyList(),
    val completenessOptions: List<String> = emptyList(),
)

@Serializable
data class VariantSchemaResponse(
    val success: Boolean,
    val fields: List<VariantField> = emptyList(),
    val completenessOptions: List<String> = emptyList(),
    val error: PricingV4ErrorDto? = null,
)

@Serializable
private data class VariantSchemaErrorResponseDto(
    val error: PricingV4ErrorDto? = null,
)

class VariantSchemaApi(
    private val client: OkHttpClient,
    private val json: Json = DEFAULT_JSON,
) {
    suspend fun getSchema(
        endpoint: String,
        productType: String,
        apiKey: String,
        correlationId: String,
        deviceId: String? = null,
        authToken: String? = null,
    ): VariantSchema =
        withContext(Dispatchers.IO) {
            val httpUrl =
                endpoint
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("productType", productType)
                    .build()

            val urlPath = buildString {
                append(httpUrl.encodedPath)
                httpUrl.encodedQuery?.let { query -> append("?").append(query) }
            }

            val requestBuilder =
                Request
                    .Builder()
                    .url(httpUrl)
                    .get()
                    .header("X-API-Key", apiKey)
                    .header("X-Scanium-Correlation-Id", correlationId)
                    .header("X-Client", "Scanium-Android")
                    .header("X-App-Version", BuildConfig.VERSION_NAME)

            RequestSigner.addSignatureHeadersForGet(
                builder = requestBuilder,
                apiKey = apiKey,
                urlPath = urlPath,
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
                        val payload =
                            json.decodeFromString(VariantSchemaResponse.serializer(), responseBody)
                        if (!payload.success) {
                            throw PricingV4Exception(
                                errorCode = payload.error?.code ?: "UNKNOWN_ERROR",
                                userMessage = payload.error?.message ?: "Variant schema request failed",
                                retryable = false,
                            )
                        }

                        return@use VariantSchema(
                            fields = payload.fields,
                            completenessOptions = payload.completenessOptions,
                        )
                    }

                    val errorDetails =
                        responseBody
                            ?.let {
                                runCatching {
                                    json.decodeFromString(VariantSchemaErrorResponseDto.serializer(), it)
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
                    userMessage = "Variant schema request timed out",
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
                400 -> "Variant schema request invalid"
                401 -> "Not authorized to use pricing"
                403 -> "Access to pricing denied"
                404 -> "Variant schema endpoint not found"
                429 -> "Pricing rate limit exceeded"
                504 -> "Variant schema request timed out"
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
