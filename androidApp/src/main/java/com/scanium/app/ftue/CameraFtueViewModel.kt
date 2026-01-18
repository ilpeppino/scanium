package com.scanium.app.ftue

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Camera screen FTUE (First-Time User Experience).
 *
 * Manages a multi-step sequence of hints:
 * 1. ROI pulse: "Center an object here to scan it"
 * 2. BBox hint: "Boxes appear around detected objects"
 * 3. Shutter pulse: "Tap to capture your first item"
 *
 * Triggers:
 * - Shows only on first run AFTER camera permission is granted
 * - Skips if user already captured at least one item
 * - Re-runnable via "Replay first-time guide"
 *
 * State machine:
 * - IDLE: Not started or completed
 * - WAITING_ROI: Waiting to show ROI pulse hint
 * - ROI_HINT_SHOWN: ROI hint displayed
 * - WAITING_BBOX: Waiting for first detection or timeout
 * - BBOX_HINT_SHOWN: BBox hint displayed
 * - WAITING_SHUTTER: Waiting to show shutter hint
 * - SHUTTER_HINT_SHOWN: Shutter hint displayed
 * - COMPLETED: All hints shown or user captured first item
 */
class CameraFtueViewModel(private val ftueRepository: FtueRepository) : ViewModel() {
    companion object {
        private const val TAG = "CameraFtueViewModel"

        // Timing constants (in milliseconds)
        private const val ROI_PULSE_DURATION_MS = 1200L
        private const val ROI_HINT_INITIAL_DELAY_MS = 800L  // Delay before showing first hint
        private const val BBOX_DETECTION_TIMEOUT_MS = 3000L  // Wait for first detection
        private const val BBOX_GLOW_DURATION_MS = 800L
        private const val SHUTTER_HINT_DELAY_MS = 500L
    }

    enum class CameraFtueStep {
        IDLE,
        WAITING_ROI,
        ROI_HINT_SHOWN,
        WAITING_BBOX,
        BBOX_HINT_SHOWN,
        WAITING_SHUTTER,
        SHUTTER_HINT_SHOWN,
        COMPLETED,
    }

    // State management
    private val _currentStep = MutableStateFlow(CameraFtueStep.IDLE)
    val currentStep: StateFlow<CameraFtueStep> = _currentStep.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _showRoiHint = MutableStateFlow(false)
    val showRoiHint: StateFlow<Boolean> = _showRoiHint.asStateFlow()

    private val _showBboxHint = MutableStateFlow(false)
    val showBboxHint: StateFlow<Boolean> = _showBboxHint.asStateFlow()

    private val _showShutterHint = MutableStateFlow(false)
    val showShutterHint: StateFlow<Boolean> = _showShutterHint.asStateFlow()

    private var stepTimeoutJob: Job? = null

    /**
     * Initialize the camera FTUE sequence.
     * Should be called when camera screen is first shown.
     * Checks persistence flags to determine if FTUE should run.
     *
     * @param shouldStartFtue: True if FTUE should start (check based on completion status)
     * @param hasExistingItems: True if user already has items (skip FTUE if true)
     */
    fun initialize(shouldStartFtue: Boolean, hasExistingItems: Boolean = false) {
        viewModelScope.launch {
            if (!shouldStartFtue || hasExistingItems) {
                _currentStep.value = CameraFtueStep.COMPLETED
                _isActive.value = false
                ftueRepository.setCameraFtueCompleted(true)
                return@launch
            }

            _isActive.value = true
            _currentStep.value = CameraFtueStep.WAITING_ROI

            // Delay before showing first hint
            delay(ROI_HINT_INITIAL_DELAY_MS)

            if (_currentStep.value == CameraFtueStep.WAITING_ROI) {
                showRoiHint()
            }
        }
    }

    /**
     * Called when first detection/bounding box appears.
     * Triggers BBox hint.
     */
    fun onFirstDetectionAppeared() {
        viewModelScope.launch {
            if (_currentStep.value == CameraFtueStep.WAITING_BBOX) {
                showBboxHint()
            }
        }
    }

    /**
     * Called when user taps the shutter button (before actual capture).
     * Completes the FTUE sequence.
     */
    fun onShutterTapped() {
        viewModelScope.launch {
            if (_currentStep.value in setOf(
                    CameraFtueStep.BBOX_HINT_SHOWN,
                    CameraFtueStep.WAITING_SHUTTER,
                    CameraFtueStep.SHUTTER_HINT_SHOWN
                )
            ) {
                completeSequence()
            }
        }
    }

    /**
     * Called when user successfully captures an item.
     * Completes the FTUE sequence.
     */
    fun onItemCaptured() {
        viewModelScope.launch {
            completeSequence()
        }
    }

    /**
     * Dismiss the FTUE overlay (user taps outside or closes).
     */
    fun dismiss() {
        viewModelScope.launch {
            cancelTimeouts()
            _showRoiHint.value = false
            _showBboxHint.value = false
            _showShutterHint.value = false
            _isActive.value = false
            _currentStep.value = CameraFtueStep.COMPLETED
            // Mark as seen even if dismissed early
            ftueRepository.setCameraFtueCompleted(true)
        }
    }

    /**
     * Reset FTUE to initial state (for Replay functionality).
     */
    fun resetForReplay() {
        viewModelScope.launch {
            cancelTimeouts()
            _showRoiHint.value = false
            _showBboxHint.value = false
            _showShutterHint.value = false
            _isActive.value = false
            _currentStep.value = CameraFtueStep.IDLE

            // Reset persistence flags
            ftueRepository.setCameraRoiHintSeen(false)
            ftueRepository.setCameraBboxHintSeen(false)
            ftueRepository.setCameraShutterHintSeen(false)
            ftueRepository.setCameraFtueCompleted(false)

            // Re-initialize
            initialize(true, false)
        }
    }

    private suspend fun showRoiHint() {
        _currentStep.value = CameraFtueStep.ROI_HINT_SHOWN
        _showRoiHint.value = true
        ftueRepository.setCameraRoiHintSeen(true)

        // ROI hint automatically hides after pulse animation completes
        delay(ROI_PULSE_DURATION_MS)
        _showRoiHint.value = false

        // Move to next step
        _currentStep.value = CameraFtueStep.WAITING_BBOX
        scheduleDetectionTimeout()
    }

    private suspend fun showBboxHint() {
        cancelTimeouts()
        _currentStep.value = CameraFtueStep.BBOX_HINT_SHOWN
        _showBboxHint.value = true
        ftueRepository.setCameraBboxHintSeen(true)

        // BBox hint shows briefly then hides
        delay(BBOX_GLOW_DURATION_MS)
        _showBboxHint.value = false

        // Move to next step
        _currentStep.value = CameraFtueStep.WAITING_SHUTTER
        delay(SHUTTER_HINT_DELAY_MS)

        if (_currentStep.value == CameraFtueStep.WAITING_SHUTTER) {
            showShutterHint()
        }
    }

    private suspend fun showShutterHint() {
        _currentStep.value = CameraFtueStep.SHUTTER_HINT_SHOWN
        _showShutterHint.value = true
        ftueRepository.setCameraShutterHintSeen(true)

        // Keep shutter hint visible until user taps shutter
        // (will be dismissed in onShutterTapped/onItemCaptured)
    }

    private fun scheduleDetectionTimeout() {
        cancelTimeouts()
        stepTimeoutJob = viewModelScope.launch {
            delay(BBOX_DETECTION_TIMEOUT_MS)

            // If no detection appeared, show the BBox hint anyway
            if (_currentStep.value == CameraFtueStep.WAITING_BBOX) {
                Log.d(TAG, "No detection within timeout, showing BBox hint anyway")
                showBboxHint()
            }
        }
    }

    private suspend fun completeSequence() {
        cancelTimeouts()
        _showRoiHint.value = false
        _showBboxHint.value = false
        _showShutterHint.value = false
        _currentStep.value = CameraFtueStep.COMPLETED
        _isActive.value = false
        ftueRepository.setCameraFtueCompleted(true)
        Log.d(TAG, "Camera FTUE sequence completed")
    }

    private fun cancelTimeouts() {
        stepTimeoutJob?.cancel()
        stepTimeoutJob = null
    }

    override fun onCleared() {
        cancelTimeouts()
        super.onCleared()
    }
}
