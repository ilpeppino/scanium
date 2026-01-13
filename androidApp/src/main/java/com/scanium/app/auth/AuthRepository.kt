package com.scanium.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.scanium.app.config.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val apiKeyStore: SecureApiKeyStore,
    private val authApi: GoogleAuthApi,
    private val authLauncher: AuthLauncher = CredentialManagerAuthLauncher(),
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Initiates Google Sign-In flow via AuthLauncher (Credential Manager).
     * Exchanges Google ID token with backend and stores session token.
     *
     * @param activity The Activity context required for showing the Google Sign-In UI
     * @return Result indicating success or failure with exception details
     */
    suspend fun signInWithGoogle(activity: Activity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Auth flow initiated - requesting Google Sign-In")

                // Step 1: Launch Google Sign-In UI and obtain ID token
                val idToken = authLauncher.startGoogleSignIn(activity).getOrThrow()

                Log.i(TAG, "Auth flow - obtained Google ID token, exchanging with backend")

                // Step 2: Exchange with backend
                val authResponse = authApi.exchangeToken(idToken).getOrThrow()

                Log.i(TAG, "Auth flow - backend exchange successful, storing session data")

                // Step 3: Store session token, refresh token, expiry times, and user info
                apiKeyStore.setAuthToken(authResponse.accessToken)

                // Phase C: Store expiry times (convert seconds to milliseconds)
                val now = System.currentTimeMillis()
                apiKeyStore.setAccessTokenExpiresAt(now + authResponse.expiresIn * 1000L)

                // Phase C: Store refresh token if provided
                authResponse.refreshToken?.let { refreshToken ->
                    apiKeyStore.setRefreshToken(refreshToken)
                    authResponse.refreshTokenExpiresIn?.let { expiresIn ->
                        apiKeyStore.setRefreshTokenExpiresAt(now + expiresIn * 1000L)
                    }
                }

                apiKeyStore.setUserInfo(
                    SecureApiKeyStore.UserInfo(
                        id = authResponse.user.id,
                        email = authResponse.user.email,
                        displayName = authResponse.user.displayName,
                        pictureUrl = authResponse.user.pictureUrl,
                    ),
                )

                Log.i(TAG, "Auth flow completed - user signed in successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Auth flow failed - ${e.javaClass.simpleName}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Phase C: Sign out with backend logout call
     */
    suspend fun signOut(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Call backend logout if we have an access token
                val accessToken = apiKeyStore.getAuthToken()
                if (accessToken != null) {
                    authApi.logout(accessToken)
                        .onFailure { e ->
                            // Log but don't fail - we'll clear local state anyway
                            android.util.Log.w("AuthRepository", "Backend logout failed, clearing local state anyway", e)
                        }
                }

                // Clear all auth-related data
                apiKeyStore.clearAuthToken()
                apiKeyStore.setRefreshToken(null)
                apiKeyStore.setAccessTokenExpiresAt(null)
                apiKeyStore.setRefreshTokenExpiresAt(null)
                apiKeyStore.setUserInfo(null)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun isSignedIn(): Boolean {
        return apiKeyStore.getAuthToken() != null
    }

    fun getUserInfo(): SecureApiKeyStore.UserInfo? {
        return apiKeyStore.getUserInfo()
    }

    /**
     * Phase C: Refresh the access token using the refresh token
     */
    suspend fun refreshSession(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val refreshToken = apiKeyStore.getRefreshToken()
                    ?: return@withContext Result.failure(Exception("No refresh token available"))

                // Call backend refresh endpoint
                val refreshResponse = authApi.refreshSession(refreshToken).getOrThrow()

                // Store new access token and expiry
                val now = System.currentTimeMillis()
                apiKeyStore.setAuthToken(refreshResponse.accessToken)
                apiKeyStore.setAccessTokenExpiresAt(now + refreshResponse.expiresIn * 1000L)

                // If backend rotated the refresh token, store the new one
                refreshResponse.refreshToken?.let { newRefreshToken ->
                    apiKeyStore.setRefreshToken(newRefreshToken)
                    refreshResponse.refreshTokenExpiresIn?.let { expiresIn ->
                        apiKeyStore.setRefreshTokenExpiresAt(now + expiresIn * 1000L)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                // On refresh failure, clear auth state (token expired/invalid)
                apiKeyStore.clearAuthToken()
                apiKeyStore.setRefreshToken(null)
                apiKeyStore.setAccessTokenExpiresAt(null)
                apiKeyStore.setRefreshTokenExpiresAt(null)
                apiKeyStore.setUserInfo(null)
                Result.failure(e)
            }
        }

    /**
     * Phase C: Get access token expiry timestamp (milliseconds since epoch)
     */
    fun getAccessTokenExpiresAt(): Long? {
        return apiKeyStore.getAccessTokenExpiresAt()
    }

    /**
     * Phase C: Check if access token needs refresh (< 7 days from expiry)
     */
    fun shouldRefreshToken(): Boolean {
        val expiresAt = apiKeyStore.getAccessTokenExpiresAt() ?: return false
        val now = System.currentTimeMillis()
        val sevenDaysInMs = 7 * 24 * 60 * 60 * 1000L
        return (expiresAt - now) < sevenDaysInMs
    }

    /**
     * Phase D: Delete account (permanently delete user account and all data)
     * This calls the backend to delete the account, then clears local state.
     */
    suspend fun deleteAccount(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Get access token
                val accessToken = apiKeyStore.getAuthToken()
                    ?: return@withContext Result.failure(Exception("Not signed in"))

                // Call backend delete endpoint
                authApi.deleteAccount(accessToken).getOrThrow()

                // Clear all local auth data
                apiKeyStore.clearAuthToken()
                apiKeyStore.setRefreshToken(null)
                apiKeyStore.setAccessTokenExpiresAt(null)
                apiKeyStore.setRefreshTokenExpiresAt(null)
                apiKeyStore.setUserInfo(null)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
