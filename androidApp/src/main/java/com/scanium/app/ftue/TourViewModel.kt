package com.scanium.app.ftue

import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the FTUE guided tour state and orchestration.
 *
 * @param ftueRepository Repository for persisting FTUE state
 * @param itemsViewModel Shared ItemsViewModel for demo item management
 */
class TourViewModel(
    private val ftueRepository: FtueRepository,
    private val itemsViewModel: ItemsViewModel
) : ViewModel() {

    // Current step index (-1 = not started)
    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    // Derived current step
    val currentStep: StateFlow<TourStep?> = currentStepIndex.map { index ->
        if (index in TOUR_STEPS.indices) TOUR_STEPS[index] else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Tour active flag (true when force enabled OR not completed)
    val isTourActive: StateFlow<Boolean> = combine(
        ftueRepository.completedFlow,
        ftueRepository.forceEnabledFlow
    ) { completed, forceEnabled ->
        forceEnabled || !completed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Target bounds registry (keyed by targetKey)
    private val _targetBounds = MutableStateFlow<Map<String, Rect>>(emptyMap())
    val targetBounds: StateFlow<Map<String, Rect>> = _targetBounds.asStateFlow()

    // Demo item ID for items list tour
    private var demoItemId: String? = null

    /**
     * Starts the tour from the beginning (welcome step).
     */
    fun startTour() {
        _currentStepIndex.value = 0
    }

    /**
     * Advances to the next tour step.
     * If at the last step, completes the tour.
     */
    fun nextStep() {
        val nextIndex = _currentStepIndex.value + 1
        if (nextIndex < TOUR_STEPS.size) {
            _currentStepIndex.value = nextIndex

            // Create demo item when reaching items list
            if (nextIndex > 0 && TOUR_STEPS[nextIndex].screen == TourScreen.ITEMS_LIST) {
                createDemoItemIfNeeded()
            }
        } else {
            completeTour()
        }
    }

    /**
     * Goes back to the previous tour step.
     */
    fun previousStep() {
        val prevIndex = _currentStepIndex.value - 1
        if (prevIndex >= 0) {
            _currentStepIndex.value = prevIndex
        }
    }

    /**
     * Skips the tour and marks it as completed.
     */
    fun skipTour() {
        removeDemoItem()
        viewModelScope.launch {
            ftueRepository.setCompleted(true)
            _currentStepIndex.value = -1
        }
    }

    /**
     * Completes the tour and marks it as completed.
     */
    fun completeTour() {
        removeDemoItem()
        viewModelScope.launch {
            ftueRepository.setCompleted(true)
            _currentStepIndex.value = -1
        }
    }

    /**
     * Skips directly to the completion overlay (used when items list is empty).
     */
    fun skipToCompletion() {
        _currentStepIndex.value = TOUR_STEPS.size - 1
    }

    /**
     * Registers target bounds for a specific control.
     * @param key Target key identifying the control
     * @param bounds Bounds of the control in window coordinates
     */
    fun registerTargetBounds(key: String, bounds: Rect) {
        _targetBounds.update { currentBounds ->
            currentBounds + (key to bounds)
        }
    }

    /**
     * Clears target bounds for a specific control.
     * @param key Target key identifying the control
     */
    fun clearTargetBounds(key: String) {
        _targetBounds.update { currentBounds ->
            currentBounds - key
        }
    }

    /**
     * Creates a demo item for the items list tour if none exist.
     * The demo item is labeled to indicate it's for demonstration purposes.
     */
    private fun createDemoItemIfNeeded() {
        if (demoItemId != null) return // Already created

        viewModelScope.launch {
            // Check if items list is empty
            val currentItems = itemsViewModel.items.value
            if (currentItems.isEmpty()) {
                // Create a demo scanned item
                // Note: This requires access to ScannedItem creation logic
                // For now, we'll add a simple placeholder that ItemsViewModel can handle
                // The actual implementation will depend on your ScannedItem model

                // TODO: Implement demo item creation via ItemsViewModel
                // demoItemId = "ftue_demo_item"
                // itemsViewModel.addDemoItem(...)
            }
        }
    }

    /**
     * Removes the demo item if it exists.
     */
    private fun removeDemoItem() {
        demoItemId?.let { id ->
            viewModelScope.launch {
                itemsViewModel.removeItem(id)
                demoItemId = null
            }
        }
    }

    companion object {
        /**
         * Complete ordered list of tour steps.
         */
        val TOUR_STEPS = listOf(
            // Step 0: Welcome (full-screen overlay)
            TourStep(
                key = TourStepKey.WELCOME,
                screen = TourScreen.CAMERA,
                targetKey = null,
                title = "Welcome to Scanium!",
                description = "Let's take a quick tour of the main features.",
                requiresUserAction = false
            ),

            // Step 1: Camera Settings Icon
            TourStep(
                key = TourStepKey.CAMERA_SETTINGS,
                screen = TourScreen.CAMERA,
                targetKey = "camera_settings",
                title = "Settings Menu",
                description = "Access theme, resolution, and processing settings here. You can also navigate to full app settings.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.ROUNDED_RECT
            ),

            // Step 2: Shutter Button
            TourStep(
                key = TourStepKey.CAMERA_SHUTTER,
                screen = TourScreen.CAMERA,
                targetKey = "camera_shutter",
                title = "Capture Button",
                description = "Tap to capture a single frame, or long-press to start continuous scanning. Tap again to stop scanning.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.CIRCLE
            ),

            // Step 3: Items List Button (transition - requires user tap)
            TourStep(
                key = TourStepKey.CAMERA_ITEMS_BUTTON,
                screen = TourScreen.CAMERA,
                targetKey = "camera_items_button",
                title = "Items List",
                description = "View all your scanned items here. The badge shows the current count. Tap this button to continue the tour.",
                requiresUserAction = true,
                spotlightShape = SpotlightShape.ROUNDED_RECT
            ),

            // Step 5: Action Dropdown FAB
            TourStep(
                key = TourStepKey.ITEMS_ACTION_FAB,
                screen = TourScreen.ITEMS_LIST,
                targetKey = "items_action_fab",
                title = "Quick Actions",
                description = "After selecting items, use this menu to export them, review drafts, or perform bulk actions.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.ROUNDED_RECT
            ),

            // Step 6: AI Assistant Button
            TourStep(
                key = TourStepKey.ITEMS_AI_ASSISTANT,
                screen = TourScreen.ITEMS_LIST,
                targetKey = "items_ai_assistant",
                title = "AI Assistant",
                description = "Get help with pricing, descriptions, and export strategies for your selected items.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.CIRCLE
            ),

            // Step 7: Swipe to Delete
            TourStep(
                key = TourStepKey.ITEMS_SWIPE_DELETE,
                screen = TourScreen.ITEMS_LIST,
                targetKey = "items_first_item",
                title = "Swipe to Delete",
                description = "Swipe right on any item to delete it. You can undo immediately after deletion.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.ROUNDED_RECT
            ),

            // Step 8: Selection & Long-Press
            TourStep(
                key = TourStepKey.ITEMS_SELECTION,
                screen = TourScreen.ITEMS_LIST,
                targetKey = "items_first_item",
                title = "Select & Preview",
                description = "Tap items to select them for bulk actions. Long-press any item to preview its full-resolution image.",
                requiresUserAction = false,
                spotlightShape = SpotlightShape.ROUNDED_RECT
            ),

            // Step 9: Completion (full-screen overlay)
            TourStep(
                key = TourStepKey.COMPLETION,
                screen = TourScreen.ITEMS_LIST,
                targetKey = null,
                title = "You're Ready!",
                description = "You're all set to start scanning and cataloging objects. Happy scanning!",
                requiresUserAction = false
            )
        )

        /**
         * Factory for creating TourViewModel instances.
         */
        fun provideFactory(
            ftueRepository: FtueRepository,
            itemsViewModel: ItemsViewModel
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TourViewModel::class.java)) {
                        return TourViewModel(ftueRepository, itemsViewModel) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
