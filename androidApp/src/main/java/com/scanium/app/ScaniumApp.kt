package com.scanium.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.scanium.app.audio.AndroidSoundManager
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.billing.ui.PaywallViewModel
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.TourViewModel
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.navigation.ScaniumNavGraph
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.MockEbayApi
import com.scanium.app.selling.data.MockEbayConfigManager
import com.scanium.app.selling.persistence.ListingDraftRepository
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.ui.settings.SettingsViewModel
import com.scanium.app.di.DraftStoreEntryPoint
import com.scanium.app.di.SettingsRepositoryEntryPoint
import com.scanium.app.di.TourViewModelFactoryEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Root Composable for the Scanium app.
 *
 * Sets up:
 * - Navigation controller
 * - Shared ViewModels via Hilt
 * - Navigation graph
 *
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 * The manual DI setup has been replaced with hiltViewModel() calls,
 * dramatically reducing boilerplate and improving testability.
 */
@Composable
fun ScaniumApp() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext

    // All ViewModels are now injected via Hilt - no manual factory creation needed
    val classificationModeViewModel: ClassificationModeViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val paywallViewModel: PaywallViewModel = hiltViewModel()
    val itemsViewModel: ItemsViewModel = hiltViewModel()

    // TourViewModel uses assisted injection because it depends on ItemsViewModel
    val tourViewModelFactory = EntryPointAccessors.fromApplication(
        context,
        TourViewModelFactoryEntryPoint::class.java
    ).tourViewModelFactory()

    val tourViewModel: TourViewModel = viewModel(
        factory = TourViewModel.provideFactory(tourViewModelFactory, itemsViewModel)
    )

    // ListingDraftRepository and EbayMarketplaceService remain as remember blocks
    // because they're not ViewModels (they're used directly in composables)
    val draftStore: ListingDraftRepository = hiltEntryPoint<DraftStoreEntryPoint>(context).draftStore()

    // Sound manager needs SettingsRepository for the enabled flow
    val settingsRepository: SettingsRepository = hiltEntryPoint<SettingsRepositoryEntryPoint>(context).settingsRepository()
    val soundManager = remember {
        AndroidSoundManager(
            context = context,
            soundsEnabledFlow = settingsRepository.soundsEnabledFlow
        )
    }

    // Use the config manager's current config for MockEbayApi
    val mockEbayConfig by MockEbayConfigManager.config.collectAsState()
    val marketplaceService = remember(mockEbayConfig) {
        EbayMarketplaceService(context, MockEbayApi(mockEbayConfig))
    }

    DisposableEffect(soundManager) {
        soundManager.preload()
        onDispose { soundManager.release() }
    }

    CompositionLocalProvider(LocalSoundManager provides soundManager) {
        ScaniumNavGraph(
            navController = navController,
            itemsViewModel = itemsViewModel,
            classificationModeViewModel = classificationModeViewModel,
            settingsViewModel = settingsViewModel,
            paywallViewModel = paywallViewModel,
            marketplaceService = marketplaceService,
            draftStore = draftStore,
            tourViewModel = tourViewModel
        )
    }
}

/**
 * Helper function to access Hilt entry points.
 */
@Composable
private inline fun <reified T : Any> hiltEntryPoint(context: android.content.Context): T {
    return EntryPointAccessors.fromApplication(context, T::class.java)
}

// Backward compatibility alias
@Deprecated("Use ScaniumApp instead", ReplaceWith("ScaniumApp()"))
@Composable
fun ObjectaApp() {
    ScaniumApp()
}
