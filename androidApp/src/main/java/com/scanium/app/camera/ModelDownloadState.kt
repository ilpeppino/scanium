package com.scanium.app.camera

/**
 * Represents the state of ML Kit model download/availability.
 *
 * Used to provide UI feedback during first launch when models
 * are being downloaded in the background.
 */
sealed class ModelDownloadState {
    /**
     * Initial state - checking if model is available.
     */
    object Checking : ModelDownloadState()

    /**
     * Model is being downloaded (or initialized).
     */
    object Downloading : ModelDownloadState()

    /**
     * Model download/initialization is complete and ready for use.
     */
    object Ready : ModelDownloadState()

    /**
     * Model download failed with an error.
     *
     * @param message Human-readable error message
     */
    data class Error(
        val message: String,
    ) : ModelDownloadState()
}
