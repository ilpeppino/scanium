package com.scanium.app.items.merging

import com.scanium.app.AggregationPresets
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.state.ItemsStateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Coordinates automatic duplicate detection and manages merge suggestion state.
 *
 * Listens to item additions, runs detection asynchronously in the background,
 * and emits merge suggestions via StateFlow for UI consumption.
 *
 * Features:
 * - Debouncing (500ms) to prevent excessive detection on rapid item additions
 * - Respects user settings (only detects when enabled)
 * - Non-blocking (runs on worker dispatcher)
 * - Manages suggestion state transitions (None → Available → Dismissed → None)
 *
 * @property scope CoroutineScope for launching detection jobs
 * @property stateManager ItemsStateManager to access current items
 * @property settingsRepository Settings repository to check if feature is enabled
 * @property workerDispatcher Background dispatcher for CPU-bound detection work
 */
class DuplicateDetectionCoordinator(
    private val scope: CoroutineScope,
    private val stateManager: ItemsStateManager,
    private val settingsRepository: SettingsRepository,
    private val workerDispatcher: CoroutineDispatcher,
) {
    private val _mergeSuggestionState = MutableStateFlow<MergeSuggestionState>(MergeSuggestionState.None)

    /**
     * Current merge suggestion state. Observed by UI to show/hide banner and bottom sheet.
     */
    val mergeSuggestionState: StateFlow<MergeSuggestionState> = _mergeSuggestionState.asStateFlow()

    private val detector = DuplicateDetector(AggregationPresets.BALANCED)
    private var detectionJob: Job? = null

    /**
     * Triggers duplicate detection asynchronously.
     *
     * Flow:
     * 1. Cancels any pending detection job (debouncing)
     * 2. Waits 500ms to batch rapid additions
     * 3. Checks if feature is enabled in settings
     * 4. Runs detection on worker dispatcher
     * 5. Emits Available state if groups found
     *
     * Called from ItemsStateManager.onStateChanged callback.
     */
    fun triggerDetection() {
        detectionJob?.cancel()
        detectionJob = scope.launch(workerDispatcher) {
            delay(500) // Debounce rapid additions

            // Check if feature is enabled
            val enabled = settingsRepository.smartMergeSuggestionsEnabledFlow.first()
            if (!enabled) return@launch

            // Need at least 2 items to detect duplicates
            val allItems = stateManager.getScannedItems()
            if (allItems.size < 2) return@launch

            // Run detection (CPU-bound work)
            val groups = detector.findDuplicateGroups(allItems)

            // Emit results if any groups found
            if (groups.isNotEmpty()) {
                _mergeSuggestionState.value = MergeSuggestionState.Available(groups)
            } else {
                // No duplicates found, clear any existing suggestions
                _mergeSuggestionState.value = MergeSuggestionState.None
            }
        }
    }

    /**
     * User dismissed the merge suggestions banner.
     * Transitions state to Dismissed.
     */
    fun dismissSuggestions() {
        _mergeSuggestionState.value = MergeSuggestionState.Dismissed(System.currentTimeMillis())
    }

    /**
     * User accepted (merged) a specific group.
     * Removes that group from the available suggestions.
     * If no groups remain, transitions to None.
     *
     * @param group The merge group that was accepted
     */
    fun acceptGroup(group: MergeGroup) {
        val current = _mergeSuggestionState.value
        if (current is MergeSuggestionState.Available) {
            val remaining = current.groups.filterNot { it == group }
            _mergeSuggestionState.value = if (remaining.isEmpty()) {
                MergeSuggestionState.None
            } else {
                MergeSuggestionState.Available(remaining)
            }
        }
    }

    /**
     * User rejected (did not merge) a specific group.
     * Same behavior as acceptGroup - removes group from suggestions.
     *
     * @param group The merge group that was rejected
     */
    fun rejectGroup(group: MergeGroup) {
        acceptGroup(group) // Same logic: remove from suggestions
    }

    /**
     * User accepted all merge groups at once.
     * Transitions state to None.
     */
    fun acceptAllGroups() {
        _mergeSuggestionState.value = MergeSuggestionState.None
    }
}
