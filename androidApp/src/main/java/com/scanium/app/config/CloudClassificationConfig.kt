package com.scanium.app.config

import com.scanium.shared.core.models.config.CloudClassifierConfig

/**
 * Convenience accessor for BuildConfig-backed cloud classifier config.
 * Keeps naming aligned with architecture docs while delegating to the
 * AndroidCloudConfigProvider.
 */
object CloudClassificationConfig {
    private val provider = AndroidCloudConfigProvider()

    fun current(): CloudClassifierConfig = provider.current()
}
