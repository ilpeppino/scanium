package com.scanium.app.config

import android.content.Context
import com.scanium.shared.core.models.config.CloudClassifierConfig

/**
 * Convenience accessor for BuildConfig-backed cloud classifier config.
 * Keeps naming aligned with architecture docs while delegating to the
 * AndroidCloudConfigProvider.
 */
object CloudClassificationConfig {
    fun current(context: Context): CloudClassifierConfig = AndroidCloudConfigProvider(context).current()
}
