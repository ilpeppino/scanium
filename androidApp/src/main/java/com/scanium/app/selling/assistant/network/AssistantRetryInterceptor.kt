package com.scanium.app.selling.assistant.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * OkHttp interceptor that retries requests on transient errors.
 *
 * **Retry Policy:**
 * - Retries on: timeouts, network IOExceptions, 502/503/504 gateway errors
 * - Does NOT retry on: 400/401/403/404/429 (client errors or rate limits)
 * - Does NOT retry on: SSL/TLS errors (certificate issues shouldn't be retried)
 *
 * **Behavior:**
 * - Waits [retryDelayMs] between attempts
 * - Logs each retry attempt for debugging
 * - Returns the last response/exception after all retries exhausted
 *
 * @param maxRetries Maximum number of retry attempts (0 = no retries)
 * @param retryDelayMs Delay between retry attempts in milliseconds
 */
class AssistantRetryInterceptor(
    private val maxRetries: Int = 1,
    private val retryDelayMs: Long = 500L,
) : Interceptor {
    companion object {
        private const val TAG = "AssistantRetry"

        // HTTP status codes that are transient and worth retrying
        private val RETRYABLE_STATUS_CODES =
            setOf(
                // Bad Gateway
                502,
                // Service Unavailable
                503,
                // Gateway Timeout
                504,
            )

        // HTTP status codes that should NOT be retried
        private val NON_RETRYABLE_STATUS_CODES =
            setOf(
                // Bad Request
                400,
                // Unauthorized
                401,
                // Forbidden
                403,
                // Not Found
                404,
                // Rate Limited (respect rate limits, don't hammer)
                429,
            )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null
        var lastResponse: Response? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                // Close previous response if we're retrying
                lastResponse?.close()

                val response = chain.proceed(request)

                // Check if response indicates a retryable error
                if (response.code in RETRYABLE_STATUS_CODES && attempt < maxRetries) {
                    Log.d(
                        TAG,
                        "Transient error ${response.code} on attempt ${attempt + 1}/${maxRetries + 1}, " +
                            "will retry in ${retryDelayMs}ms",
                    )
                    response.close()
                    attempt++
                    Thread.sleep(retryDelayMs)
                    continue
                }

                // Non-retryable status code or success - return immediately
                if (response.code in NON_RETRYABLE_STATUS_CODES) {
                    Log.d(TAG, "Non-retryable status ${response.code}, not retrying")
                    return response
                }

                // Success or max retries reached
                if (attempt > 0) {
                    Log.d(TAG, "Request succeeded on attempt ${attempt + 1}/${maxRetries + 1}")
                }
                return response
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < maxRetries) {
                    Log.d(
                        TAG,
                        "Timeout on attempt ${attempt + 1}/${maxRetries + 1}: ${e.message}, " +
                            "will retry in ${retryDelayMs}ms",
                    )
                    attempt++
                    Thread.sleep(retryDelayMs)
                } else {
                    Log.w(TAG, "Timeout on final attempt ${attempt + 1}/${maxRetries + 1}", e)
                    throw e
                }
            } catch (e: SSLException) {
                // SSL errors should not be retried - likely certificate or handshake issues
                Log.w(TAG, "SSL error, not retrying", e)
                throw e
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries && isRetryableIOException(e)) {
                    Log.d(
                        TAG,
                        "Network error on attempt ${attempt + 1}/${maxRetries + 1}: ${e.message}, " +
                            "will retry in ${retryDelayMs}ms",
                    )
                    attempt++
                    Thread.sleep(retryDelayMs)
                } else {
                    Log.w(TAG, "Network error on final attempt ${attempt + 1}/${maxRetries + 1}", e)
                    throw e
                }
            }
        }

        // Should not reach here, but if we do, throw the last exception
        lastException?.let { throw it }
        lastResponse?.let { return it }

        // This really shouldn't happen, but just in case
        throw IOException("Retry logic error: no response or exception")
    }

    /**
     * Determines if an IOException is worth retrying.
     * Connection refused, reset, and similar transient network issues are retryable.
     */
    private fun isRetryableIOException(e: IOException): Boolean {
        val message = e.message?.lowercase() ?: return true
        return when {
            // Connection issues - likely transient
            message.contains("connection refused") -> true

            message.contains("connection reset") -> true

            message.contains("broken pipe") -> true

            message.contains("unexpected end of stream") -> true

            message.contains("failed to connect") -> true

            // DNS issues might resolve on retry
            e is java.net.UnknownHostException -> true

            // Default to retryable for unknown IOExceptions
            else -> true
        }
    }
}
