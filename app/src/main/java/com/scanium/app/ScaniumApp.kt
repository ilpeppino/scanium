package com.scanium.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.scanium.app.data.ItemsRepositoryImpl
import com.scanium.app.data.ScaniumDatabase
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ItemsViewModelFactory
import com.scanium.app.navigation.ScaniumNavGraph

/**
 * Root Composable for the Scanium app.
 *
 * Sets up:
 * - Database and repository (manual dependency injection)
 * - Navigation controller
 * - Shared ItemsViewModel with repository
 * - Navigation graph
 */
@Composable
fun ScaniumApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Manual DI: Create database and repository
    // In production, consider using ViewModelFactory for proper lifecycle management
    val database = ScaniumDatabase.getInstance(context)
    val repository = ItemsRepositoryImpl(database.itemsDao())

    // Shared ViewModel across screens with factory
    val itemsViewModel: ItemsViewModel = viewModel(
        factory = ItemsViewModelFactory(repository)
    )

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
