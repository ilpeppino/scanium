package com.scanium.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.scanium.app.config.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val apiKeyStore: SecureApiKeyStore,
    private val authApi: GoogleAuthApi,
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signInWithGoogle(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Get Google ID token via Credential Manager
                val googleIdOption =
                    GetGoogleIdOption
                        .Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(GOOGLE_SERVER_CLIENT_ID)
                        .build()

                val request =
                    GetCredentialRequest
                        .Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                val result: GetCredentialResponse =
                    credentialManager.getCredential(
                        request = request,
                        context = context,
                    )

                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = credential.idToken

                // Step 2: Exchange with backend
                val authResponse = authApi.exchangeToken(idToken).getOrThrow()

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

                Result.success(Unit)
            } catch (e: GetCredentialException) {
                Result.failure(e)
            } catch (e: Exception) {
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

    companion object {
        // This must match GOOGLE_OAUTH_CLIENT_ID in backend .env
        // TODO: Replace with actual Android OAuth Client ID from Google Cloud Console
        private const val GOOGLE_SERVER_CLIENT_ID = "YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com"
    }
}
