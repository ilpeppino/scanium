package com.scanium.app.telemetry.otlp

import android.util.Log
import com.scanium.app.telemetry.OtlpConfiguration
import com.scanium.telemetry.TelemetryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * HTTP exporter for OTLP (OpenTelemetry Protocol) over HTTP/JSON.
 *
 * Sends telemetry data to an OTLP-compatible backend (e.g., Grafana Alloy) using
 * HTTP POST requests with JSON payloads.
 *
 * ## Thread Safety
 * All export operations are async and non-blocking. Uses coroutines for background export.
 *
 * ## Error Handling
 * - Implements exponential backoff retry logic
 * - Retries on network failures and 5xx errors
 * - Does not retry on 4xx errors (client errors)
 * - Errors are logged but do not throw exceptions (fire-and-forget telemetry)
 */
class OtlpHttpExporter(
    private val otlpConfig: OtlpConfiguration,
    private val telemetryConfig: TelemetryConfig,
) {
    private val tag = "OtlpHttpExporter"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(otlpConfig.httpTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(otlpConfig.httpTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(otlpConfig.httpTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Exports logs to OTLP endpoint with exponential backoff retry.
     * POST {endpoint}/v1/logs
     */
    fun exportLogs(request: ExportLogsServiceRequest) {
        if (!otlpConfig.enabled) return

        scope.launch {
            val payload = json.encodeToString(request)
            val url = "${otlpConfig.endpoint}/v1/logs"

            if (otlpConfig.debugLogging) {
                Log.d(tag, "Exporting ${request.resourceLogs.sumOf { it.scopeLogs.sumOf { it.logRecords.size } }} logs to $url")
            }

            executeWithRetry(url, payload, "logs")
        }
    }

    /**
     * Exports metrics to OTLP endpoint with exponential backoff retry.
     * POST {endpoint}/v1/metrics
     */
    fun exportMetrics(request: ExportMetricsServiceRequest) {
        if (!otlpConfig.enabled) return

        scope.launch {
            val payload = json.encodeToString(request)
            val url = "${otlpConfig.endpoint}/v1/metrics"

            if (otlpConfig.debugLogging) {
                Log.d(tag, "Exporting ${request.resourceMetrics.sumOf { it.scopeMetrics.sumOf { it.metrics.size } }} metrics to $url")
            }

            executeWithRetry(url, payload, "metrics")
        }
    }

    /**
     * Exports traces to OTLP endpoint with exponential backoff retry.
     * POST {endpoint}/v1/traces
     */
    fun exportTraces(request: ExportTraceServiceRequest) {
        if (!otlpConfig.enabled) return

        scope.launch {
            val payload = json.encodeToString(request)
            val url = "${otlpConfig.endpoint}/v1/traces"

            if (otlpConfig.debugLogging) {
                Log.d(tag, "Exporting ${request.resourceSpans.sumOf { it.scopeSpans.sumOf { it.spans.size } }} spans to $url")
            }

            executeWithRetry(url, payload, "traces")
        }
    }

    /**
     * Executes HTTP request with exponential backoff retry.
     *
     * Retry behavior:
     * - Retries on network failures and 5xx server errors
     * - Does NOT retry on 4xx client errors (bad request, auth, etc.)
     * - Exponential backoff: baseMs * 2^attempt (e.g., 1s, 2s, 4s)
     * - Max retries controlled by telemetryConfig.maxRetries
     */
    private suspend fun executeWithRetry(
        url: String,
        payload: String,
        signalType: String,
    ) {
        var attempt = 0
        while (attempt <= telemetryConfig.maxRetries) {
            try {
                val httpRequest =
                    Request
                        .Builder()
                        .url(url)
                        .post(payload.toRequestBody(jsonMediaType))
                        .header("Content-Type", "application/json")
                        .build()

                client.newCall(httpRequest).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            if (otlpConfig.debugLogging) {
                                Log.d(tag, "Successfully exported $signalType (attempt ${attempt + 1})")
                            }
                            return // Success, exit retry loop
                        }

                        response.code in 400..499 -> {
                            // Client error, don't retry
                            Log.w(tag, "Failed to export $signalType: HTTP ${response.code} (client error, not retrying)")
                            return
                        }

                        else -> {
                            // Server error or other, retry
                            Log.w(
                                tag,
                                "Failed to export $signalType: HTTP ${response.code} (attempt ${attempt + 1}/${telemetryConfig.maxRetries + 1})",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error exporting $signalType (attempt ${attempt + 1}/${telemetryConfig.maxRetries + 1}): ${e.message}")
            }

            // If we get here, export failed and we should retry (if attempts remaining)
            attempt++
            if (attempt <= telemetryConfig.maxRetries) {
                val backoffMs = telemetryConfig.retryBackoffMs * 2.0.pow(attempt - 1).toLong()
                if (otlpConfig.debugLogging) {
                    Log.d(tag, "Retrying $signalType export in ${backoffMs}ms...")
                }
                delay(backoffMs)
            } else {
                Log.e(tag, "Failed to export $signalType after ${telemetryConfig.maxRetries + 1} attempts, dropping batch")
            }
        }
    }

    /**
     * Builds resource attributes common to all telemetry signals.
     */
    fun buildResource(): Resource =
        Resource(
            attributes =
                listOf(
                    KeyValue("service.name", AnyValue.string(otlpConfig.serviceName)),
                    KeyValue("service.version", AnyValue.string(otlpConfig.serviceVersion)),
                    KeyValue("deployment.environment", AnyValue.string(otlpConfig.environment)),
                    KeyValue("telemetry.sdk.name", AnyValue.string("scanium-telemetry")),
                    KeyValue("telemetry.sdk.language", AnyValue.string("kotlin")),
                    KeyValue("telemetry.sdk.version", AnyValue.string("1.0.0")),
                ),
        )

    /**
     * Releases resources held by this exporter.
     *
     * This method should be called when the exporter is no longer needed to:
     * - Cancel pending coroutines and prevent new exports
     * - Shutdown the HTTP client's executor service
     * - Evict all connections from the connection pool
     *
     * After calling close(), this exporter should not be used.
     */
    fun close() {
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
