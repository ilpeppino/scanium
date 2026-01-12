package com.scanium.app.ftue

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemsViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
 * Uses assisted injection because it depends on ItemsViewModel which is scoped to the composable.
 *
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 *
 * @param ftueRepository Repository for persisting FTUE state (injected by Hilt)
 * @param itemsViewModel Shared ItemsViewModel for demo item management (passed at creation time)
 */
class TourViewModel
    @AssistedInject
    constructor(
        private val ftueRepository: FtueRepository,
        @Assisted private val itemsViewModel: ItemsViewModel,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemsViewModel: ItemsViewModel): TourViewModel
        }

        // Current step index (-1 = not started)
        private val _currentStepIndex = MutableStateFlow(-1)
        val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

        // Derived current step
        val currentStep: StateFlow<TourStep?> =
            currentStepIndex.map { index ->
                if (index in TOUR_STEPS.indices) TOUR_STEPS[index] else null
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        // Tour active flag (true when force enabled OR not completed)
        val isTourActive: StateFlow<Boolean> =
            combine(
                ftueRepository.completedFlow,
                ftueRepository.forceEnabledFlow,
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
        fun registerTargetBounds(
            key: String,
            bounds: Rect,
        ) {
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
             *
             * Updated to match the 7-step guided onboarding flow that teaches beta testers
             * how to use Scanium's core functionality:
             * 1. Take first photo → 2. Open item list → 3. Add extra photos →
             * 4. Edit attributes → 5. Use AI assistant → 6. Save changes → 7. Share bundle
             */
            val TOUR_STEPS =
                listOf(
                    // Step 0: Welcome (full-screen overlay)
                    TourStep(
                        key = TourStepKey.WELCOME,
                        screen = TourScreen.CAMERA,
                        targetKey = null,
                        title = "Welcome to Scanium!",
                        description = "Let's take a quick tour to learn how to scan, edit, and share your items.",
                        requiresUserAction = false,
                    ),
                    // Step 1: Take First Photo
                    TourStep(
                        key = TourStepKey.TAKE_FIRST_PHOTO,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_shutter",
                        title = "Capture Your First Item",
                        description = "Tap the shutter button to take a photo of your first item. Point your camera at any object you want to catalog.",
                        requiresUserAction = true, // Advances when item is added
                        spotlightShape = SpotlightShape.CIRCLE,
                    ),
                    // Step 2: Open Item List
                    TourStep(
                        key = TourStepKey.OPEN_ITEM_LIST,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_items_button",
                        title = "View Your Items",
                        description = "Tap here to open your scanned items list. The badge shows how many items you've captured.",
                        requiresUserAction = true, // Advances when navigating to items list
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 3: Add Extra Photos (in Edit Item screen)
                    TourStep(
                        key = TourStepKey.ADD_EXTRA_PHOTOS,
                        screen = TourScreen.EDIT_ITEM,
                        targetKey = "edit_add_photo",
                        title = "Add More Photos",
                        description = "Capture additional angles or details. Multiple photos help buyers see your item better and improve AI descriptions.",
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 4: Edit Attributes
                    TourStep(
                        key = TourStepKey.EDIT_ATTRIBUTES,
                        screen = TourScreen.EDIT_ITEM,
                        targetKey = "edit_brand_field",
                        title = "Improve Item Details",
                        description = "Fill in brand, type, color, size, and other attributes. Better details mean better listings and faster sales.",
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 5: Use AI Assistant
                    TourStep(
                        key = TourStepKey.USE_AI_ASSISTANT,
                        screen = TourScreen.EDIT_ITEM,
                        targetKey = "edit_ai_button",
                        title = "Generate with AI",
                        description = "Let AI create a professional marketplace description using your photos and attributes. Get pricing insights too!",
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 6: Save Changes
                    TourStep(
                        key = TourStepKey.SAVE_CHANGES,
                        screen = TourScreen.EDIT_ITEM,
                        targetKey = "edit_save_button",
                        title = "Save Your Work",
                        description = "Tap Save to keep your changes. Your item will be ready for export.",
                        requiresUserAction = true, // Advances when navigating back to items list
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 7: Share Bundle
                    TourStep(
                        key = TourStepKey.SHARE_BUNDLE,
                        screen = TourScreen.ITEMS_LIST,
                        targetKey = "items_share_button",
                        title = "Export & Share",
                        description = "Select items and tap the share button to export them as images, text, or a ZIP bundle for listing platforms.",
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.CIRCLE,
                    ),
                    // Step 8: Completion (full-screen overlay)
                    TourStep(
                        key = TourStepKey.COMPLETION,
                        screen = TourScreen.ITEMS_LIST,
                        targetKey = null,
                        title = "You're Ready!",
                        description = "You're all set to scan, catalog, and sell your items. Happy scanning!",
                        requiresUserAction = false,
                    ),
                )

            /**
             * Factory for creating TourViewModel instances with assisted injection.
             * @param assistedFactory The Hilt-generated assisted factory
             * @param itemsViewModel The shared ItemsViewModel instance
             */
            fun provideFactory(
                assistedFactory: Factory,
                itemsViewModel: ItemsViewModel,
            ): ViewModelProvider.Factory {
                return object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(TourViewModel::class.java)) {
                            return assistedFactory.create(itemsViewModel) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }
            }
        }
    }
