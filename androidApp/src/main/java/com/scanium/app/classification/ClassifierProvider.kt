package com.scanium.app.classification

import android.content.Context
import com.scanium.app.config.AndroidCloudConfigProvider
import com.scanium.shared.core.models.classification.Classifier
import com.scanium.shared.core.models.config.CloudConfigProvider

/**
 * Simple service locator for classifiers. Today returns NoOp by default
 * to keep builds/tests offline-friendly. Swap in real cloud/on-device
 * implementations when wiring the pipeline.
 */
object ClassifierProvider {
    fun provide(
        context: Context,
        configProvider: CloudConfigProvider = AndroidCloudConfigProvider(context),
    ): Classifier {
        val config = configProvider.current()
        // When cloud config is missing, stay non-blocking with NoOp.
        if (!config.isConfigured) return NoOpClassifier()

        // Placeholder: wire real cloud classifier here when ready.
        return NoOpClassifier()
    }
}
