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

    companion object {
        private const val TAG = "ScaniumAuth"
        private const val STORE_NAME = "secure_config"
        private const val KEY_API_KEY = "scanium_api_key"
    }
}
