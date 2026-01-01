package com.scanium.app.config

import android.content.Context
import com.scanium.app.BuildConfig
import com.scanium.shared.core.models.classification.DEFAULT_DOMAIN_PACK_ID
import com.scanium.shared.core.models.config.CloudClassifierConfig
import com.scanium.shared.core.models.config.CloudConfigProvider

/**
 * Android implementation of CloudConfigProvider that reads base URL from BuildConfig
 * and API keys from encrypted storage backed by the Android Keystore.
 */
class AndroidCloudConfigProvider(
    private val context: Context,
    private val domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
) : CloudConfigProvider {
    override fun current(): CloudClassifierConfig {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.orEmpty()
        val apiKey = SecureApiKeyStore(context).getApiKey()

        return CloudClassifierConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            domainPackId = domainPackId,
        )
    }
}
