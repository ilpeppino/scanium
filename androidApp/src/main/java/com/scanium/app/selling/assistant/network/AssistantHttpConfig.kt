package com.scanium.app.selling.assistant.network

/**
 * Unified timeout configuration for all assistant HTTP clients.
 *
 * **Production Defaults:**
 * - Connect: 15s - reasonable for mobile network variability
 * - Read: 60s - LLM responses can take time, especially with vision
 * - Write: 30s - multipart uploads with images need time
 * - Call: 75s - overall request budget (connect + read + processing overhead)
 *
 * **Preflight Defaults:**
 * - Tighter timeouts (2-3s) to avoid blocking UI during quick health checks
 *
 * @see AssistantOkHttpClientFactory for client creation
 */
data class AssistantHttpConfig(
    /** Timeout for establishing TCP connection (seconds) */
    val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
    /** Timeout for reading response data (seconds) */
    val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
    /** Timeout for writing request data (seconds) */
    val writeTimeoutSeconds: Long = DEFAULT_WRITE_TIMEOUT_SECONDS,
    /** Overall timeout for the entire call (seconds) */
    val callTimeoutSeconds: Long = DEFAULT_CALL_TIMEOUT_SECONDS,
    /** Number of retry attempts for transient errors */
    val retryCount: Int = DEFAULT_RETRY_COUNT,
) {
    companion object {
        // Production defaults for chat/multipart requests
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
        const val DEFAULT_WRITE_TIMEOUT_SECONDS = 30L
        const val DEFAULT_CALL_TIMEOUT_SECONDS = 75L
        const val DEFAULT_RETRY_COUNT = 1

        // Preflight defaults (tight to avoid blocking)
        const val PREFLIGHT_CONNECT_TIMEOUT_SECONDS = 3L
        const val PREFLIGHT_READ_TIMEOUT_SECONDS = 3L
        const val PREFLIGHT_WRITE_TIMEOUT_SECONDS = 3L
        const val PREFLIGHT_CALL_TIMEOUT_SECONDS = 5L
        const val PREFLIGHT_RETRY_COUNT = 0 // No retries for preflight

        // Warmup defaults (moderate - not time-critical)
        const val WARMUP_CONNECT_TIMEOUT_SECONDS = 5L
        const val WARMUP_READ_TIMEOUT_SECONDS = 10L
        const val WARMUP_WRITE_TIMEOUT_SECONDS = 5L
        const val WARMUP_CALL_TIMEOUT_SECONDS = 15L
        const val WARMUP_RETRY_COUNT = 0 // No retries for warmup

        /**
         * Default production configuration for chat and multipart requests.
         */
        val DEFAULT = AssistantHttpConfig()

        /**
         * Tight timeout configuration for preflight health checks.
         * These should be fast and non-blocking.
         */
        val PREFLIGHT = AssistantHttpConfig(
            connectTimeoutSeconds = PREFLIGHT_CONNECT_TIMEOUT_SECONDS,
            readTimeoutSeconds = PREFLIGHT_READ_TIMEOUT_SECONDS,
            writeTimeoutSeconds = PREFLIGHT_WRITE_TIMEOUT_SECONDS,
            callTimeoutSeconds = PREFLIGHT_CALL_TIMEOUT_SECONDS,
            retryCount = PREFLIGHT_RETRY_COUNT,
        )

        /**
         * Moderate timeout configuration for warmup requests.
         * Not time-critical, but shouldn't hang indefinitely.
         */
        val WARMUP = AssistantHttpConfig(
            connectTimeoutSeconds = WARMUP_CONNECT_TIMEOUT_SECONDS,
            readTimeoutSeconds = WARMUP_READ_TIMEOUT_SECONDS,
            writeTimeoutSeconds = WARMUP_WRITE_TIMEOUT_SECONDS,
            callTimeoutSeconds = WARMUP_CALL_TIMEOUT_SECONDS,
            retryCount = WARMUP_RETRY_COUNT,
        )

        /**
         * Configuration optimized for test environments with MockWebServer.
         * Short timeouts to fail fast on test issues.
         */
        val TEST = AssistantHttpConfig(
            connectTimeoutSeconds = 5L,
            readTimeoutSeconds = 5L,
            writeTimeoutSeconds = 5L,
            callTimeoutSeconds = 10L,
            retryCount = 0, // Tests should control retry behavior explicitly
        )
    }

    /**
     * Returns a human-readable summary for logging.
     */
    fun toLogString(): String = buildString {
        append("AssistantHttpConfig(")
        append("connect=${connectTimeoutSeconds}s, ")
        append("read=${readTimeoutSeconds}s, ")
        append("write=${writeTimeoutSeconds}s, ")
        append("call=${callTimeoutSeconds}s, ")
        append("retries=$retryCount)")
    }
}
