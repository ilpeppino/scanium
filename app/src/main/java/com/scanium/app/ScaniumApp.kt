package com.scanium.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.MockEbayApi
import com.scanium.app.selling.data.MockEbayConfigManager
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.navigation.ScaniumNavGraph
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.ml.classification.CloudClassifier
import com.scanium.app.ml.classification.OnDeviceClassifier
import com.scanium.app.settings.ClassificationModeViewModel

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

    val context = LocalContext.current.applicationContext
    val classificationPreferences = remember { ClassificationPreferences(context) }
    val classificationModeViewModel: ClassificationModeViewModel = viewModel(
        factory = ClassificationModeViewModel.factory(classificationPreferences)
    )

    // Shared ViewModel across screens
    val itemsViewModel: ItemsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ItemsViewModel(
                    classificationMode = classificationModeViewModel.classificationMode,
                    onDeviceClassifier = OnDeviceClassifier(),
                    cloudClassifier = CloudClassifier()
                ) as T
            }
        }
    )

    // Use the config manager's current config for MockEbayApi
    val mockEbayConfig by MockEbayConfigManager.config.collectAsState()
    val marketplaceService = remember(mockEbayConfig) {
        EbayMarketplaceService(context, MockEbayApi(mockEbayConfig))
    }

    ScaniumNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel,
        classificationModeViewModel = classificationModeViewModel,
        marketplaceService = marketplaceService
    )
}

// Backward compatibility alias
@Deprecated("Use ScaniumApp instead", ReplaceWith("ScaniumApp()"))
@Composable
fun ObjectaApp() {
    ScaniumApp()
}
