package com.scanium.app.camera

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for camera settings and state.
 *
 * Manages:
 * - Image capture resolution (Low/Normal/High)
 * - Camera configuration state
 *
 * Shared between CameraScreen and CameraXManager.
 */
class CameraViewModel : ViewModel() {

    // Capture resolution setting
    private val _captureResolution = MutableStateFlow(CaptureResolution.DEFAULT)
    val captureResolution: StateFlow<CaptureResolution> = _captureResolution.asStateFlow()

    /**
     * Updates the capture resolution setting.
     * This will trigger camera rebinding to apply the new resolution.
     */
    fun updateCaptureResolution(resolution: CaptureResolution) {
        if (_captureResolution.value != resolution) {
            _captureResolution.value = resolution
        }
    }
}
