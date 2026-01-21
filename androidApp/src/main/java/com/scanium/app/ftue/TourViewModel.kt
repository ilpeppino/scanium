package com.scanium.app.ftue

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.R
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
            currentStepIndex
                .map { index ->
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
            viewModelScope.launch {
                ftueRepository.setCompleted(true)
                _currentStepIndex.value = -1
            }
        }

        /**
         * Completes the tour and marks it as completed.
         */
        fun completeTour() {
            viewModelScope.launch {
                ftueRepository.setCompleted(true)
                _currentStepIndex.value = -1
            }
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


        companion object {
            /**
             * Complete ordered list of tour steps.
             *
             * Streamlined 5-step flow focusing on camera UI education:
             * 0. Welcome → 1. Shutter → 2. Flip Camera → 3. Items List → 4. Settings
             */
            val TOUR_STEPS =
                listOf(
                    // Step 0: Welcome (full-screen overlay)
                    TourStep(
                        key = TourStepKey.WELCOME,
                        screen = TourScreen.CAMERA,
                        targetKey = null,
                        titleRes = R.string.ftue_tour_welcome_title,
                        descriptionRes = R.string.ftue_tour_welcome_desc,
                        requiresUserAction = false,
                    ),
                    // Step 1: Shutter Button
                    TourStep(
                        key = TourStepKey.CAMERA_SHUTTER,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_ui_shutter",
                        titleRes = R.string.ftue_tour_camera_shutter_title,
                        descriptionRes = R.string.ftue_tour_camera_shutter_desc,
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.CIRCLE,
                    ),
                    // Step 2: Flip Camera
                    TourStep(
                        key = TourStepKey.CAMERA_FLIP,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_ui_flip",
                        titleRes = R.string.ftue_tour_camera_flip_title,
                        descriptionRes = R.string.ftue_tour_camera_flip_desc,
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 3: Items List Button
                    TourStep(
                        key = TourStepKey.CAMERA_ITEMS,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_ui_items",
                        titleRes = R.string.ftue_tour_camera_items_title,
                        descriptionRes = R.string.ftue_tour_camera_items_desc,
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
                    ),
                    // Step 4: Settings Button
                    TourStep(
                        key = TourStepKey.CAMERA_SETTINGS,
                        screen = TourScreen.CAMERA,
                        targetKey = "camera_ui_settings",
                        titleRes = R.string.ftue_tour_camera_settings_title,
                        descriptionRes = R.string.ftue_tour_camera_settings_desc,
                        requiresUserAction = false,
                        spotlightShape = SpotlightShape.ROUNDED_RECT,
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
