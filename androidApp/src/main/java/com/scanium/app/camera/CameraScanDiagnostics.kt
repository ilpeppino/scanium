package com.scanium.app.camera

import com.scanium.app.camera.detection.LiveScanDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class CameraScanDiagnostics {
    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        _overlayEnabled.value = enabled
    }

    fun enableLiveLogging(enabled: Boolean) {
        LiveScanDiagnostics.enabled = enabled
    }

    fun isLiveLoggingEnabled(): Boolean = LiveScanDiagnostics.enabled

    fun logSharpness(
        frameId: Long,
        sharpnessScore: Float,
        isBlurry: Boolean,
        threshold: Float,
    ) {
        if (LiveScanDiagnostics.enabled) {
            LiveScanDiagnostics.logSharpness(
                frameId = frameId,
                sharpnessScore = sharpnessScore,
                isBlurry = isBlurry,
                threshold = threshold,
            )
        }
    }
}
