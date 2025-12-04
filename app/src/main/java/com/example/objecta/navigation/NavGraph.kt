package com.example.objecta.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.objecta.camera.CameraScreen
import com.example.objecta.items.ItemsListScreen
import com.example.objecta.items.ItemsViewModel

/**
 * Navigation routes for the app.
 */
object Routes {
    const val CAMERA = "camera"
    const val ITEMS_LIST = "items_list"
}

/**
 * Navigation graph for Objecta app.
 *
 * Two main screens:
 * - CameraScreen: Main camera interface (start destination)
 * - ItemsListScreen: List of detected items
 */
@Composable
fun ObjectaNavGraph(
    navController: NavHostController,
    itemsViewModel: ItemsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CAMERA
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onNavigateToItems = {
                    navController.navigate(Routes.ITEMS_LIST)
                },
                itemsViewModel = itemsViewModel
            )
        }

        composable(Routes.ITEMS_LIST) {
            ItemsListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                itemsViewModel = itemsViewModel
            )
        }
    }
}
