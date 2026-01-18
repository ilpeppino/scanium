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
 * ViewModel for managing Edit Item screen FTUE (First-Time User Experience).
 *
 * Manages a three-step sequence of field improvement hints:
 * 1. Improve details: "Improve details to get better listings"
 * 2. Condition & price: "Set condition and price for better results"
 * 3. AI assistant: "Use AI to generate a ready-to-sell description" (Dev flavor only)
 *
 * Triggers:
 * - Shows only on first visit to Edit Item screen
 * - Step 3 (AI) skipped in beta/prod builds
 * - Non-blocking, progressive
 * - Re-runnable via "Replay first-time guide"
 *
 * State machine:
 * - IDLE: Not started or completed
 * - WAITING_DETAILS_HINT: Waiting to show details hint
 * - DETAILS_HINT_SHOWN: Details hint displayed
 * - WAITING_CONDITION_PRICE_HINT: Waiting to show condition/price hint
 * - CONDITION_PRICE_HINT_SHOWN: Condition/price hint displayed
 * - WAITING_AI_HINT: Waiting to show AI hint (Dev only)
 * - AI_HINT_SHOWN: AI hint displayed
 * - COMPLETED: All hints shown
 */
class EditItemFtueViewModel(private val ftueRepository: FtueRepository) : ViewModel() {
    companion object {
        private const val TAG = "EditItemFtueViewModel"

        // Timing constants (in milliseconds)
        private const val INITIAL_DELAY_MS = 500L
        private const val STEP_DISPLAY_DURATION_MS = 2500L
        private const val STEP_TRANSITION_DELAY_MS = 300L
    }

    enum class EditItemFtueStep {
        IDLE,
        WAITING_DETAILS_HINT,
        DETAILS_HINT_SHOWN,
        WAITING_CONDITION_PRICE_HINT,
        CONDITION_PRICE_HINT_SHOWN,
        WAITING_AI_HINT,
        AI_HINT_SHOWN,
        COMPLETED,
    }

    // State management
    private val _currentStep = MutableStateFlow(EditItemFtueStep.IDLE)
    val currentStep: StateFlow<EditItemFtueStep> = _currentStep.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _showDetailsHint = MutableStateFlow(false)
    val showDetailsHint: StateFlow<Boolean> = _showDetailsHint.asStateFlow()

    private val _showConditionPriceHint = MutableStateFlow(false)
    val showConditionPriceHint: StateFlow<Boolean> = _showConditionPriceHint.asStateFlow()

    private val _showAiHint = MutableStateFlow(false)
    val showAiHint: StateFlow<Boolean> = _showAiHint.asStateFlow()

    private var stepTimeoutJob: Job? = null

    /**
     * Initialize the edit item FTUE sequence.
     * Should be called when edit item screen is first shown.
     *
     * @param shouldStartFtue: True if FTUE should start
     * @param isDevBuild: True if running dev flavor (AI hint included)
     */
    fun initialize(shouldStartFtue: Boolean, isDevBuild: Boolean = false) {
        viewModelScope.launch {
            if (!shouldStartFtue) {
                _currentStep.value = EditItemFtueStep.COMPLETED
                _isActive.value = false
                ftueRepository.setEditFtueCompleted(true)
                return@launch
            }

            _isActive.value = true
            _currentStep.value = EditItemFtueStep.WAITING_DETAILS_HINT

            // Delay before showing first hint
            delay(INITIAL_DELAY_MS)

            if (_currentStep.value == EditItemFtueStep.WAITING_DETAILS_HINT) {
                showDetailsHint()
            }
        }
    }

    /**
     * Called when user edits a field.
     * Can advance to next step.
     */
    fun onFieldEdited() {
        viewModelScope.launch {
            if (_currentStep.value == EditItemFtueStep.DETAILS_HINT_SHOWN) {
                advanceToNextStep()
            }
        }
    }

    /**
     * Called when user sets condition or price.
     * Advances to next step.
     */
    fun onConditionOrPriceSet() {
        viewModelScope.launch {
            if (_currentStep.value == EditItemFtueStep.CONDITION_PRICE_HINT_SHOWN) {
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
            _showDetailsHint.value = false
            _showConditionPriceHint.value = false
            _showAiHint.value = false
            _isActive.value = false
            _currentStep.value = EditItemFtueStep.COMPLETED
            ftueRepository.setEditFtueCompleted(true)
        }
    }

    /**
     * Reset FTUE to initial state (for Replay functionality).
     */
    fun resetForReplay(isDevBuild: Boolean = false) {
        viewModelScope.launch {
            cancelTimeouts()
            _showDetailsHint.value = false
            _showConditionPriceHint.value = false
            _showAiHint.value = false
            _isActive.value = false
            _currentStep.value = EditItemFtueStep.IDLE

            // Reset persistence flags
            ftueRepository.setEditImproveDetailsHintSeen(false)
            ftueRepository.setEditConditionPriceHintSeen(false)
            ftueRepository.setEditUseAiHintSeen(false)
            ftueRepository.setEditFtueCompleted(false)

            // Re-initialize
            initialize(true, isDevBuild)
        }
    }

    private suspend fun showDetailsHint() {
        _currentStep.value = EditItemFtueStep.DETAILS_HINT_SHOWN
        _showDetailsHint.value = true
        ftueRepository.setEditImproveDetailsHintSeen(true)

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showDetailsHint.value = false

        _currentStep.value = EditItemFtueStep.WAITING_CONDITION_PRICE_HINT
        delay(STEP_TRANSITION_DELAY_MS)

        if (_currentStep.value == EditItemFtueStep.WAITING_CONDITION_PRICE_HINT) {
            showConditionPriceHint()
        }
    }

    private suspend fun showConditionPriceHint() {
        _currentStep.value = EditItemFtueStep.CONDITION_PRICE_HINT_SHOWN
        _showConditionPriceHint.value = true
        ftueRepository.setEditConditionPriceHintSeen(true)

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showConditionPriceHint.value = false

        // Note: AI hint shown only in dev builds - would be skipped in beta/prod
        _currentStep.value = EditItemFtueStep.COMPLETED
        _isActive.value = false
        ftueRepository.setEditFtueCompleted(true)
        Log.d(TAG, "Edit Item FTUE sequence completed")
    }

    private suspend fun advanceToNextStep() {
        cancelTimeouts()
        when (_currentStep.value) {
            EditItemFtueStep.DETAILS_HINT_SHOWN -> {
                _showDetailsHint.value = false
                _currentStep.value = EditItemFtueStep.WAITING_CONDITION_PRICE_HINT
                delay(STEP_TRANSITION_DELAY_MS)
                showConditionPriceHint()
            }

            else -> {
                // No-op for other states
            }
        }
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
