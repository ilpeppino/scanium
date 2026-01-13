package com.scanium.app.config

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.scanium.app.BuildConfig

class SecureApiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences =
        EncryptedSharedPreferences.create(
            STORE_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun getApiKey(): String? {
        val storedKey =
            sharedPreferences
                .getString(KEY_API_KEY, null)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        if (storedKey != null) {
            // DIAG: Log stored key info (safe: length + prefix only)
            Log.d(TAG, "getApiKey: found stored key len=${storedKey.length} prefix=${storedKey.take(6)}...")
            return storedKey
        }

        val buildConfigKey =
            BuildConfig.SCANIUM_API_KEY
                .takeIf { it.isNotBlank() }
                ?.trim()

        if (buildConfigKey != null) {
            // DIAG: Log BuildConfig key info
            Log.d(TAG, "getApiKey: using BuildConfig key len=${buildConfigKey.length} prefix=${buildConfigKey.take(6)}...")
            // Seed encrypted storage so subsequent reads don't rely on BuildConfig.
            sharedPreferences.edit()
                .putString(KEY_API_KEY, buildConfigKey)
                .apply()
        } else {
            // DIAG: Warn if no key found
            Log.w(TAG, "getApiKey: NO API KEY FOUND (BuildConfig.SCANIUM_API_KEY is blank)")
        }

        return buildConfigKey
    }

    fun setApiKey(apiKey: String?) {
        sharedPreferences.edit().apply {
            if (apiKey.isNullOrBlank()) {
                remove(KEY_API_KEY)
            } else {
                putString(KEY_API_KEY, apiKey)
            }
        }.apply()
    }

    // Auth token methods
    fun getAuthToken(): String? {
        return sharedPreferences
            .getString(KEY_AUTH_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setAuthToken(token: String?) {
        sharedPreferences.edit().apply {
            if (token.isNullOrBlank()) {
                remove(KEY_AUTH_TOKEN)
            } else {
                putString(KEY_AUTH_TOKEN, token)
            }
        }.apply()
    }

    fun clearAuthToken() {
        setAuthToken(null)
    }

    // Phase C: Refresh token methods
    fun getRefreshToken(): String? {
        return sharedPreferences
            .getString(KEY_REFRESH_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setRefreshToken(token: String?) {
        sharedPreferences.edit().apply {
            if (token.isNullOrBlank()) {
                remove(KEY_REFRESH_TOKEN)
            } else {
                putString(KEY_REFRESH_TOKEN, token)
            }
        }.apply()
    }

    // Phase C: Session expiry methods (stores milliseconds since epoch)
    fun getAccessTokenExpiresAt(): Long? {
        val value = sharedPreferences.getLong(KEY_ACCESS_TOKEN_EXPIRES_AT, -1L)
        return if (value == -1L) null else value
    }

    fun setAccessTokenExpiresAt(timestampMs: Long?) {
        sharedPreferences.edit().apply {
            if (timestampMs == null) {
                remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
            } else {
                putLong(KEY_ACCESS_TOKEN_EXPIRES_AT, timestampMs)
            }
        }.apply()
    }

    fun getRefreshTokenExpiresAt(): Long? {
        val value = sharedPreferences.getLong(KEY_REFRESH_TOKEN_EXPIRES_AT, -1L)
        return if (value == -1L) null else value
    }

    fun setRefreshTokenExpiresAt(timestampMs: Long?) {
        sharedPreferences.edit().apply {
            if (timestampMs == null) {
                remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
            } else {
                putLong(KEY_REFRESH_TOKEN_EXPIRES_AT, timestampMs)
            }
        }.apply()
    }

    // User info methods
    fun getUserInfo(): UserInfo? {
        val id = sharedPreferences.getString(KEY_USER_ID, null) ?: return null
        val email = sharedPreferences.getString(KEY_USER_EMAIL, null)
        val displayName = sharedPreferences.getString(KEY_USER_DISPLAY_NAME, null)
        val pictureUrl = sharedPreferences.getString(KEY_USER_PICTURE_URL, null)

        return UserInfo(id, email, displayName, pictureUrl)
    }

    fun setUserInfo(userInfo: UserInfo?) {
        sharedPreferences.edit().apply {
            if (userInfo == null) {
                remove(KEY_USER_ID)
                remove(KEY_USER_EMAIL)
                remove(KEY_USER_DISPLAY_NAME)
                remove(KEY_USER_PICTURE_URL)
            } else {
                putString(KEY_USER_ID, userInfo.id)
                putString(KEY_USER_EMAIL, userInfo.email)
                putString(KEY_USER_DISPLAY_NAME, userInfo.displayName)
                putString(KEY_USER_PICTURE_URL, userInfo.pictureUrl)
            }
        }.apply()
    }

    data class UserInfo(
        val id: String,
        val email: String?,
        val displayName: String?,
        val pictureUrl: String?,
    )

    companion object {
        private const val TAG = "ScaniumAuth"
        private const val STORE_NAME = "secure_config"
        private const val KEY_API_KEY = "scanium_api_key"
        private const val KEY_AUTH_TOKEN = "scanium_auth_token"
        private const val KEY_REFRESH_TOKEN = "scanium_refresh_token"
        private const val KEY_ACCESS_TOKEN_EXPIRES_AT = "scanium_access_token_expires_at"
        private const val KEY_REFRESH_TOKEN_EXPIRES_AT = "scanium_refresh_token_expires_at"
        private const val KEY_USER_ID = "scanium_user_id"
        private const val KEY_USER_EMAIL = "scanium_user_email"
        private const val KEY_USER_DISPLAY_NAME = "scanium_user_display_name"
        private const val KEY_USER_PICTURE_URL = "scanium_user_picture_url"
    }
}
