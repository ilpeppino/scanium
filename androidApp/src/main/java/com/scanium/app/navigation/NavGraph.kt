package com.scanium.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.scanium.app.camera.CameraScreen
import com.scanium.app.items.ItemsListScreen
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.selling.ui.DraftReviewScreen
import com.scanium.app.selling.ui.PostingAssistScreen
import com.scanium.app.selling.ui.SellOnEbayScreen
import com.scanium.app.selling.assistant.AssistantScreen

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
    marketplaceService: EbayMarketplaceService,
    draftStore: ListingDraftStore
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
                itemsViewModel = itemsViewModel,
                classificationModeViewModel = classificationModeViewModel
            )
        }

        composable(Routes.ITEMS_LIST) {
            ItemsListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSell = { ids ->
                    if (ids.isNotEmpty()) {
                        navController.navigate("${Routes.SELL_ON_EBAY}/${ids.joinToString(",")}")
                    }
                },
                onNavigateToDraft = { ids ->
                    if (ids.isNotEmpty()) {
                        val encoded = Uri.encode(ids.joinToString(","))
                        navController.navigate("${Routes.DRAFT_REVIEW}?itemIds=$encoded")
                    }
                },
                onNavigateToAssistant = { ids ->
                    if (ids.isNotEmpty()) {
                        val encoded = Uri.encode(ids.joinToString(","))
                        navController.navigate("${Routes.ASSISTANT}?itemIds=$encoded")
                    }
                },
                draftStore = draftStore,
                itemsViewModel = itemsViewModel
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

    ScaniumNavGraph(
        navController = navController,
        itemsViewModel = itemsViewModel,
        classificationModeViewModel = classificationModeViewModel,
        marketplaceService = marketplaceService,
        draftStore = com.scanium.app.selling.persistence.NoopListingDraftStore
    )
}
