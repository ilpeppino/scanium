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
 * ViewModel for managing Items List screen FTUE (First-Time User Experience).
 *
 * Manages a four-step sequence of gesture hints:
 * 1. Tap to edit: "Tap an item to edit details before selling"
 * 2. Swipe right to delete: "Swipe right to remove items" + nudge animation
 * 3. Long-press to select: "Long-press to select multiple items"
 * 4. Share/export goal: "Select items, then share or export to sell"
 *
 * Triggers:
 * - Shows only on first open with at least 1 item
 * - Non-blocking, progressive (one step at a time)
 * - Steps advance automatically or on user interaction
 * - Re-runnable via "Replay first-time guide"
 *
 * State machine:
 * - IDLE: Not started or completed
 * - WAITING_TAP_HINT: Waiting to show tap hint
 * - TAP_HINT_SHOWN: Tap hint displayed
 * - WAITING_SWIPE_HINT: Waiting to show swipe hint
 * - SWIPE_HINT_SHOWN: Swipe hint displayed with nudge animation
 * - WAITING_LONG_PRESS_HINT: Waiting to show long-press hint
 * - LONG_PRESS_HINT_SHOWN: Long-press hint displayed
 * - WAITING_SHARE_GOAL: Waiting to show share/export goal
 * - SHARE_GOAL_SHOWN: Share/export goal hint displayed
 * - COMPLETED: All hints shown or user completed major action
 */
class ItemsListFtueViewModel(
    private val ftueRepository: FtueRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "ItemsListFtueViewModel"

        // Timing constants (in milliseconds)
        private const val INITIAL_DELAY_MS = 500L // Delay before showing first hint
        private const val STEP_DISPLAY_DURATION_MS = 2500L // How long to show each hint
        private const val NUDGE_ANIMATION_DURATION_MS = 800L // Swipe nudge animation
        private const val STEP_TRANSITION_DELAY_MS = 300L // Delay between steps
    }

    enum class ItemsListFtueStep {
        IDLE,
        WAITING_TAP_HINT,
        TAP_HINT_SHOWN,
        WAITING_SWIPE_HINT,
        SWIPE_HINT_SHOWN,
        WAITING_LONG_PRESS_HINT,
        LONG_PRESS_HINT_SHOWN,
        WAITING_SHARE_GOAL,
        SHARE_GOAL_SHOWN,
        COMPLETED,
    }

    // State management
    private val _currentStep = MutableStateFlow(ItemsListFtueStep.IDLE)
    val currentStep: StateFlow<ItemsListFtueStep> = _currentStep.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _showTapEditHint = MutableStateFlow(false)
    val showTapEditHint: StateFlow<Boolean> = _showTapEditHint.asStateFlow()

    private val _showSwipeDeleteHint = MutableStateFlow(false)
    val showSwipeDeleteHint: StateFlow<Boolean> = _showSwipeDeleteHint.asStateFlow()

    private val _showLongPressHint = MutableStateFlow(false)
    val showLongPressHint: StateFlow<Boolean> = _showLongPressHint.asStateFlow()

    private val _showShareGoalHint = MutableStateFlow(false)
    val showShareGoalHint: StateFlow<Boolean> = _showShareGoalHint.asStateFlow()

    // Nudge animation state for swipe hint
    private val _swipeNudgeProgress = MutableStateFlow(0f)
    val swipeNudgeProgress: StateFlow<Float> = _swipeNudgeProgress.asStateFlow()

    private var stepTimeoutJob: Job? = null

    /**
     * Initialize the items list FTUE sequence.
     * Should be called when items list screen is first shown with items.
     * Checks persistence flags to determine if FTUE should run.
     *
     * @param shouldStartFtue: True if FTUE should start (check based on completion status)
     * @param itemCount: Number of items in list (skip if 0)
     */
    fun initialize(
        shouldStartFtue: Boolean,
        itemCount: Int = 0,
    ) {
        viewModelScope.launch {
            if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                Log.d(TAG, "initialize: shouldStartFtue=$shouldStartFtue, itemCount=$itemCount")
            }

            if (!shouldStartFtue || itemCount == 0) {
                // Don't set completion flag - just mark as not active
                _currentStep.value = ItemsListFtueStep.COMPLETED
                _isActive.value = false
                // NO ftueRepository.setListFtueCompleted() call here!
                if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                    Log.d(TAG, "FTUE not starting (preconditions not met)")
                }
                return@launch
            }

            _isActive.value = true
            _currentStep.value = ItemsListFtueStep.WAITING_TAP_HINT

            if (com.scanium.app.config.FeatureFlags.isDevBuild) {
                Log.d(TAG, "FTUE starting: step=WAITING_TAP_HINT")
            }

            // Delay before showing first hint
            delay(INITIAL_DELAY_MS)

            if (_currentStep.value == ItemsListFtueStep.WAITING_TAP_HINT) {
                showTapEditHint()
            }
        }
    }

    /**
     * Called when user taps on an item row.
     * Advances to next step (swipe hint).
     */
    fun onItemTapped() {
        viewModelScope.launch {
            if (_currentStep.value == ItemsListFtueStep.TAP_HINT_SHOWN) {
                advanceToNextStep()
            }
        }
    }

    /**
     * Called when user performs a swipe gesture.
     * Advances to next step (long-press hint).
     */
    fun onItemSwiped() {
        viewModelScope.launch {
            if (_currentStep.value == ItemsListFtueStep.SWIPE_HINT_SHOWN) {
                advanceToNextStep()
            }
        }
    }

    /**
     * Called when user performs a long-press gesture.
     * Advances to next step (share goal hint).
     */
    fun onItemLongPressed() {
        viewModelScope.launch {
            if (_currentStep.value == ItemsListFtueStep.LONG_PRESS_HINT_SHOWN) {
                advanceToNextStep()
            }
        }
    }

    /**
     * Called when user opens selection/share menu.
     * Completes the FTUE sequence.
     */
    fun onShareMenuOpened() {
        viewModelScope.launch {
            if (_currentStep.value in
                setOf(
                    ItemsListFtueStep.SHARE_GOAL_SHOWN,
                    ItemsListFtueStep.WAITING_SHARE_GOAL,
                )
            ) {
                completeSequence()
            }
        }
    }

    /**
     * Dismiss the FTUE overlay (user taps outside or closes).
     */
    fun dismiss() {
        viewModelScope.launch {
            cancelTimeouts()
            _showTapEditHint.value = false
            _showSwipeDeleteHint.value = false
            _showLongPressHint.value = false
            _showShareGoalHint.value = false
            _isActive.value = false
            _currentStep.value = ItemsListFtueStep.COMPLETED
            // Mark as seen even if dismissed early
            ftueRepository.setListFtueCompleted(true)
        }
    }

    /**
     * Reset FTUE to initial state (for Replay functionality).
     */
    fun resetForReplay() {
        viewModelScope.launch {
            cancelTimeouts()
            _showTapEditHint.value = false
            _showSwipeDeleteHint.value = false
            _showLongPressHint.value = false
            _showShareGoalHint.value = false
            _isActive.value = false
            _currentStep.value = ItemsListFtueStep.IDLE

            // Reset persistence flags
            ftueRepository.setListTapEditHintSeen(false)
            ftueRepository.setListSwipeDeleteHintSeen(false)
            ftueRepository.setListLongPressHintSeen(false)
            ftueRepository.setListShareGoalHintSeen(false)
            ftueRepository.setListFtueCompleted(false)

            // Re-initialize
            initialize(true, 1)
        }
    }

    private suspend fun showTapEditHint() {
        _currentStep.value = ItemsListFtueStep.TAP_HINT_SHOWN
        _showTapEditHint.value = true
        ftueRepository.setListTapEditHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: TAP_HINT_SHOWN, overlayVisible=true")
        }

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showTapEditHint.value = false

        _currentStep.value = ItemsListFtueStep.WAITING_SWIPE_HINT
        delay(STEP_TRANSITION_DELAY_MS)

        if (_currentStep.value == ItemsListFtueStep.WAITING_SWIPE_HINT) {
            showSwipeDeleteHint()
        }
    }

    private suspend fun showSwipeDeleteHint() {
        _currentStep.value = ItemsListFtueStep.SWIPE_HINT_SHOWN
        _showSwipeDeleteHint.value = true
        ftueRepository.setListSwipeDeleteHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: SWIPE_HINT_SHOWN, overlayVisible=true")
        }

        // Run nudge animation (offset right, then snap back)
        runSwipeNudgeAnimation()

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showSwipeDeleteHint.value = false
        _swipeNudgeProgress.value = 0f

        _currentStep.value = ItemsListFtueStep.WAITING_LONG_PRESS_HINT
        delay(STEP_TRANSITION_DELAY_MS)

        if (_currentStep.value == ItemsListFtueStep.WAITING_LONG_PRESS_HINT) {
            showLongPressHint()
        }
    }

    private suspend fun showLongPressHint() {
        _currentStep.value = ItemsListFtueStep.LONG_PRESS_HINT_SHOWN
        _showLongPressHint.value = true
        ftueRepository.setListLongPressHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: LONG_PRESS_HINT_SHOWN, overlayVisible=true")
        }

        // Show hint for duration, then auto-advance
        delay(STEP_DISPLAY_DURATION_MS)
        _showLongPressHint.value = false

        _currentStep.value = ItemsListFtueStep.WAITING_SHARE_GOAL
        delay(STEP_TRANSITION_DELAY_MS)

        if (_currentStep.value == ItemsListFtueStep.WAITING_SHARE_GOAL) {
            showShareGoalHint()
        }
    }

    private suspend fun showShareGoalHint() {
        _currentStep.value = ItemsListFtueStep.SHARE_GOAL_SHOWN
        _showShareGoalHint.value = true
        ftueRepository.setListShareGoalHintSeen(true)

        if (com.scanium.app.config.FeatureFlags.isDevBuild) {
            Log.d(TAG, "Step transition: SHARE_GOAL_SHOWN, overlayVisible=true")
        }

        // Show hint for duration, then auto-complete
        delay(STEP_DISPLAY_DURATION_MS)
        _showShareGoalHint.value = false

        completeSequence()
    }

    private suspend fun runSwipeNudgeAnimation() {
        // Offset row to the right (0 → 0.25 → 0), don't actually delete
        val steps = 20
        val duration = NUDGE_ANIMATION_DURATION_MS
        val delayPerStep = duration / steps

        // Move right
        for (i in 0..steps) {
            _swipeNudgeProgress.value = (i.toFloat() / steps) * 0.25f
            delay(delayPerStep)
        }

        // Snap back
        _swipeNudgeProgress.value = 0f
    }

    private suspend fun advanceToNextStep() {
        cancelTimeouts()
        when (_currentStep.value) {
            ItemsListFtueStep.TAP_HINT_SHOWN -> {
                _showTapEditHint.value = false
                _currentStep.value = ItemsListFtueStep.WAITING_SWIPE_HINT
                delay(STEP_TRANSITION_DELAY_MS)
                showSwipeDeleteHint()
            }

            ItemsListFtueStep.SWIPE_HINT_SHOWN -> {
                _showSwipeDeleteHint.value = false
                _currentStep.value = ItemsListFtueStep.WAITING_LONG_PRESS_HINT
                delay(STEP_TRANSITION_DELAY_MS)
                showLongPressHint()
            }

            ItemsListFtueStep.LONG_PRESS_HINT_SHOWN -> {
                _showLongPressHint.value = false
                _currentStep.value = ItemsListFtueStep.WAITING_SHARE_GOAL
                delay(STEP_TRANSITION_DELAY_MS)
                showShareGoalHint()
            }

            else -> {
                // No-op for other states
            }
        }
    }

    private suspend fun completeSequence() {
        cancelTimeouts()
        _showTapEditHint.value = false
        _showSwipeDeleteHint.value = false
        _showLongPressHint.value = false
        _showShareGoalHint.value = false
        _currentStep.value = ItemsListFtueStep.COMPLETED
        _isActive.value = false
        ftueRepository.setListFtueCompleted(true)
        Log.d(TAG, "Items List FTUE sequence completed")
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
