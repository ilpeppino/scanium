package com.scanium.app.selling.assistant

import android.content.Context
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.network.security.RequestSigner
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

internal class PreflightPolicy(private val json: Json) {
    fun buildRequest(
        context: Context,
        endpoint: String,
        apiKey: String,
    ): Request {
        val payloadJson = json.encodeToString(PreflightChatRequest.serializer(), PreflightChatRequest())

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payloadJson.toRequestBody("application/json".toMediaType()))
            .header("X-API-Key", apiKey)
            .header("X-Client", "Scanium-Android")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-Scanium-Preflight", "true")

        val deviceId = DeviceIdProvider.getRawDeviceId(context)
        if (deviceId.isNotBlank()) {
            requestBuilder.header("X-Scanium-Device-Id", deviceId)
        }

        RequestSigner.addSignatureHeaders(
            builder = requestBuilder,
            apiKey = apiKey,
            requestBody = payloadJson,
        )

        return requestBuilder.build()
    }

    fun parseSuccessResponse(
        body: String?,
        latencyMs: Long,
    ): PreflightResult {
        val chatResponse = body?.let {
            runCatching { json.decodeFromString<PreflightChatResponse>(it) }.getOrNull()
        }

        if (chatResponse?.assistantError != null) {
            val errorType = chatResponse.assistantError.type.lowercase()
            return when {
                errorType.contains("provider_unavailable") ||
                    errorType.contains("provider_unreachable") -> {
                    PreflightResult(
                        status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                        latencyMs = latencyMs,
                        reasonCode = errorType,
                    )
                }
                else -> {
                    ScaniumLog.w(TAG, "Preflight: 200 with embedded error type=$errorType")
                    PreflightResult(
                        status = PreflightStatus.AVAILABLE,
                        latencyMs = latencyMs,
                    )
                }
            }
        }

        return PreflightResult(
            status = PreflightStatus.AVAILABLE,
            latencyMs = latencyMs,
        )
    }

    fun mapHttpFailure(
        code: Int,
        latencyMs: Long,
    ): PreflightResult {
        return when {
            code == 401 ->
                PreflightResult(
                    status = PreflightStatus.UNAUTHORIZED,
                    latencyMs = latencyMs,
                    reasonCode = "unauthorized_api_key",
                )
            code == 403 ->
                PreflightResult(
                    status = PreflightStatus.UNAUTHORIZED,
                    latencyMs = latencyMs,
                    reasonCode = "forbidden_access",
                )
            code == 404 ->
                PreflightResult(
                    status = PreflightStatus.ENDPOINT_NOT_FOUND,
                    latencyMs = latencyMs,
                    reasonCode = "endpoint_not_found",
                )
            code == 429 ->
                PreflightResult(
                    status = PreflightStatus.RATE_LIMITED,
                    latencyMs = latencyMs,
                    reasonCode = "http_429",
                )
            code == 400 -> {
                ScaniumLog.w(
                    TAG,
                    "Preflight: CLIENT_ERROR (HTTP 400) - preflight request schema mismatch, allowing chat attempt",
                )
                PreflightResult(
                    status = PreflightStatus.CLIENT_ERROR,
                    latencyMs = latencyMs,
                    reasonCode = "preflight_schema_error",
                )
            }
            code in 500..599 ->
                PreflightResult(
                    status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                    latencyMs = latencyMs,
                    reasonCode = "http_$code",
                )
            else -> {
                ScaniumLog.w(TAG, "Preflight: unknown HTTP $code, allowing chat attempt")
                PreflightResult(
                    status = PreflightStatus.UNKNOWN,
                    latencyMs = latencyMs,
                    reasonCode = "http_$code",
                )
            }
        }
    }

    fun mapException(
        exception: Exception,
        latencyMs: Long,
    ): PreflightResult {
        return when (exception) {
            is SocketTimeoutException -> {
                ScaniumLog.w(TAG, "Preflight: UNKNOWN (timeout - will allow chat attempt)", exception)
                PreflightResult(
                    status = PreflightStatus.UNKNOWN,
                    latencyMs = latencyMs,
                    reasonCode = "timeout",
                )
            }
            is java.net.UnknownHostException -> {
                ScaniumLog.w(TAG, "Preflight: OFFLINE (DNS failure)", exception)
                PreflightResult(
                    status = PreflightStatus.OFFLINE,
                    latencyMs = latencyMs,
                    reasonCode = "dns_failure",
                )
            }
            is java.net.ConnectException -> {
                ScaniumLog.w(TAG, "Preflight: OFFLINE (connection refused)", exception)
                PreflightResult(
                    status = PreflightStatus.OFFLINE,
                    latencyMs = latencyMs,
                    reasonCode = "connection_refused",
                )
            }
            is IOException -> {
                ScaniumLog.w(TAG, "Preflight: UNKNOWN (IO error - will allow chat attempt)", exception)
                PreflightResult(
                    status = PreflightStatus.UNKNOWN,
                    latencyMs = latencyMs,
                    reasonCode = "io_error",
                )
            }
            else -> {
                Log.w(TAG, "Preflight: unexpected error", exception)
                PreflightResult(
                    status = PreflightStatus.UNKNOWN,
                    latencyMs = latencyMs,
                    reasonCode = "unexpected_error",
                )
            }
        }
    }

    companion object {
        private const val TAG = "AssistantPreflight"
    }
}

@Serializable
internal data class PreflightChatRequest(
    val message: String = "ping",
    val items: List<PreflightItemDto> = emptyList(),
    val history: List<PreflightHistoryDto> = emptyList(),
)

@Serializable
internal data class PreflightItemDto(
    val itemId: String,
)

@Serializable
internal data class PreflightHistoryDto(
    val role: String,
    val content: String,
    val timestamp: Long,
)

@Serializable
internal data class PreflightChatResponse(
    @SerialName("reply")
    val content: String = "",
    val assistantError: PreflightAssistantErrorDto? = null,
)

@Serializable
internal data class PreflightAssistantErrorDto(
    val type: String = "",
    val category: String = "",
    val retryable: Boolean = false,
    val message: String? = null,
)
