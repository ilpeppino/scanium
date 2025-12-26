package com.scanium.app.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // Capture resolution setting
    private val _captureResolution = MutableStateFlow(CaptureResolution.DEFAULT)
    val captureResolution: StateFlow<CaptureResolution> = _captureResolution.asStateFlow()

    private val _stopScanningRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopScanningRequests: SharedFlow<Unit> = _stopScanningRequests.asSharedFlow()

    /**
     * Updates the capture resolution setting.
     * This will trigger camera rebinding to apply the new resolution.
     */
    fun updateCaptureResolution(resolution: CaptureResolution) {
        if (_captureResolution.value != resolution) {
            _captureResolution.value = resolution
        }
    }

    fun onPermissionStateChanged(isGranted: Boolean, isScanning: Boolean) {
        if (!isGranted && isScanning) {
            viewModelScope.launch {
                _stopScanningRequests.emit(Unit)
            }
        }
    }
}
