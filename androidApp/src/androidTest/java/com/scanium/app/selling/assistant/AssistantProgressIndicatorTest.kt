package com.scanium.app.selling.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ui.theme.ScaniumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantProgressIndicatorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sendingProgressShowsSendingLabel() {
        composeTestRule.setContent {
            ScaniumTheme {
                ProgressIndicatorSection(
                    progress = AssistantRequestProgress.Sending(),
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Sending...").assertIsDisplayed()
    }

    @Test
    fun visionProgressShowsImageCount() {
        composeTestRule.setContent {
            ScaniumTheme {
                ProgressIndicatorSection(
                    progress = AssistantRequestProgress.ExtractingVision(imageCount = 2),
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Analyzing 2 images...").assertIsDisplayed()
    }

    @Test
    fun validationErrorShowsMessage() {
        composeTestRule.setContent {
            ScaniumTheme {
                ProgressIndicatorSection(
                    progress = AssistantRequestProgress.ErrorValidation(message = "Invalid request"),
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Invalid request").assertIsDisplayed()
    }
}
