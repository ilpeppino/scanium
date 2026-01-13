package com.scanium.app.network

import android.util.Log
import com.scanium.app.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Phase C: Enhanced auth token interceptor with silent session renewal.
 * Automatically refreshes access token when < 7 days from expiry.
 */
class AuthTokenInterceptor(
    private val tokenProvider: () -> String?,
    private val authRepository: AuthRepository,
) : Interceptor {
    // Phase C: Retry guard to prevent refresh loops
    private var lastRefreshAttemptMs: Long = 0
    private var refreshAttemptsInLastHour: Int = 0
    private val maxRefreshAttemptsPerHour = 3
    private val oneHourMs = 60 * 60 * 1000L

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Phase C: Check if we should attempt silent token refresh
        if (shouldAttemptTokenRefresh()) {
            attemptSilentTokenRefresh()
        }

        // Get current token (may have been refreshed)
        val token = tokenProvider()

        val request =
            if (!token.isNullOrBlank()) {
                originalRequest
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }

        return chain.proceed(request)
    }

    /**
     * Phase C: Determine if we should attempt a token refresh.
     * Returns true if token needs refresh and we haven't exceeded retry limits.
     */
    private fun shouldAttemptTokenRefresh(): Boolean {
        // Check if token needs refresh (< 7 days from expiry)
        if (!authRepository.shouldRefreshToken()) {
            return false
        }

        // Check retry guard
        val now = System.currentTimeMillis()
        if (now - lastRefreshAttemptMs > oneHourMs) {
            // Reset counter after 1 hour
            refreshAttemptsInLastHour = 0
        }

        if (refreshAttemptsInLastHour >= maxRefreshAttemptsPerHour) {
            Log.w(TAG, "Max refresh attempts ($maxRefreshAttemptsPerHour/hour) reached, skipping refresh")
            return false
        }

        return true
    }

    /**
     * Phase C: Attempt to refresh the session token silently.
     * Uses runBlocking since OkHttp interceptors are synchronous.
     */
    private fun attemptSilentTokenRefresh() {
        val now = System.currentTimeMillis()
        lastRefreshAttemptMs = now
        refreshAttemptsInLastHour++

        try {
            Log.i(TAG, "Attempting silent token refresh (attempt $refreshAttemptsInLastHour/$maxRefreshAttemptsPerHour)")
            runBlocking {
                val result = authRepository.refreshSession()
                if (result.isSuccess) {
                    Log.i(TAG, "Silent token refresh successful")
                    // Reset retry counter on success
                    refreshAttemptsInLastHour = 0
                } else {
                    Log.w(TAG, "Silent token refresh failed: ${result.exceptionOrNull()?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent token refresh error", e)
        }
    }

    companion object {
        private const val TAG = "AuthTokenInterceptor"
    }
}
