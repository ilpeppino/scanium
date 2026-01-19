package com.scanium.app.assistant

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.network.security.RequestSigner
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import com.scanium.app.telemetry.TraceContext
import com.scanium.shared.core.models.assistant.AssistantPromptRequest
import com.scanium.shared.core.models.assistant.AssistantResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Error response from the AI Gateway.
 */
@Serializable
private data class GatewayErrorResponse(
    val error: GatewayError? = null,
)

@Serializable
private data class GatewayError(
    val code: String,
    val message: String,
    val correlationId: String? = null,
)

/**
 * Exception with user-friendly message for display in UI.
 */
class AssistantException(
    val errorCode: String,
    val userMessage: String,
    val retryAfterSeconds: Int? = null,
) : Exception(userMessage)

/**
 * Repository for AI Assistant API calls.
 *
 * Features:
 * - Client-side throttling to prevent accidental rapid-fire requests
 * - User-friendly error messages for different error codes
 * - Device ID header for rate limiting (hashed for privacy)
 * - Unified timeout configuration via AssistantHttpConfig
 *
 * @param apiKeyProvider Provider for the API key
 * @param getDeviceId Provider for the device ID (for rate limiting)
 * @param httpConfig HTTP timeout and retry configuration
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class AssistantRepository(
    private val apiKeyProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
    private val httpConfig: AssistantHttpConfig = AssistantHttpConfig.DEFAULT,
    private val telemetry: com.scanium.telemetry.facade.Telemetry? = null,
) {
    companion object {
        private const val TAG = "AssistantRepository"
        private const val MIN_REQUEST_INTERVAL_MS = 1000L // 1 second minimum between requests
    }

    private val client: OkHttpClient = AssistantOkHttpClientFactory.create(httpConfig)

    init {
        AssistantOkHttpClientFactory.logConfigurationUsage("assistant-chat", httpConfig)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false // Don't encode null values for optional fields
        }

    // Client-side throttling
    private val lastRequestTime = AtomicLong(0)

    /**
     * Send a message to the AI assistant.
     *
     * @param request The request containing context and user message
     * @return Result with the assistant response or failure with AssistantException
     */
    suspend fun sendMessage(request: AssistantPromptRequest): Result<AssistantResponse> =
        withContext(Dispatchers.IO) {
            // Create span for assistant request
            val parentSpan = TraceContext.getActiveSpan()
            val assistSpan =
                if (parentSpan != null) {
                    telemetry?.beginChildSpan(
                        "assistant.request",
                        parentSpan,
                        mapOf(
                            "message_length" to request.userMessage.length.toString(),
                            "item_count" to request.items.size.toString(),
                        ),
                    )
                } else {
                    telemetry?.beginSpan(
                        "assistant.request",
                        mapOf(
                            "message_length" to request.userMessage.length.toString(),
                            "item_count" to request.items.size.toString(),
                        ),
                    )
                }

            // Set as active for HTTP header injection
            assistSpan?.let { TraceContext.setActiveSpan(it) }
            val startTime = System.currentTimeMillis()

            val baseUrl =
                BuildConfig.SCANIUM_API_BASE_URL.takeIf { it.isNotBlank() }
                    ?: return@withContext Result
                        .failure<AssistantResponse>(
                            AssistantException(
                                errorCode = "CONFIG_ERROR",
                                userMessage = "Assistant is not configured. Please check app settings.",
                            ),
                        ).also {
                            assistSpan?.end(mapOf("status" to "config_error"))
                            TraceContext.clearActiveSpan()
                        }

            val apiKey =
                apiKeyProvider()?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result
                        .failure<AssistantResponse>(
                            AssistantException(
                                errorCode = "CONFIG_ERROR",
                                userMessage = "API key is missing. Please check app settings.",
                            ),
                        ).also {
                            assistSpan?.end(mapOf("status" to "config_error"))
                            TraceContext.clearActiveSpan()
                        }

            // Client-side throttling
            val now = System.currentTimeMillis()
            val lastTime = lastRequestTime.get()
            if (now - lastTime < MIN_REQUEST_INTERVAL_MS) {
                return@withContext Result
                    .failure<AssistantResponse>(
                        AssistantException(
                            errorCode = "THROTTLED",
                            userMessage = "Please wait a moment before sending another message.",
                        ),
                    ).also {
                        assistSpan?.end(mapOf("status" to "throttled"))
                        TraceContext.clearActiveSpan()
                    }
            }
            lastRequestTime.set(now)

            val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
            val correlationId = CorrelationIds.currentClassificationSessionId()

            try {
                val requestBodyJson = json.encodeToString(request)
                val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())

                val httpRequestBuilder =
                    Request
                        .Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .header("X-API-Key", apiKey)
                        .header("X-Scanium-Correlation-Id", correlationId)
                        .header("X-Client", "Scanium-Android")
                        .header("X-App-Version", BuildConfig.VERSION_NAME)

                // Add HMAC signature for replay protection (SEC-004)
                RequestSigner.addSignatureHeaders(
                    builder = httpRequestBuilder,
                    apiKey = apiKey,
                    requestBody = requestBodyJson,
                )

                // Add device ID for rate limiting (hashed for privacy)
                val deviceId = getDeviceId()
                if (deviceId.isNotBlank()) {
                    httpRequestBuilder.header("X-Scanium-Device-Id", hashDeviceId(deviceId))
                }

                val httpRequest = httpRequestBuilder.build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()

                    when {
                        response.isSuccessful -> {
                            if (responseBody == null) {
                                return@use Result.failure(
                                    AssistantException(
                                        errorCode = "EMPTY_RESPONSE",
                                        userMessage = "No response received. Please try again.",
                                    ),
                                )
                            }

                            val assistantResponse = json.decodeFromString<AssistantResponse>(responseBody)
                            val latencyMs = System.currentTimeMillis() - startTime

                            // Record success metrics
                            telemetry?.timer(
                                "mobile.api.duration_ms",
                                latencyMs,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to "200",
                                ),
                            )
                            telemetry?.counter(
                                "mobile.api.request_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status" to "success",
                                ),
                            )

                            // Check if response was blocked by safety filters
                            val safety = assistantResponse.safety
                            if (safety?.blocked == true) {
                                ScaniumLog.w(TAG, "Response blocked: ${safety.reasonCode}")
                            }

                            // End span with success attributes
                            assistSpan?.end(
                                mapOf(
                                    "status_code" to "200",
                                    "latency_ms" to latencyMs.toString(),
                                    "blocked" to (safety?.blocked?.toString() ?: "false"),
                                ),
                            )
                            TraceContext.clearActiveSpan()

                            Result.success(assistantResponse)
                        }

                        response.code == 401 -> {
                            telemetry?.counter(
                                "mobile.api.error_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to "401",
                                ),
                            )
                            assistSpan?.recordError("Unauthorized", mapOf("status_code" to "401"))
                            assistSpan?.end(mapOf("status" to "error"))
                            TraceContext.clearActiveSpan()

                            Result.failure(
                                AssistantException(
                                    errorCode = "UNAUTHORIZED",
                                    userMessage = "Authentication failed. Please restart the app.",
                                ),
                            )
                        }

                        response.code == 429 -> {
                            val retryAfter = response.header("Retry-After")?.toIntOrNull()
                            val errorResponse =
                                responseBody?.let {
                                    runCatching { json.decodeFromString<GatewayErrorResponse>(it) }.getOrNull()
                                }

                            val userMessage =
                                when (errorResponse?.error?.code) {
                                    "QUOTA_EXCEEDED" -> "Daily message limit reached. Try again tomorrow."
                                    "RATE_LIMITED" -> "Please wait before sending another message."
                                    else -> "Too many requests. Please wait a moment."
                                }

                            telemetry?.counter(
                                "mobile.api.error_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to "429",
                                ),
                            )
                            assistSpan?.recordError(userMessage, mapOf("status_code" to "429"))
                            assistSpan?.end(mapOf("status" to "rate_limited"))
                            TraceContext.clearActiveSpan()

                            Result.failure(
                                AssistantException(
                                    errorCode = errorResponse?.error?.code ?: "RATE_LIMITED",
                                    userMessage = userMessage,
                                    retryAfterSeconds = retryAfter,
                                ),
                            )
                        }

                        response.code == 400 -> {
                            telemetry?.counter(
                                "mobile.api.error_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to "400",
                                ),
                            )
                            assistSpan?.recordError("Validation error", mapOf("status_code" to "400"))
                            assistSpan?.end(mapOf("status" to "validation_error"))
                            TraceContext.clearActiveSpan()

                            Result.failure(
                                AssistantException(
                                    errorCode = "VALIDATION_ERROR",
                                    userMessage = "Message could not be processed. Please try a shorter message.",
                                ),
                            )
                        }

                        response.code == 503 -> {
                            telemetry?.counter(
                                "mobile.api.error_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to "503",
                                ),
                            )
                            assistSpan?.recordError("Service unavailable", mapOf("status_code" to "503"))
                            assistSpan?.end(mapOf("status" to "service_unavailable"))
                            TraceContext.clearActiveSpan()

                            Result.failure(
                                AssistantException(
                                    errorCode = "SERVICE_UNAVAILABLE",
                                    userMessage = "Assistant is temporarily unavailable. Please try again later.",
                                ),
                            )
                        }

                        else -> {
                            telemetry?.counter(
                                "mobile.api.error_count",
                                1,
                                mapOf(
                                    "endpoint" to "/v1/assist/chat",
                                    "status_code" to response.code.toString(),
                                ),
                            )
                            assistSpan?.recordError("Unexpected error", mapOf("status_code" to response.code.toString()))
                            assistSpan?.end(mapOf("status" to "error"))
                            TraceContext.clearActiveSpan()

                            ScaniumLog.e(TAG, "Unexpected error: ${response.code} - ${ScaniumLog.sanitizeResponseBody(responseBody)}")
                            Result.failure(
                                AssistantException(
                                    errorCode = "UNKNOWN_ERROR",
                                    userMessage = "Something went wrong. Please try again.",
                                ),
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                telemetry?.counter(
                    "mobile.api.error_count",
                    1,
                    mapOf(
                        "endpoint" to "/v1/assist/chat",
                        "error_type" to "network",
                    ),
                )
                assistSpan?.recordError("Network error: ${e.message}")
                assistSpan?.end(mapOf("status" to "network_error"))
                TraceContext.clearActiveSpan()

                ScaniumLog.e(TAG, "Network error", e)
                Result.failure(
                    AssistantException(
                        errorCode = "NETWORK_ERROR",
                        userMessage = "Network error. Please check your connection and try again.",
                    ),
                )
            } catch (e: Exception) {
                telemetry?.counter(
                    "mobile.api.error_count",
                    1,
                    mapOf(
                        "endpoint" to "/v1/assist/chat",
                        "error_type" to (e::class.simpleName ?: "unknown"),
                    ),
                )
                assistSpan?.recordError("Exception: ${e.message}")
                assistSpan?.end(mapOf("status" to "exception"))
                TraceContext.clearActiveSpan()

                ScaniumLog.e(TAG, "Failed to send assistant message", e)
                Result.failure(
                    AssistantException(
                        errorCode = "UNKNOWN_ERROR",
                        userMessage = "Something went wrong. Please try again.",
                    ),
                )
            } finally {
                // Ensure TraceContext is always cleared
                TraceContext.clearActiveSpan()
            }
        }

    private fun hashDeviceId(deviceId: String): String =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceId.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            deviceId // Fallback to original if hashing fails
        }
}
