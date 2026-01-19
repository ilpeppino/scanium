package com.scanium.app.config

import android.content.Context
import com.scanium.app.BuildConfig
import com.scanium.shared.core.models.classification.DEFAULT_DOMAIN_PACK_ID
import com.scanium.shared.core.models.config.CloudClassifierConfig
import com.scanium.shared.core.models.config.CloudConfigProvider

/**
 * Android implementation of CloudConfigProvider that reads base URL from BuildConfig
 * and API keys from encrypted storage backed by the Android Keystore.
 *
 * ***REMOVED******REMOVED*** URL Resolution Order (debug builds only):
 * 1. DevConfigOverride (if explicitly set and not stale)
 * 2. BuildConfig.SCANIUM_API_BASE_URL
 *
 * ***REMOVED******REMOVED*** URL Resolution (release builds):
 * - BuildConfig.SCANIUM_API_BASE_URL only (overrides are ignored)
 */
class AndroidCloudConfigProvider(
    private val context: Context,
    private val domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
) : CloudConfigProvider {
    private val devConfigOverride by lazy { DevConfigOverride(context) }

    override fun current(): CloudClassifierConfig {
        // In debug builds, check for explicit developer override
        val baseUrl =
            if (BuildConfig.DEBUG) {
                devConfigOverride.getEffectiveBaseUrl()
            } else {
                BuildConfig.SCANIUM_API_BASE_URL.orEmpty()
            }

        val apiKey = SecureApiKeyStore(context).getApiKey()

        return CloudClassifierConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            domainPackId = domainPackId,
        )
    }

    /**
     * Get the dev config override for UI access (debug builds only).
     */
    fun devOverride(): DevConfigOverride? = if (BuildConfig.DEBUG) devConfigOverride else null
}
