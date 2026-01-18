package com.scanium.app.ftue

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Camera UI FTUE (Button Navigation Tutorial).
 *
 * Teaches users the 4 main camera buttons in a deterministic sequence:
 * 1. SHUTTER: "Tap here to capture an item"
 * 2. FLIP_CAMERA: "Switch between front and rear camera"
 * 3. ITEM_LIST: "View and manage scanned items"
 * 4. SETTINGS: "Adjust how Scanium works"
 *
 * Phase 1: State Model
 * - Runs AFTER existing Camera FTUE (detection tutorial) completes
 * - Waits for all button anchors to be registered
 * - Advances only on explicit user action (Next tap or highlighted element tap)
 * - Logs all state transitions for debugging
 *
 * Triggers:
 * - Camera permission granted
 * - Camera preview visible
 * - All 4 anchors registered
 * - Existing Camera FTUE completed
 * - (forceShow OR not completed)
 */
class CameraUiFtueViewModel(
    private val ftueRepository: FtueRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "FTUE_CAMERA_UI"

        // Expected anchor IDs
        const val ANCHOR_SHUTTER = "camera_ui_shutter"
        const val ANCHOR_FLIP = "camera_ui_flip"
        const val ANCHOR_ITEMS = "camera_ui_items"
        const val ANCHOR_SETTINGS = "camera_ui_settings"

        val ALL_ANCHOR_IDS =
            listOf(
                ANCHOR_SHUTTER,
                ANCHOR_FLIP,
                ANCHOR_ITEMS,
                ANCHOR_SETTINGS,
            )
    }

    /**
     * Camera UI FTUE step enum.
     * Each step highlights a specific button with pulsating animation and tooltip.
     */
    enum class CameraUiFtueStep {
        IDLE, // Not started
        SHUTTER, // Step 1: Shutter button
        FLIP_CAMERA, // Step 2: Flip camera button
        ITEM_LIST, // Step 3: Items list button
        SETTINGS, // Step 4: Settings button
        COMPLETED, // All steps shown
    }

    // State management
    private val _currentStep = MutableStateFlow(CameraUiFtueStep.IDLE)
    val currentStep: StateFlow<CameraUiFtueStep> = _currentStep.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Derived state: FTUE should run if (forceShow OR not completed)
    val shouldRun: StateFlow<Boolean> =
        combine(
            ftueRepository.cameraUiFtueForceShowFlow,
            ftueRepository.cameraUiFtueCompletedFlow,
        ) { forceShow, completed ->
            forceShow || !completed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Initialize the Camera UI FTUE sequence.
     * Should be called when all trigger conditions are met.
     *
     * @param cameraPermissionGranted True if camera permission granted
     * @param previewVisible True if camera preview is visible (size > 0)
     * @param allAnchorsRegistered True if all 4 button anchors are registered
     */
    fun initialize(
        cameraPermissionGranted: Boolean,
        previewVisible: Boolean,
        allAnchorsRegistered: Boolean,
    ) {
        viewModelScope.launch {
            val shouldRunValue = shouldRun.value

            Log.d(
                TAG,
                "initialize: permission=$cameraPermissionGranted, " +
                    "preview=$previewVisible, anchors=$allAnchorsRegistered, " +
                    "shouldRun=$shouldRunValue",
            )

            val allConditionsMet =
                cameraPermissionGranted &&
                    previewVisible &&
                    allAnchorsRegistered &&
                    shouldRunValue

            if (!allConditionsMet) {
                Log.d(TAG, "Conditions not met, FTUE will not start")
                return@launch
            }

            // Check if already started
            if (_isActive.value && _currentStep.value != CameraUiFtueStep.IDLE) {
                Log.d(TAG, "FTUE already running, ignoring initialize")
                return@launch
            }

            // Start FTUE from first step (single source of truth)
            startCameraFtue(CameraUiFtueStep.SHUTTER)
        }
    }

    /**
     * SINGLE SOURCE OF TRUTH: Start Camera FTUE from a specific step.
     * Used by both real FTUE initialization and Force Step diagnostic.
     *
     * @param startStep The step to begin from
     */
    private fun startCameraFtue(startStep: CameraUiFtueStep) {
        _isActive.value = true
        _currentStep.value = startStep
        Log.d(TAG, "FTUE starting: stepIndex=${getStepIndex(startStep)}, stepId=${startStep.name}")
    }

    /**
     * Advance to the next step in the sequence.
     * Called when user taps "Next" button or taps the highlighted element.
     */
    fun nextStep() {
        viewModelScope.launch {
            val current = _currentStep.value
            val next =
                when (current) {
                    CameraUiFtueStep.IDLE -> {
                        Log.w(TAG, "nextStep called from IDLE, ignoring")
                        return@launch
                    }
                    CameraUiFtueStep.SHUTTER -> CameraUiFtueStep.FLIP_CAMERA
                    CameraUiFtueStep.FLIP_CAMERA -> CameraUiFtueStep.ITEM_LIST
                    CameraUiFtueStep.ITEM_LIST -> CameraUiFtueStep.SETTINGS
                    CameraUiFtueStep.SETTINGS -> CameraUiFtueStep.COMPLETED
                    CameraUiFtueStep.COMPLETED -> {
                        Log.w(TAG, "nextStep called from COMPLETED, ignoring")
                        return@launch
                    }
                }

            Log.d(TAG, "Advancing: $current -> $next, stepIndex=${getStepIndex(next)}")

            _currentStep.value = next

            // Mark as completed when reaching final step
            if (next == CameraUiFtueStep.COMPLETED) {
                _isActive.value = false
                ftueRepository.setCameraUiFtueCompleted(true)
                Log.d(TAG, "FTUE completed, marked in DataStore")
            }
        }
    }

    /**
     * Get the 0-based step index for logging.
     */
    private fun getStepIndex(step: CameraUiFtueStep): Int =
        when (step) {
            CameraUiFtueStep.IDLE -> -1
            CameraUiFtueStep.SHUTTER -> 0
            CameraUiFtueStep.FLIP_CAMERA -> 1
            CameraUiFtueStep.ITEM_LIST -> 2
            CameraUiFtueStep.SETTINGS -> 3
            CameraUiFtueStep.COMPLETED -> 4
        }

    /**
     * Dismiss the FTUE without marking as completed.
     * Used when user explicitly dismisses (e.g., skip button).
     */
    fun dismiss() {
        viewModelScope.launch {
            Log.d(TAG, "FTUE dismissed by user")
            _currentStep.value = CameraUiFtueStep.COMPLETED
            _isActive.value = false
            // Note: Do NOT mark as completed - user can see it again next time
        }
    }

    /**
     * Reset for replay (developer option or user request).
     * Clears completion flag and resets state to IDLE.
     */
    fun resetForReplay() {
        viewModelScope.launch {
            Log.d(TAG, "Resetting Camera UI FTUE for replay")
            ftueRepository.setCameraUiFtueCompleted(false)
            ftueRepository.setCameraUiFtueForceShow(false)
            _currentStep.value = CameraUiFtueStep.IDLE
            _isActive.value = false
        }
    }

    /**
     * Get the current step index (0-3) for the active step.
     * Returns -1 if not active.
     */
    fun getCurrentStepIndex(): Int = getStepIndex(_currentStep.value)

    /**
     * Get the current step ID as a string for logging.
     */
    fun getCurrentStepId(): String = _currentStep.value.name

    /**
     * DEV-ONLY: Force a specific step for testing.
     * Does NOT mark as completed or persist state.
     * Used by diagnostics panel to isolate and test individual steps.
     *
     * IMPORTANT: Uses the same execution path as real FTUE (startCameraFtue).
     * This ensures Force Step and real FTUE behave identically.
     */
    fun forceStep(step: CameraUiFtueStep) {
        viewModelScope.launch {
            Log.d(TAG, "DEV: Force step to $step")

            // Handle special cases (IDLE, COMPLETED)
            if (step == CameraUiFtueStep.IDLE || step == CameraUiFtueStep.COMPLETED) {
                _currentStep.value = step
                _isActive.value = false
            } else {
                // Use same code path as real FTUE (single source of truth)
                startCameraFtue(step)
            }
        }
    }
}

