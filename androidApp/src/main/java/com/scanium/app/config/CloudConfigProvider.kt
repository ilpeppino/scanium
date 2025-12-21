package com.scanium.app.config

import com.scanium.app.BuildConfig
import com.scanium.shared.core.models.classification.DEFAULT_DOMAIN_PACK_ID
import com.scanium.shared.core.models.config.CloudClassifierConfig
import com.scanium.shared.core.models.config.CloudConfigProvider

/**
 * Android implementation of CloudConfigProvider that reads values from BuildConfig.
 * Secrets stay out of source control; BuildConfig values are populated from
 * local.properties or environment variables at build time.
 */
class AndroidCloudConfigProvider(
    private val domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
) : CloudConfigProvider {

    override fun current(): CloudClassifierConfig {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.orEmpty()
        val apiKey = BuildConfig.SCANIUM_API_KEY.takeIf { it.isNotBlank() }

        return CloudClassifierConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            domainPackId = domainPackId,
        )
    }
}
