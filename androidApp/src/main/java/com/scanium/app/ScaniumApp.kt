package com.scanium.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.MockEbayApi
import com.scanium.app.selling.data.MockEbayConfigManager
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.persistence.NoopScannedItemSyncer
import com.scanium.app.items.persistence.ScannedItemDatabase
import com.scanium.app.items.persistence.ScannedItemRepository
import com.scanium.app.selling.persistence.ListingDraftRepository
import com.scanium.app.navigation.ScaniumNavGraph
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.ml.classification.CloudClassifier
import com.scanium.app.ml.classification.OnDeviceClassifier
import com.scanium.app.ml.classification.StableItemCropper
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.EntitlementManager
import com.scanium.app.ui.settings.SettingsViewModel
import com.scanium.app.billing.BillingRepository
import com.scanium.app.billing.AndroidBillingProvider
import com.scanium.app.billing.FakeBillingProvider
import com.scanium.app.billing.ui.PaywallViewModel
import com.scanium.app.data.AndroidRemoteConfigProvider

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
    val scope = rememberCoroutineScope()

    val context = LocalContext.current.applicationContext
    val classificationPreferences = remember { ClassificationPreferences(context) }
    val database = remember { ScannedItemDatabase.getInstance(context) }
    
    val settingsRepository = remember { SettingsRepository(context) }
    val billingRepository = remember { BillingRepository(context) }
    val configProvider = remember { AndroidRemoteConfigProvider(context, scope) }
    
    // Choose billing provider based on build type
    val billingProvider = remember {
        if (BuildConfig.DEBUG) {
            FakeBillingProvider(billingRepository)
        } else {
            AndroidBillingProvider(context, billingRepository, scope)
        }
    }
    
    val entitlementManager = remember { EntitlementManager(settingsRepository, billingProvider) }
    
    val classificationModeViewModel: ClassificationModeViewModel = viewModel(
        factory = ClassificationModeViewModel.factory(classificationPreferences)
    )
    
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(context as android.app.Application, settingsRepository, entitlementManager, configProvider)
    )
    
    val paywallViewModel: PaywallViewModel = viewModel(
        factory = PaywallViewModel.Factory(billingProvider)
    )

    // Shared ViewModel across screens
    val itemsViewModel: ItemsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val itemsRepository = ScannedItemRepository(
                    dao = database.scannedItemDao(),
                    syncer = NoopScannedItemSyncer
                )
                
                // Get Telemetry facade from Application
                val telemetry = (context as? com.scanium.app.ScaniumApplication)?.telemetry

                return ItemsViewModel(
                    classificationMode = classificationModeViewModel.classificationMode,
                    entitlementManager = entitlementManager,
                    onDeviceClassifier = OnDeviceClassifier(),
                    cloudClassifier = CloudClassifier(context = context),
                    itemsStore = itemsRepository,
                    stableItemCropper = StableItemCropper(context),
                    telemetry = telemetry
                ) as T
            }
        }
    )

    val draftStore = remember { ListingDraftRepository(database.listingDraftDao()) }

    // Use the config manager's current config for MockEbayApi
    val mockEbayConfig by MockEbayConfigManager.config.collectAsState()
    val marketplaceService = remember(mockEbayConfig) {
        EbayMarketplaceService(context, MockEbayApi(mockEbayConfig))
    }

    ScaniumNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel,
        classificationModeViewModel = classificationModeViewModel,
        settingsViewModel = settingsViewModel,
        paywallViewModel = paywallViewModel,
        marketplaceService = marketplaceService,
        draftStore = draftStore
    )
}

// Backward compatibility alias
@Deprecated("Use ScaniumApp instead", ReplaceWith("ScaniumApp()"))
@Composable
fun ObjectaApp() {
    ScaniumApp()
}
