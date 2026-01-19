package com.scanium.app.selling.assistant

import android.content.Context
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.model.AssistantResponse
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.network.security.RequestSigner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

internal class AssistantApi(
    private val context: Context,
    private val client: OkHttpClient,
    private val apiKey: String?,
    private val errorMapper: AssistantErrorMapper = AssistantErrorMapper(),
) {
    suspend fun send(
        endpoint: String,
        requestPayload: AssistantChatRequest,
        imageAttachments: List<ItemImageAttachment>,
        correlationId: String,
    ): AssistantResponse {
        val payloadJson = ASSISTANT_JSON.encodeToString(AssistantChatRequest.serializer(), requestPayload)

        Log.d("ScaniumNet", "AssistantApi: endpoint=$endpoint")
        Log.d(
            "ScaniumAssist",
            "Request: items=${requestPayload.items.size} " +
                "history=${requestPayload.history.size} " +
                "message.length=${requestPayload.message.length} " +
                "hasExportProfile=${requestPayload.exportProfile != null} " +
                "hasPrefs=${requestPayload.assistantPrefs != null}",
        )
        Log.d("ScaniumAssist", "Request JSON payload (full): $payloadJson")

        val request =
            if (imageAttachments.isNotEmpty()) {
                buildMultipartRequest(endpoint, payloadJson, imageAttachments, correlationId)
            } else {
                buildJsonRequest(endpoint, payloadJson, correlationId)
            }

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val parsed = ASSISTANT_JSON.decodeFromString(AssistantChatResponse.serializer(), responseBody)
                    parsed.assistantError?.let { throw AssistantBackendException(it.toFailure()) }
                    return parsed.toModel()
                }

                if (response.code == 400) {
                    Log.e("ScaniumAssist", "Validation error (HTTP 400): correlationId=$correlationId")
                    Log.e("ScaniumAssist", "Response body: ${responseBody?.take(500)}")
                }
                Log.d("AssistantRepo", "Assistant backend error: ${response.code} ${ScaniumLog.sanitizeResponseBody(responseBody)}")
                throw errorMapper.mapHttpFailure(response.code, responseBody)
            }
        } catch (error: AssistantBackendException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Assistant request timed out",
                ),
                error,
            )
        } catch (error: java.net.UnknownHostException) {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Unable to reach assistant server",
                ),
                error,
            )
        } catch (error: java.net.ConnectException) {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Could not connect to assistant server",
                ),
                error,
            )
        } catch (error: IOException) {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Network error contacting assistant",
                ),
                error,
            )
        }
    }

    private fun buildJsonRequest(
        endpoint: String,
        payloadJson: String,
        correlationId: String,
    ): Request =
        Request
            .Builder()
            .url(endpoint)
            .post(payloadJson.toRequestBody("application/json".toMediaType()))
            .apply { addCommonHeaders(this, payloadJson, correlationId) }
            .build()

    private fun buildMultipartRequest(
        endpoint: String,
        payloadJson: String,
        imageAttachments: List<ItemImageAttachment>,
        correlationId: String,
    ): Request {
        val multipartBuilder =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload", payloadJson)

        val itemImageCounts = mutableMapOf<String, Int>()
        var totalImageBytes = 0L

        imageAttachments.forEachIndexed { index, attachment ->
            val fieldName = "itemImages[${attachment.itemId}]"
            val mediaType = attachment.mimeType.toMediaType()
            val filename = "image_${index}_${attachment.filename}"
            multipartBuilder.addFormDataPart(
                fieldName,
                filename,
                attachment.imageBytes.toRequestBody(mediaType),
            )
            itemImageCounts[attachment.itemId] = (itemImageCounts[attachment.itemId] ?: 0) + 1
            totalImageBytes += attachment.imageBytes.size
        }

        val multipartBody = multipartBuilder.build()

        Log.i(
            "ScaniumAssist",
            "Multipart request: correlationId=$correlationId " +
                "imageCount=${imageAttachments.size} totalBytes=$totalImageBytes " +
                "itemImageCounts=$itemImageCounts",
        )
        Log.d("AssistantRepo", "Sending multipart request with ${imageAttachments.size} images")

        return Request
            .Builder()
            .url(endpoint)
            .post(multipartBody)
            .apply { addCommonHeaders(this, payloadJson, correlationId) }
            .build()
    }

    private fun addCommonHeaders(
        builder: Request.Builder,
        payloadJson: String,
        correlationId: String,
    ) {
        if (apiKey != null) {
            Log.d("ScaniumAuth", "AssistantApi: apiKey present len=${apiKey.length} prefix=${apiKey.take(6)}...")
            Log.d("ScaniumAuth", "AssistantApi: Adding X-API-Key header")
            builder.header("X-API-Key", apiKey)
            RequestSigner.addSignatureHeaders(
                builder = builder,
                apiKey = apiKey,
                requestBody = payloadJson,
            )
        } else {
            Log.w("ScaniumAuth", "AssistantApi: apiKey is NULL - X-API-Key header will NOT be added!")
        }
        builder.header("X-Scanium-Correlation-Id", correlationId)
        builder.header("X-Client", "Scanium-Android")
        builder.header("X-App-Version", BuildConfig.VERSION_NAME)

        val deviceId = DeviceIdProvider.getRawDeviceId(context)
        if (deviceId.isNotBlank()) {
            builder.header("X-Scanium-Device-Id", deviceId)
        }

        // Phase B: Add session token for authenticated requests
        val authToken =
            com.scanium.app.config
                .SecureApiKeyStore(context)
                .getAuthToken()
        if (authToken != null) {
            Log.d("ScaniumAuth", "AssistantApi: Adding Authorization header")
            builder.header("Authorization", "Bearer $authToken")
        } else {
            Log.w("ScaniumAuth", "AssistantApi: No auth token - Authorization header will NOT be added")
        }
    }
}
