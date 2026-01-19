package com.scanium.app.ftue

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
 * ViewModel for managing Settings screen FTUE (First-Time User Experience).
 *
 * Manages a two-step minimal sequence:
 * 1. Language setting: "Set your language once — AI and voice follow it"
 * 2. Replay guide: "You can replay this guide anytime"
 *
 * Triggers:
 * - Shows only on first visit to Settings screen
 * - Minimal guidance for discoverability
 * - Non-blocking
 * - Re-runnable via "Replay first-time guide"
 *
 * State machine:
 * - IDLE: Not started or completed
 * - WAITING_LANGUAGE_HINT: Waiting to show language hint
 * - LANGUAGE_HINT_SHOWN: Language hint displayed
 * - WAITING_REPLAY_HINT: Waiting to show replay hint
 * - REPLAY_HINT_SHOWN: Replay hint displayed
 * - COMPLETED: Both hints shown
 */
class SettingsFtueViewModel(
    private val ftueRepository: FtueRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "SettingsFtueViewModel"

        // Timing constants (in milliseconds)
        private const val INITIAL_DELAY_MS = 500L
        private const val STEP_DISPLAY_DURATION_MS = 2000L // Shorter for minimal settings
        private const val STEP_TRANSITION_DELAY_MS = 300L
    }

    enum class SettingsFtueStep {
        IDLE,
        WAITING_LANGUAGE_HINT,
        LANGUAGE_HINT_SHOWN,
        WAITING_REPLAY_HINT,
        REPLAY_HINT_SHOWN,
        COMPLETED,
    }

    // State management
    private val _currentStep = MutableStateFlow(SettingsFtueStep.IDLE)
    val currentStep: StateFlow<SettingsFtueStep> = _currentStep.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _showLanguageHint = MutableStateFlow(false)
    val showLanguageHint: StateFlow<Boolean> = _showLanguageHint.asStateFlow()

    private val _showReplayHint = MutableStateFlow(false)
    val showReplayHint: StateFlow<Boolean> = _showReplayHint.asStateFlow()

    private var stepTimeoutJob: Job? = null

    /**
     * Initialize the settings FTUE sequence.
     * Should be called when settings screen is first shown.
     *
     * @param shouldStartFtue: True if FTUE should start
     */
    fun initialize(shouldStartFtue: Boolean) {
        viewModelScope.launch {
            if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                Log.d(TAG, "initialize: shouldStartFtue=$shouldStartFtue")
            }

            if (!shouldStartFtue) {
                // Don't set completion flag - just mark as not active
                _currentStep.value = SettingsFtueStep.COMPLETED
                _isActive.value = false
                // NO ftueRepository.setSettingsFtueCompleted() call here!
                if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                    Log.d(TAG, "FTUE not starting (preconditions not met)")
                }
                return@launch
            }

            _isActive.value = true
            _currentStep.value = SettingsFtueStep.WAITING_LANGUAGE_HINT

            if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                Log.d(TAG, "FTUE starting: step=WAITING_LANGUAGE_HINT")
            }

            // Delay before showing first hint
            delay(INITIAL_DELAY_MS)

            if (_currentStep.value == SettingsFtueStep.WAITING_LANGUAGE_HINT) {
                showLanguageHint()
            }
        }
    }

    /**
     * Called when user changes language setting.
     * Can advance to next step.
     */
    fun onLanguageChanged() {
        viewModelScope.launch {
            if (_currentStep.value == SettingsFtueStep.LANGUAGE_HINT_SHOWN) {
                advanceToNextStep()
            }
        }
    }

    /**
     * Dismiss the FTUE overlay.
     */
    fun dismiss() {
        viewModelScope.launch {
            cancelTimeouts()
            _showLanguageHint.value = false
            _showReplayHint.value = false
            _isActive.value = false
            _currentStep.value = SettingsFtueStep.COMPLETED
            ftueRepository.setSettingsFtueCompleted(true)
        }
    }

    /**
     * Reset FTUE to initial state (for Replay functionality).
     */
    fun resetForReplay() {
        viewModelScope.launch {
            cancelTimeouts()
            _showLanguageHint.value = false
            _showReplayHint.value = false
            _isActive.value = false
            _currentStep.value = SettingsFtueStep.IDLE

            // Reset persistence flags
            ftueRepository.setSettingsLanguageHintSeen(false)
            ftueRepository.setSettingsReplayHintSeen(false)
            ftueRepository.setSettingsFtueCompleted(false)

            // Re-initialize
            initialize(true)
        }
    }

    private suspend fun showLanguageHint() {
        _currentStep.value = SettingsFtueStep.LANGUAGE_HINT_SHOWN
        _showLanguageHint.value = true
        ftueRepository.setSettingsLanguageHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: LANGUAGE_HINT_SHOWN, overlayVisible=true")
        }

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showLanguageHint.value = false

        _currentStep.value = SettingsFtueStep.WAITING_REPLAY_HINT
        delay(STEP_TRANSITION_DELAY_MS)

        if (_currentStep.value == SettingsFtueStep.WAITING_REPLAY_HINT) {
            showReplayHint()
        }
    }

    private suspend fun showReplayHint() {
        _currentStep.value = SettingsFtueStep.REPLAY_HINT_SHOWN
        _showReplayHint.value = true
        ftueRepository.setSettingsReplayHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: REPLAY_HINT_SHOWN, overlayVisible=true")
        }

        // Show hint for duration, then auto-complete
        delay(STEP_DISPLAY_DURATION_MS)
        _showReplayHint.value = false

        completeSequence()
    }

    private suspend fun advanceToNextStep() {
        cancelTimeouts()
        when (_currentStep.value) {
            SettingsFtueStep.LANGUAGE_HINT_SHOWN -> {
                _showLanguageHint.value = false
                _currentStep.value = SettingsFtueStep.WAITING_REPLAY_HINT
                delay(STEP_TRANSITION_DELAY_MS)
                showReplayHint()
            }

            else -> {
                // No-op for other states
            }
        }
    }

    private suspend fun completeSequence() {
        cancelTimeouts()
        _currentStep.value = SettingsFtueStep.COMPLETED
        _isActive.value = false
        ftueRepository.setSettingsFtueCompleted(true)
        Log.d(TAG, "Settings FTUE sequence completed")
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
