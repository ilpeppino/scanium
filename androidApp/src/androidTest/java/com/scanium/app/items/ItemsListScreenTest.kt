package com.scanium.app.items

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ItemCategory
import com.scanium.app.R
import com.scanium.app.items.export.bundle.BundleZipExporter
import com.scanium.app.items.export.bundle.ExportBundleRepository
import com.scanium.app.items.export.bundle.ExportViewModel
import com.scanium.app.items.export.bundle.PlainTextExporter
import com.scanium.app.items.photos.ItemPhotoManager
import com.scanium.app.items.photos.PerItemDedupeHelper
import com.scanium.app.selling.persistence.NoopListingDraftStore
import com.scanium.app.ui.theme.ScaniumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemsListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyStateIsVisibleWhenNoItems() {
        val itemsViewModel = createAndroidTestItemsViewModel()
        val exportViewModel = createExportViewModel(context)

        composeTestRule.setContent {
            ScaniumTheme {
                ItemsListScreen(
                    onNavigateBack = {},
                    onNavigateToAssistant = {},
                    onNavigateToEdit = {},
                    draftStore = NoopListingDraftStore,
                    itemsViewModel = itemsViewModel,
                    exportViewModel = exportViewModel,
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.items_empty_title))
            .assertIsDisplayed()
    }

    @Test
    fun itemRowShowsLabelWhenItemsPresent() {
        val itemsViewModel = createAndroidTestItemsViewModel()
        val exportViewModel = createExportViewModel(context)

        composeTestRule.setContent {
            ScaniumTheme {
                ItemsListScreen(
                    onNavigateBack = {},
                    onNavigateToAssistant = {},
                    onNavigateToEdit = {},
                    draftStore = NoopListingDraftStore,
                    itemsViewModel = itemsViewModel,
                    exportViewModel = exportViewModel,
                )
            }
        }

        composeTestRule.runOnIdle {
            itemsViewModel.addItem(
                ScannedItem(
                    id = "item-1",
                    category = ItemCategory.HOME_GOOD,
                    priceRange = 10.0 to 20.0,
                    confidence = 0.9f,
                    labelText = "Vintage Lamp",
                    timestamp = 1000L,
                ),
            )
        }

        composeTestRule
            .onNodeWithText("Vintage Lamp")
            .assertIsDisplayed()
    }

    private fun createExportViewModel(context: Context): ExportViewModel {
        val dedupeHelper = PerItemDedupeHelper()
        val photoManager = ItemPhotoManager(context, dedupeHelper)
        val bundleRepository = ExportBundleRepository(context, photoManager)
        val zipExporter = BundleZipExporter(context)
        val textExporter = PlainTextExporter(context)

        return ExportViewModel(context, bundleRepository, zipExporter, textExporter)
    }
}
