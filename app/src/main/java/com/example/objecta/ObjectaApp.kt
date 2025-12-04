package com.example.objecta

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.objecta.items.ItemsViewModel
import com.example.objecta.navigation.ObjectaNavGraph

/**
 * Root Composable for the Objecta app.
 *
 * Sets up:
 * - Navigation controller
 * - Shared ItemsViewModel
 * - Navigation graph
 */
@Composable
fun ObjectaApp() {
    val navController = rememberNavController()

    // Shared ViewModel across screens
    val itemsViewModel: ItemsViewModel = viewModel()

    ObjectaNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel
    )
}
