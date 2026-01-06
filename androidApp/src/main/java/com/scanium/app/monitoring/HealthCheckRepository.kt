package com.scanium.app.monitoring

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Repository for performing backend health checks.
 * DEV-only: Used by the background health monitor.
 *
 * Endpoints checked:
 * 1. GET /health - PASS if HTTP 200
 * 2. GET /v1/config - PASS if 200; or 401 when no API key
 * 3. GET /v1/preflight - PASS if 200; or 401 when no API key
 * 4. GET /v1/assist/status - PASS if 200 or 403 (reachable but protected)
 */
class HealthCheckRepository {
    companion object {
        private const val TAG = "HealthCheck"
        private const val TIMEOUT_SECONDS = 10L

        /**
         * Endpoints to check with their pass conditions.
         */
        private val ENDPOINTS = listOf(
            EndpointSpec("/health", requiresAuth = false, allowedCodes = setOf(200)),
            EndpointSpec("/v1/config", requiresAuth = true, allowedCodes = setOf(200), unauthAllowedCodes = setOf(200, 401)),
            EndpointSpec("/v1/preflight", requiresAuth = true, allowedCodes = setOf(200), unauthAllowedCodes = setOf(200, 401)),
            EndpointSpec("/v1/assist/status", requiresAuth = false, allowedCodes = setOf(200, 403), unauthAllowedCodes = setOf(200, 403)),
        )
    }

    private data class EndpointSpec(
        val path: String,
        val requiresAuth: Boolean,
        val allowedCodes: Set<Int>,
        val unauthAllowedCodes: Set<Int> = allowedCodes,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Performs health checks on all configured endpoints.
     *
     * @param config Health monitor configuration (base URL, API key, etc.)
     * @return HealthCheckResult with overall status and individual endpoint results
     */
    suspend fun performHealthCheck(config: HealthMonitorConfig): HealthCheckResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val hasApiKey = !config.apiKey.isNullOrBlank()

        Log.d(TAG, "Starting health check for ${config.baseUrl} (hasApiKey=$hasApiKey)")

        // Check all endpoints in parallel
        val results = ENDPOINTS.map { spec ->
            async {
                checkEndpoint(config.baseUrl, spec, config.apiKey)
            }
        }.awaitAll()

        val failures = results.filter { !it.passed }
        val status = if (failures.isEmpty()) MonitorHealthStatus.OK else MonitorHealthStatus.FAIL

        val summary = if (failures.isEmpty()) {
            "All ${results.size} endpoints healthy"
        } else {
            "${failures.size}/${results.size} endpoints failed"
        }

        Log.d(TAG, "Health check complete: $status ($summary)")

        HealthCheckResult(
            status = status,
            checkedAt = startTime,
            detailsSummary = summary,
            failures = failures,
        )
    }

    /**
     * Check a single endpoint.
     */
    private fun checkEndpoint(
        baseUrl: String,
        spec: EndpointSpec,
        apiKey: String?,
    ): EndpointCheckResult {
        val url = baseUrl.trimEnd('/') + spec.path
        val hasKey = !apiKey.isNullOrBlank()
        val allowedCodes = if (hasKey) spec.allowedCodes else spec.unauthAllowedCodes

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        // Add API key header if provided (but don't log it!)
        if (hasKey && spec.requiresAuth) {
            requestBuilder.addHeader("X-API-Key", apiKey!!)
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()

            val passed = code in allowedCodes
            val failureReason = if (passed) null else formatFailureReason(spec.path, code)

            Log.d(TAG, "${spec.path}: $code (passed=$passed)")

            EndpointCheckResult(
                endpoint = spec.path,
                passed = passed,
                httpCode = code,
                failureReason = failureReason,
            )
        } catch (e: IOException) {
            val reason = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "timeout"
                e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "unreachable"
                e.message?.contains("Connection refused", ignoreCase = true) == true -> "connection refused"
                else -> "network error"
            }

            Log.w(TAG, "${spec.path}: $reason (${e.message})")

            EndpointCheckResult(
                endpoint = spec.path,
                passed = false,
                httpCode = null,
                failureReason = "${spec.path.removePrefix("/")} $reason",
            )
        }
    }

    /**
     * Format a human-readable failure reason (no secrets).
     */
    private fun formatFailureReason(path: String, code: Int): String {
        val endpoint = path.removePrefix("/").replace("/", " ")
        val codeDesc = when (code) {
            401 -> "unauthorized"
            403 -> "forbidden"
            404 -> "not found"
            500 -> "server error"
            502 -> "bad gateway"
            503 -> "service unavailable"
            504 -> "gateway timeout"
            else -> "HTTP $code"
        }
        return "$endpoint $codeDesc ($code)"
    }
}
