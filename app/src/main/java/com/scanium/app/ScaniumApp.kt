package com.scanium.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.navigation.ScaniumNavGraph

/**
 * Root Composable for the Scanium app.
 *
 * Sets up:
 * - Navigation controller
 * - Shared ItemsViewModel
 * - Navigation graph
 */
@Composable
fun ScaniumApp() {
    val navController = rememberNavController()

    // Shared ViewModel across screens
    val itemsViewModel: ItemsViewModel = viewModel()

    ScaniumNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel
    )
}

// Backward compatibility alias
@Deprecated("Use ScaniumApp instead", ReplaceWith("ScaniumApp()"))
@Composable
fun ObjectaApp() {
    ScaniumApp()
}
