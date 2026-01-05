package com.scanium.app.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    // Capture resolution setting - clamped at initialization for beta/prod
    private val _captureResolution = MutableStateFlow(clampResolution(CaptureResolution.DEFAULT))
    val captureResolution: StateFlow<CaptureResolution> = _captureResolution.asStateFlow()

    private val _stopScanningRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopScanningRequests: SharedFlow<Unit> = _stopScanningRequests.asSharedFlow()

    /**
     * Updates the capture resolution setting.
     * In beta/prod builds, HIGH is automatically clamped to NORMAL.
     * This will trigger camera rebinding to apply the new resolution.
     */
    fun updateCaptureResolution(resolution: CaptureResolution) {
        val clampedResolution = clampResolution(resolution)
        if (_captureResolution.value != clampedResolution) {
            _captureResolution.value = clampedResolution
        }
    }

    companion object {
        /**
         * Clamps resolution based on FeatureFlags.
         * In beta/prod builds, HIGH is clamped to NORMAL.
         */
        private fun clampResolution(resolution: CaptureResolution): CaptureResolution {
            return if (resolution == CaptureResolution.HIGH && !FeatureFlags.allowHighResolution) {
                CaptureResolution.NORMAL
            } else {
                resolution
            }
        }
    }

    fun onPermissionStateChanged(
        isGranted: Boolean,
        isScanning: Boolean,
    ) {
        if (!isGranted && isScanning) {
            viewModelScope.launch {
                _stopScanningRequests.emit(Unit)
            }
        }
    }
}
