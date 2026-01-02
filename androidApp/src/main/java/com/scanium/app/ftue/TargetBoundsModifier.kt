package com.scanium.app.ftue

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Modifier extension for capturing and registering target bounds for the FTUE tour.
 *
 * This modifier uses onGloballyPositioned to capture the bounds of a composable
 * and registers them with the TourViewModel for spotlight highlighting.
 *
 * @param key Unique key identifying this tour target
 * @param tourViewModel TourViewModel instance to register bounds with
 * @return Modified Modifier with bounds capture behavior
 */
fun Modifier.tourTarget(
    key: String,
    tourViewModel: TourViewModel,
): Modifier =
    composed {
        this
            .onGloballyPositioned { coordinates ->
                // Get bounds in window coordinates
                val bounds = coordinates.boundsInWindow()

                // Register with tour view model
                tourViewModel.registerTargetBounds(key, bounds)
            }
            .also {
                // Clean up bounds when composable is disposed
                DisposableEffect(key) {
                    onDispose {
                        tourViewModel.clearTargetBounds(key)
                    }
                }
            }
    }
