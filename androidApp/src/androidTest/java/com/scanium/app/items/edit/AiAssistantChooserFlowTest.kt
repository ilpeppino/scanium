package com.scanium.app.items.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem
import com.scanium.app.items.createAndroidTestItemsViewModel
import com.scanium.app.ui.theme.ScaniumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiAssistantChooserFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aiButton_showsChooser() {
        val itemsViewModel = createAndroidTestItemsViewModel()

        composeTestRule.setContent {
            ScaniumTheme {
                EditItemScreenV3(
                    itemId = "item-1",
                    onBack = {},
                    onAddPhotos = {},
                    onAiGenerate = {},
                    itemsViewModel = itemsViewModel,
                    exportAssistantViewModelFactory = null,
                    pricingAssistantViewModelFactory = null,
                )
            }
        }

        composeTestRule.runOnIdle {
            itemsViewModel.addItem(
                ScannedItem(
                    id = "item-1",
                    category = ItemCategory.HOME_GOOD,
                    priceRange = 10.0 to 20.0,
                    labelText = "Lamp",
                    timestamp = 1000L,
                ),
            )
        }

        composeTestRule.onNodeWithTag("editItem_aiButton").performClick()
        composeTestRule.onNodeWithTag("aiChooser_price").assertIsDisplayed()
        composeTestRule.onNodeWithTag("aiChooser_listing").assertIsDisplayed()
    }

    @Test
    fun chooserSelection_routesToPricingSheet() {
        composeTestRule.setContent {
            ScaniumTheme {
                AiChooserTestHost()
            }
        }

        composeTestRule.onNodeWithTag("aiChooser_price_action").performClick()
        composeTestRule.onNodeWithTag("pricingAssistant_sheet").assertIsDisplayed()
    }

    @Test
    fun chooserSelection_routesToExportSheet() {
        composeTestRule.setContent {
            ScaniumTheme {
                AiChooserTestHost()
            }
        }

        composeTestRule.onNodeWithTag("aiChooser_listing_action").performClick()
        composeTestRule.onNodeWithTag("exportAssistant_sheet").assertIsDisplayed()
    }
}

@Composable
private fun AiChooserTestHost() {
    val showChooser = remember { mutableStateOf(true) }
    val showPricing = remember { mutableStateOf(false) }
    val showExport = remember { mutableStateOf(false) }

    if (showChooser.value) {
        AiAssistantChooserSheet(
            onDismiss = { showChooser.value = false },
            onChoosePrice = {
                showChooser.value = false
                showPricing.value = true
            },
            onChooseListing = {
                showChooser.value = false
                showExport.value = true
            },
            highlightPrice = true,
        )
    }

    if (showPricing.value) {
        androidx.compose.material3.Text(
            text = "Pricing Assistant",
            modifier = androidx.compose.ui.Modifier.testTag("pricingAssistant_sheet"),
        )
    }

    if (showExport.value) {
        androidx.compose.material3.Text(
            text = "Export Assistant",
            modifier = androidx.compose.ui.Modifier.testTag("exportAssistant_sheet"),
        )
    }
}
