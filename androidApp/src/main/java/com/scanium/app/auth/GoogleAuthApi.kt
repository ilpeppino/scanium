package com.scanium.app.auth

import com.scanium.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
)

@Serializable
data class GoogleAuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val user: UserResponse,
    val refreshToken: String? = null, // Phase C
    val refreshTokenExpiresIn: Int? = null, // Phase C
    val correlationId: String? = null,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String?,
    val displayName: String?,
    val pictureUrl: String?,
)

// Phase C: Refresh token request/response
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshToken: String? = null, // Optional: new refresh token if rotated
    val refreshTokenExpiresIn: Int? = null,
    val correlationId: String? = null,
)

class GoogleAuthApi(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun exchangeToken(idToken: String): Result<GoogleAuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    json
                        .encodeToString(
                            GoogleAuthRequest.serializer(),
                            GoogleAuthRequest(idToken),
                        ).toRequestBody(mediaType)

                val request =
                    Request
                        .Builder()
                        .url("${BuildConfig.SCANIUM_API_BASE_URL}/v1/auth/google")
                        .post(requestBody)
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Auth failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val authResponse =
                    json.decodeFromString(
                        GoogleAuthResponse.serializer(),
                        responseBody,
                    )

                Result.success(authResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Phase C: Refresh access token using refresh token
     */
    suspend fun refreshSession(refreshToken: String): Result<RefreshTokenResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    json
                        .encodeToString(
                            RefreshTokenRequest.serializer(),
                            RefreshTokenRequest(refreshToken),
                        ).toRequestBody(mediaType)

                val request =
                    Request
                        .Builder()
                        .url("${BuildConfig.SCANIUM_API_BASE_URL}/v1/auth/refresh")
                        .post(requestBody)
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Refresh failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val refreshResponse =
                    json.decodeFromString(
                        RefreshTokenResponse.serializer(),
                        responseBody,
                    )

                Result.success(refreshResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Phase C: Logout (revoke session on backend)
     */
    suspend fun logout(accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url("${BuildConfig.SCANIUM_API_BASE_URL}/v1/auth/logout")
                        .post("".toRequestBody(mediaType))
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Logout failed: ${response.code}"),
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Phase D: Delete account (permanently delete user account and all data)
     */
    suspend fun deleteAccount(accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url("${BuildConfig.SCANIUM_API_BASE_URL}/v1/account/delete")
                        .post("".toRequestBody(mediaType))
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Account deletion failed: ${response.code}"),
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
