package com.scanium.app.selling.assistant.network

import android.util.Log
import com.scanium.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Factory for creating OkHttpClient instances with unified timeout configuration
 * for all assistant HTTP requests.
 *
 * **Usage:**
 * ```kotlin
 * // Production client for chat requests
 * val chatClient = AssistantOkHttpClientFactory.create(AssistantHttpConfig.DEFAULT)
 *
 * // Preflight client with tight timeouts
 * val preflightClient = AssistantOkHttpClientFactory.create(AssistantHttpConfig.PREFLIGHT)
 *
 * // Custom configuration
 * val customClient = AssistantOkHttpClientFactory.create(
 *     AssistantHttpConfig(connectTimeoutSeconds = 10, readTimeoutSeconds = 30)
 * )
 * ```
 *
 * **Features:**
 * - Applies all timeout settings from AssistantHttpConfig
 * - Adds retry interceptor based on config.retryCount
 * - Adds user-agent with app version
 * - Supports additional custom interceptors
 */
object AssistantOkHttpClientFactory {
    private const val TAG = "AssistantHttp"

    // Track if we've logged the startup policy
    @Volatile
    private var startupPolicyLogged = false

    /**
     * Creates a new OkHttpClient with the specified configuration.
     *
     * @param config Timeout and retry configuration
     * @param additionalInterceptors Optional interceptors to add (e.g., logging, auth)
     * @param logStartupPolicy If true and first call, logs the timeout policy at INFO level
     * @return Configured OkHttpClient instance
     */
    fun create(
        config: AssistantHttpConfig = AssistantHttpConfig.DEFAULT,
        additionalInterceptors: List<Interceptor> = emptyList(),
        logStartupPolicy: Boolean = true,
    ): OkHttpClient {
        // Log startup policy once
        if (logStartupPolicy && !startupPolicyLogged) {
            logPolicy(config)
            startupPolicyLogged = true
        }

        return OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.callTimeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor(com.scanium.app.telemetry.TraceContextInterceptor())
            .apply {
                // Add retry interceptor if retries are enabled
                if (config.retryCount > 0) {
                    addInterceptor(AssistantRetryInterceptor(maxRetries = config.retryCount))
                }

                // Add any additional interceptors
                additionalInterceptors.forEach { addInterceptor(it) }
            }
            .build()
    }

    /**
     * Creates a client based on an existing client, applying new timeout configuration.
     * Useful for creating preflight/warmup clients from a base client.
     *
     * @param baseClient The base client to derive from
     * @param config New timeout configuration to apply
     * @return New client with updated configuration
     */
    fun derive(
        baseClient: OkHttpClient,
        config: AssistantHttpConfig,
    ): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.callTimeoutSeconds, TimeUnit.SECONDS)
            .apply {
                // Note: We don't add retry interceptor here since base client may have it
                // The derive() method is for timeout adjustments only
            }
            .build()
    }

    /**
     * Logs the timeout policy at startup. Called once during app initialization.
     */
    private fun logPolicy(config: AssistantHttpConfig) {
        val policyInfo = buildString {
            appendLine("Assistant HTTP Policy Initialized:")
            appendLine("  Version: ${BuildConfig.VERSION_NAME}")
            appendLine("  Timeouts: ${config.toLogString()}")
            appendLine("  Retry: ${if (config.retryCount > 0) "${config.retryCount}x on transient errors (502/503/504, timeout, network)" else "disabled"}")
            appendLine("  Non-retryable: 400/401/403/404/429")
        }
        Log.i(TAG, policyInfo)
    }

    /**
     * Logs startup policy for the given configuration type.
     * Call this on app startup to show which configuration is active.
     *
     * @param configName Name/label for this configuration (e.g., "chat", "preflight")
     * @param config The configuration being used
     */
    fun logConfigurationUsage(configName: String, config: AssistantHttpConfig) {
        Log.d(TAG, "AssistantHttp[$configName]: ${config.toLogString()}")
    }

    /**
     * Returns information about all predefined configurations for diagnostics.
     */
    fun getDiagnosticInfo(): Map<String, String> = mapOf(
        "default" to AssistantHttpConfig.DEFAULT.toLogString(),
        "preflight" to AssistantHttpConfig.PREFLIGHT.toLogString(),
        "warmup" to AssistantHttpConfig.WARMUP.toLogString(),
        "test" to AssistantHttpConfig.TEST.toLogString(),
    )
}
