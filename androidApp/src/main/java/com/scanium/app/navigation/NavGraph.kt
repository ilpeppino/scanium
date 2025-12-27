package com.scanium.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.scanium.app.camera.CameraScreen
import com.scanium.app.camera.CameraViewModel
import com.scanium.app.items.ItemsListScreen
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.selling.ui.DraftReviewScreen
import com.scanium.app.selling.ui.PostingAssistScreen
import com.scanium.app.selling.ui.SellOnEbayScreen
import com.scanium.app.selling.assistant.AssistantScreen

import com.scanium.app.ui.settings.SettingsAssistantScreen
import com.scanium.app.ui.settings.SettingsCameraScreen
import com.scanium.app.ui.settings.SettingsFeedbackScreen
import com.scanium.app.ui.settings.SettingsGeneralScreen
import com.scanium.app.ui.settings.SettingsHomeScreen
import com.scanium.app.ui.settings.SettingsPrivacyScreen
import com.scanium.app.ui.settings.SettingsViewModel
import com.scanium.app.ui.settings.DataUsageScreen
import com.scanium.app.ui.settings.PrivacyPolicyScreen
import com.scanium.app.ui.settings.TermsScreen
import com.scanium.app.ui.settings.DeveloperOptionsScreen
import com.scanium.app.ui.settings.DeveloperOptionsViewModel
import com.scanium.app.diagnostics.DiagnosticsRepository
import com.scanium.app.billing.ui.PaywallScreen
import com.scanium.app.billing.ui.PaywallViewModel

/**
 * Navigation routes for the app.
 */
object Routes {
    const val CAMERA = "camera"
    const val ITEMS_LIST = "items_list"
    const val SELL_ON_EBAY = "sell_on_ebay"
    const val DRAFT_REVIEW = "draft_review"
    const val POSTING_ASSIST = "posting_assist"
    const val ASSISTANT = "assistant"
    const val SETTINGS_HOME = "settings/home"
    const val SETTINGS_GENERAL = "settings/general"
    const val SETTINGS_CAMERA = "settings/camera"
    const val SETTINGS_ASSISTANT = "settings/assistant"
    const val SETTINGS_FEEDBACK = "settings/feedback"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_DEVELOPER = "settings/developer"
    const val DATA_USAGE = "data_usage"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val ABOUT = "about"
    const val PAYWALL = "paywall"
}

/**
 * Navigation graph for Scanium app.
 *
 * Two main screens:
 * - CameraScreen: Main camera interface (start destination)
 * - ItemsListScreen: List of detected items
 */
@Composable
fun ScaniumNavGraph(
    navController: NavHostController,
    itemsViewModel: ItemsViewModel,
    classificationModeViewModel: ClassificationModeViewModel,
    settingsViewModel: SettingsViewModel,
    paywallViewModel: PaywallViewModel,
    marketplaceService: EbayMarketplaceService,
    draftStore: ListingDraftStore,
    tourViewModel: com.scanium.app.ftue.TourViewModel
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
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS_HOME)
                },
                itemsViewModel = itemsViewModel,
                classificationModeViewModel = classificationModeViewModel,
                tourViewModel = tourViewModel
            )
        }

        composable(Routes.SETTINGS_HOME) {
            SettingsHomeScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onGeneralClick = { navController.navigate(Routes.SETTINGS_GENERAL) },
                onCameraClick = { navController.navigate(Routes.SETTINGS_CAMERA) },
                onAssistantClick = { navController.navigate(Routes.SETTINGS_ASSISTANT) },
                onFeedbackClick = { navController.navigate(Routes.SETTINGS_FEEDBACK) },
                onPrivacyClick = { navController.navigate(Routes.SETTINGS_PRIVACY) },
                onDeveloperClick = { navController.navigate(Routes.SETTINGS_DEVELOPER) }
            )
        }

        composable(Routes.SETTINGS_GENERAL) {
            SettingsGeneralScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onUpgradeClick = { navController.navigate(Routes.PAYWALL) }
            )
        }

        composable(Routes.SETTINGS_CAMERA) {
            val cameraEntry = remember(navController) {
                navController.getBackStackEntry(Routes.CAMERA)
            }
            val cameraViewModel: CameraViewModel = viewModel(cameraEntry)
            SettingsCameraScreen(
                settingsViewModel = settingsViewModel,
                classificationViewModel = classificationModeViewModel,
                itemsViewModel = itemsViewModel,
                cameraViewModel = cameraViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_ASSISTANT) {
            SettingsAssistantScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_FEEDBACK) {
            SettingsFeedbackScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_PRIVACY) {
            SettingsPrivacyScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDataUsage = { navController.navigate(Routes.DATA_USAGE) },
                onNavigateToPrivacyPolicy = { navController.navigate(Routes.PRIVACY) },
                onNavigateToTerms = { navController.navigate(Routes.TERMS) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.SETTINGS_DEVELOPER) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val diagnosticsRepository = remember { DiagnosticsRepository(context) }
            val devSettingsRepository = remember { com.scanium.app.data.SettingsRepository(context) }
            val devFtueRepository = remember { com.scanium.app.ftue.FtueRepository(context) }
            val billingRepository = remember { com.scanium.app.billing.BillingRepository(context) }
            val billingProvider = remember { com.scanium.app.billing.FakeBillingProvider(billingRepository) }
            val entitlementManager = remember { com.scanium.app.data.EntitlementManager(devSettingsRepository, billingProvider) }
            val configProvider = remember { com.scanium.app.data.AndroidRemoteConfigProvider(context, scope) }
            val connectivityObserver = remember { com.scanium.app.platform.ConnectivityObserver(context) }
            val apiKeyStore = remember { com.scanium.app.config.SecureApiKeyStore(context) }
            val featureFlagRepository = remember {
                com.scanium.app.data.AndroidFeatureFlagRepository(
                    settingsRepository = devSettingsRepository,
                    configProvider = configProvider,
                    entitlementPolicyFlow = entitlementManager.entitlementPolicyFlow,
                    connectivityStatusProvider = connectivityObserver,
                    apiKeyStore = apiKeyStore
                )
            }
            val developerOptionsViewModel: DeveloperOptionsViewModel = viewModel(
                factory = DeveloperOptionsViewModel.Factory(
                    context.applicationContext as android.app.Application,
                    devSettingsRepository,
                    devFtueRepository,
                    diagnosticsRepository,
                    featureFlagRepository,
                    connectivityObserver
                )
            )
            DeveloperOptionsScreen(
                viewModel = developerOptionsViewModel,
                classificationViewModel = classificationModeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.PAYWALL) {
            PaywallScreen(
                viewModel = paywallViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DATA_USAGE) {
            DataUsageScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.TERMS) {
            TermsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            com.scanium.app.ui.settings.AboutScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.ITEMS_LIST) {
            ItemsListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAssistant = { ids ->
                    if (ids.isNotEmpty()) {
                        val encoded = Uri.encode(ids.joinToString(","))
                        navController.navigate("${Routes.ASSISTANT}?itemIds=$encoded")
                    }
                },
                draftStore = draftStore,
                itemsViewModel = itemsViewModel,
                tourViewModel = tourViewModel
            )
        }

        composable(
            route = "${Routes.SELL_ON_EBAY}/{itemIds}",
            arguments = listOf(navArgument("itemIds") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val ids = backStackEntry.arguments?.getString("itemIds")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val selectedItems = itemsViewModel.items.collectAsState().value.filter { ids.contains(it.id) }
            SellOnEbayScreen(
                onNavigateBack = { navController.popBackStack() },
                selectedItems = selectedItems,
                marketplaceService = marketplaceService,
                itemsViewModel = itemsViewModel
            )
        }

        composable(
            route = "${Routes.DRAFT_REVIEW}?itemIds={itemIds}",
            arguments = listOf(navArgument("itemIds") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val ids = backStackEntry.arguments?.getString("itemIds")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            DraftReviewScreen(
                itemIds = ids,
                onBack = { navController.popBackStack() },
                itemsViewModel = itemsViewModel,
                draftStore = draftStore,
                onOpenPostingAssist = { targets, index ->
                    val encoded = Uri.encode(targets.joinToString(","))
                    navController.navigate("${Routes.POSTING_ASSIST}?itemIds=$encoded&index=$index")
                },
                onOpenAssistant = { targets ->
                    val encoded = Uri.encode(targets.joinToString(","))
                    navController.navigate("${Routes.ASSISTANT}?itemIds=$encoded")
                }
            )
        }

        composable(
            route = "${Routes.DRAFT_REVIEW}/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            DraftReviewScreen(
                itemIds = listOf(itemId),
                onBack = { navController.popBackStack() },
                itemsViewModel = itemsViewModel,
                draftStore = draftStore,
                onOpenPostingAssist = { ids, index ->
                    val encoded = Uri.encode(ids.joinToString(","))
                    navController.navigate("${Routes.POSTING_ASSIST}?itemIds=$encoded&index=$index")
                },
                onOpenAssistant = { ids ->
                    val encoded = Uri.encode(ids.joinToString(","))
                    navController.navigate("${Routes.ASSISTANT}?itemIds=$encoded")
                }
            )
        }

        composable(
            route = "${Routes.POSTING_ASSIST}?itemIds={itemIds}&index={index}",
            arguments = listOf(
                navArgument("itemIds") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("index") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val ids = backStackEntry.arguments?.getString("itemIds")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            PostingAssistScreen(
                itemIds = ids,
                startIndex = index,
                onBack = { navController.popBackStack() },
                itemsViewModel = itemsViewModel,
                draftStore = draftStore,
                onOpenAssistant = { ids, indexToUse ->
                    val encoded = Uri.encode(ids.joinToString(","))
                    navController.navigate("${Routes.ASSISTANT}?itemIds=$encoded&index=$indexToUse")
                }
            )
        }

        composable(
            route = "${Routes.ASSISTANT}?itemIds={itemIds}&index={index}",
            arguments = listOf(
                navArgument("itemIds") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("index") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val ids = backStackEntry.arguments?.getString("itemIds")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val selectedIds = if (ids.isEmpty()) emptyList() else listOf(ids.getOrNull(index) ?: ids.first())
            AssistantScreen(
                itemIds = selectedIds,
                onBack = { navController.popBackStack() },
                onOpenPostingAssist = { targets, targetIndex ->
                    val encoded = Uri.encode(targets.joinToString(","))
                    navController.navigate("${Routes.POSTING_ASSIST}?itemIds=$encoded&index=$targetIndex")
                },
                itemsViewModel = itemsViewModel,
                draftStore = draftStore
            )
        }
    }
}

// Backward compatibility alias
@Deprecated("Use ScaniumNavGraph instead", ReplaceWith("ScaniumNavGraph(navController, itemsViewModel, classificationModeViewModel, marketplaceService)"))
@Composable
fun ObjectaNavGraph(
    navController: NavHostController,
    itemsViewModel: ItemsViewModel,
    classificationModeViewModel: ClassificationModeViewModel
) {
    // Create marketplace service for backward compatibility
    val marketplaceService = androidx.compose.ui.platform.LocalContext.current.let { context ->
        androidx.compose.runtime.remember { EbayMarketplaceService(context, com.scanium.app.selling.data.MockEbayApi()) }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settingsRepository = androidx.compose.runtime.remember { com.scanium.app.data.SettingsRepository(context) }
    val billingRepository = androidx.compose.runtime.remember { com.scanium.app.billing.BillingRepository(context) }
    val billingProvider = androidx.compose.runtime.remember { com.scanium.app.billing.FakeBillingProvider(billingRepository) }
    val entitlementManager = androidx.compose.runtime.remember { com.scanium.app.data.EntitlementManager(settingsRepository, billingProvider) }
    val configProvider = androidx.compose.runtime.remember { com.scanium.app.data.AndroidRemoteConfigProvider(context, scope) }
    val connectivityObserver = androidx.compose.runtime.remember { com.scanium.app.platform.ConnectivityObserver(context) }
    val apiKeyStore = androidx.compose.runtime.remember { com.scanium.app.config.SecureApiKeyStore(context) }
    val featureFlagRepository = androidx.compose.runtime.remember {
        com.scanium.app.data.AndroidFeatureFlagRepository(
            settingsRepository = settingsRepository,
            configProvider = configProvider,
            entitlementPolicyFlow = entitlementManager.entitlementPolicyFlow,
            connectivityStatusProvider = connectivityObserver,
            apiKeyStore = apiKeyStore
        )
    }
    val ftueRepository = androidx.compose.runtime.remember { com.scanium.app.ftue.FtueRepository(context) }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(context.applicationContext as android.app.Application, settingsRepository, entitlementManager, configProvider, featureFlagRepository, ftueRepository)
    )
    val paywallViewModel: PaywallViewModel = viewModel(
        factory = PaywallViewModel.Factory(billingProvider)
    )

    val tourViewModel: com.scanium.app.ftue.TourViewModel = viewModel(
        factory = com.scanium.app.ftue.TourViewModel.provideFactory(ftueRepository, itemsViewModel)
    )

    ScaniumNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel,
        classificationModeViewModel = classificationModeViewModel,
        settingsViewModel = settingsViewModel,
        paywallViewModel = paywallViewModel,
        marketplaceService = marketplaceService,
        draftStore = com.scanium.app.selling.persistence.NoopListingDraftStore,
        tourViewModel = tourViewModel
    )
}
