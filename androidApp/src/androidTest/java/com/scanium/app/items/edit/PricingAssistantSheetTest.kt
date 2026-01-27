package com.scanium.app.items.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.R
import com.scanium.app.pricing.PricingUiState
import com.scanium.shared.core.models.items.ItemCondition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PricingAssistantSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun introStep_showsTitleAndContinue() {
        val state =
            PricingAssistantState(
                steps = listOf(PricingAssistantStep.INTRO, PricingAssistantStep.CONFIRM),
                currentStepIndex = 0,
                brand = "Apple",
                productType = "electronics_laptop",
                model = "MacBook Pro",
                condition = ItemCondition.GOOD,
            )

        composeTestRule.setContent {
            PricingAssistantContent(
                state = state,
                onDismiss = {},
                onNext = {},
                onBack = {},
                onUpdateVariant = { _, _ -> },
                onToggleCompleteness = {},
                onUpdateIdentifier = {},
                onSubmit = {},
                onUsePrice = {},
                onOpenListingAssistant = {},
            )
        }

        composeTestRule.onNodeWithText("Pricing Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun confirmStep_showsEstimateCardAndListingAssistantLink() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val state =
            PricingAssistantState(
                steps = listOf(PricingAssistantStep.INTRO, PricingAssistantStep.CONFIRM),
                currentStepIndex = 1,
                brand = "Apple",
                productType = "electronics_laptop",
                model = "MacBook Pro",
                condition = ItemCondition.GOOD,
                pricingUiState = PricingUiState.Ready,
            )

        composeTestRule.setContent {
            PricingAssistantContent(
                state = state,
                onDismiss = {},
                onNext = {},
                onBack = {},
                onUpdateVariant = { _, _ -> },
                onToggleCompleteness = {},
                onUpdateIdentifier = {},
                onSubmit = {},
                onUsePrice = {},
                onOpenListingAssistant = {},
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.pricing_button_get_estimate)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Listing Assistant").assertIsDisplayed()
    }
}
