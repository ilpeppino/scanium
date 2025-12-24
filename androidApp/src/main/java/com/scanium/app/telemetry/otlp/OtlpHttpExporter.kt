package com.scanium.app.telemetry.otlp

import android.util.Log
import com.scanium.app.telemetry.OtlpConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP exporter for OTLP (OpenTelemetry Protocol) over HTTP/JSON.
 *
 * Sends telemetry data to an OTLP-compatible backend (e.g., Grafana Alloy) using
 * HTTP POST requests with JSON payloads.
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * All export operations are async and non-blocking. Uses coroutines for background export.
 *
 * ***REMOVED******REMOVED*** Error Handling
 * Errors are logged but do not throw exceptions (fire-and-forget telemetry).
 * Failed exports are dropped (no retry logic for simplicity).
 */
class OtlpHttpExporter(
    private val config: OtlpConfiguration
) {
    private val tag = "OtlpHttpExporter"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.httpTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.httpTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.httpTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Exports logs to OTLP endpoint.
     * POST {endpoint}/v1/logs
     */
    fun exportLogs(request: ExportLogsServiceRequest) {
        if (!config.enabled) return

        scope.launch {
            try {
                val payload = json.encodeToString(request)
                val url = "${config.endpoint}/v1/logs"

                if (config.debugLogging) {
                    Log.d(tag, "Exporting ${request.resourceLogs.sumOf { it.scopeLogs.sumOf { it.logRecords.size } }} logs to $url")
                }

                val httpRequest = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(tag, "Failed to export logs: HTTP ${response.code}")
                    } else if (config.debugLogging) {
                        Log.d(tag, "Successfully exported logs")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error exporting logs", e)
            }
        }
    }

    /**
     * Exports metrics to OTLP endpoint.
     * POST {endpoint}/v1/metrics
     */
    fun exportMetrics(request: ExportMetricsServiceRequest) {
        if (!config.enabled) return

        scope.launch {
            try {
                val payload = json.encodeToString(request)
                val url = "${config.endpoint}/v1/metrics"

                if (config.debugLogging) {
                    Log.d(tag, "Exporting ${request.resourceMetrics.sumOf { it.scopeMetrics.sumOf { it.metrics.size } }} metrics to $url")
                }

                val httpRequest = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(tag, "Failed to export metrics: HTTP ${response.code}")
                    } else if (config.debugLogging) {
                        Log.d(tag, "Successfully exported metrics")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error exporting metrics", e)
            }
        }
    }

    /**
     * Exports traces to OTLP endpoint.
     * POST {endpoint}/v1/traces
     */
    fun exportTraces(request: ExportTraceServiceRequest) {
        if (!config.enabled) return

        scope.launch {
            try {
                val payload = json.encodeToString(request)
                val url = "${config.endpoint}/v1/traces"

                if (config.debugLogging) {
                    Log.d(tag, "Exporting ${request.resourceSpans.sumOf { it.scopeSpans.sumOf { it.spans.size } }} spans to $url")
                }

                val httpRequest = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(tag, "Failed to export traces: HTTP ${response.code}")
                    } else if (config.debugLogging) {
                        Log.d(tag, "Successfully exported traces")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error exporting traces", e)
            }
        }
    }

    /**
     * Builds resource attributes common to all telemetry signals.
     */
    fun buildResource(): Resource {
        return Resource(
            attributes = listOf(
                KeyValue("service.name", AnyValue.string(config.serviceName)),
                KeyValue("service.version", AnyValue.string(config.serviceVersion)),
                KeyValue("deployment.environment", AnyValue.string(config.environment)),
                KeyValue("telemetry.sdk.name", AnyValue.string("scanium-telemetry")),
                KeyValue("telemetry.sdk.language", AnyValue.string("kotlin")),
                KeyValue("telemetry.sdk.version", AnyValue.string("1.0.0"))
            )
        )
    }
}
