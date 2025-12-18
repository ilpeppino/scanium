package com.scanium.shared.core.models.config

import com.scanium.shared.core.models.classification.DEFAULT_DOMAIN_PACK_ID

data class CloudClassifierConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
) {
    val isConfigured: Boolean = baseUrl.isNotBlank()
}

/**
 * Provides cloud classifier configuration per platform.
 */
interface CloudConfigProvider {
    fun current(): CloudClassifierConfig
}
