package com.scanium.app.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureApiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        STORE_NAME,
        masterKeyAlias,
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(): String? = sharedPreferences
        .getString(KEY_API_KEY, null)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

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
        private const val STORE_NAME = "secure_config"
        private const val KEY_API_KEY = "scanium_api_key"
    }
}
