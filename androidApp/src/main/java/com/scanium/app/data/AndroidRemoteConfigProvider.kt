package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.RemoteConfig
import com.scanium.app.network.security.RequestSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "remote_config")

class AndroidRemoteConfigProvider(
    private val context: Context,
    private val scope: CoroutineScope,
) : ConfigProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val apiKeyStore = SecureApiKeyStore(context)

    private val CONFIG_JSON_KEY = stringPreferencesKey("config_json")
    private val DEVICE_HASH_KEY = stringPreferencesKey("device_hash_seed")

    // In-memory cache
    private val _configState = MutableStateFlow(RemoteConfig(version = "0.0.0"))
    override val config: Flow<RemoteConfig> = _configState

    init {
        scope.launch {
            // 1. Observe DataStore updates (e.g. from refresh)
            context.configDataStore.data.collect { prefs ->
                val jsonStr = prefs[CONFIG_JSON_KEY]
                if (jsonStr != null) {
                    try {
                        val loaded = json.decodeFromString<RemoteConfig>(jsonStr)
                        if (loaded != _configState.value) {
                            _configState.value = loaded
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        scope.launch {
            // 2. Initial check for refresh
            try {
                val prefs = context.configDataStore.data.first()
                val jsonStr = prefs[CONFIG_JSON_KEY]
                var loadedConfig = RemoteConfig(version = "0.0.0")
                if (jsonStr != null) {
                    try {
                        loadedConfig = json.decodeFromString<RemoteConfig>(jsonStr)
                    } catch (e: Exception) {
                    }
                }

                if (shouldRefresh(loadedConfig)) {
                    refresh(force = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun shouldRefresh(current: RemoteConfig): Boolean {
        val now = System.currentTimeMillis()
        // fetchedAt is in ms? No, Backend service used Date.now() which is ms.
        // Model default was 0.
        // TTL is in seconds.
        val ageSeconds = (now - current.fetchedAt) / 1000
        return ageSeconds > current.ttlSeconds || current.version == "0.0.0"
    }

    override suspend fun refresh(force: Boolean) {
        if (!force && !shouldRefresh(_configState.value)) return

        try {
            val deviceHash = getOrGenerateDeviceHash()
            val urlPath = "/v1/config"
            val url = BuildConfig.SCANIUM_API_BASE_URL + urlPath
            val apiKey = apiKeyStore.getApiKey().orEmpty()

            val requestBuilder =
                Request.Builder()
                    .url(url)
                    .addHeader("X-Scanium-Device-Hash", deviceHash)
                    .addHeader("X-Scanium-Platform", "android")
                    .addHeader("X-Scanium-App-Version", BuildConfig.VERSION_NAME)

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("X-API-Key", apiKey)
            }

            // Add HMAC signature for replay protection (SEC-004)
            if (apiKey.isNotBlank()) {
                RequestSigner.addSignatureHeadersForGet(
                    builder = requestBuilder,
                    apiKey = apiKey,
                    urlPath = urlPath,
                )
            }

            val request = requestBuilder.build()

            val response =
                withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    // Validate JSON before saving
                    val newConfig = json.decodeFromString<RemoteConfig>(body)
                    // Save to cache - this triggers the collector above
                    context.configDataStore.edit { prefs ->
                        prefs[CONFIG_JSON_KEY] = body
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFlag(
        name: String,
        default: Boolean,
    ): Boolean {
        val current = _configState.value
        return when (name) {
            "enableCloud" -> current.featureFlags.enableCloud
            "enableAssistant" -> current.featureFlags.enableAssistant
            "enableProfiles" -> current.featureFlags.enableProfiles
            "enablePostingAssist" -> current.featureFlags.enablePostingAssist
            else -> default
        }
    }

    override fun getLimit(
        name: String,
        default: Int,
    ): Int {
        val current = _configState.value
        return when (name) {
            "cloudDailyCap" -> current.limits.cloudDailyCap
            "assistDailyCap" -> current.limits.assistDailyCap
            "maxPhotosShare" -> current.limits.maxPhotosShare
            else -> default
        }
    }

    override fun getLimit(
        name: String,
        default: Long,
    ): Long {
        val current = _configState.value
        return when (name) {
            "scanCloudCooldownMs" -> current.limits.scanCloudCooldownMs
            else -> default
        }
    }

    override fun getExperimentVariant(id: String): String? {
        return _configState.value.experiments[id]?.variant
    }

    private suspend fun getOrGenerateDeviceHash(): String {
        // We use a seed UUID and hash it.
        var seed: String? = null

        // Optimistic read first
        val prefs = context.configDataStore.data.first()
        seed = prefs[DEVICE_HASH_KEY]

        if (seed == null) {
            seed = UUID.randomUUID().toString()
            context.configDataStore.edit { p ->
                if (p[DEVICE_HASH_KEY] == null) {
                    p[DEVICE_HASH_KEY] = seed!!
                } else {
                    seed = p[DEVICE_HASH_KEY]
                }
            }
        }

        return hash(seed!!)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
