package com.scanium.app.items.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.pricing.PricingMissingField
import com.scanium.app.pricing.PricingUiState
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceEstimateCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readyState_enablesGetEstimateButton() {
        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.Ready,
                missingFields = emptySet(),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule
            .onNodeWithText("Get Price Estimate")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun insufficientData_disablesGetEstimateButton() {
        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.InsufficientData,
                missingFields = setOf(PricingMissingField.BRAND, PricingMissingField.MODEL),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule
            .onNodeWithText("Get Price Estimate")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun successState_showsRangeAndUseButton() {
        val insights =
            PricingInsights(
                status = "OK",
                countryCode = "NL",
                marketplacesUsed = emptyList(),
                range = PriceRange(low = 75.0, high = 120.0, currency = "EUR"),
                confidence = PricingConfidence.HIGH,
            )

        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.Success(insights, isStale = false),
                missingFields = emptySet(),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Estimated Resale Price").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use €98", substring = true).assertExists()
    }
}
