package com.scanium.app.assistant

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.shared.core.models.assistant.AssistantPromptRequest
import com.scanium.shared.core.models.assistant.AssistantResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AssistantRepository {
    companion object {
        private const val TAG = "AssistantRepository"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun sendMessage(request: AssistantPromptRequest): Result<AssistantResponse> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(Exception("Assistant API not configured"))
        
        val apiKey = BuildConfig.SCANIUM_API_KEY.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(Exception("API Key missing"))

        val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
        val correlationId = CorrelationIds.currentClassificationSessionId()

        try {
            val requestBody = json.encodeToString(request).toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("X-API-Key", apiKey)
                .header("X-Scanium-Correlation-Id", correlationId)
                .header("X-Client", "Scanium-Android")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    val errorMsg = "Assistant API error: ${response.code} ${response.message}"
                    ScaniumLog.e(TAG, "$errorMsg - Body: $responseBody")
                    return@use Result.failure(IOException(errorMsg))
                }

                if (responseBody == null) {
                    return@use Result.failure(IOException("Empty response body"))
                }

                val assistantResponse = json.decodeFromString<AssistantResponse>(responseBody)
                Result.success(assistantResponse)
            }
        } catch (e: Exception) {
            ScaniumLog.e(TAG, "Failed to send assistant message", e)
            Result.failure(e)
        }
    }
}
