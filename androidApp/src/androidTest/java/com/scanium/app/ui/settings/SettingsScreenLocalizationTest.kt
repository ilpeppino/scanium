package com.scanium.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.R
import com.scanium.app.ui.theme.ScaniumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsScreenLocalizationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun storageAndExport_isTranslated_inItalian() {
        // Force the locale for this test
        Locale.setDefault(Locale.ITALIAN)
        val context = ApplicationProvider.getApplicationContext<Context>()

        // A real view model is used here, matching patterns in other tests.
        val viewModel = SettingsViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            ScaniumTheme {
                SettingsHomeScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onGeneralClick = {},
                    onCameraClick = {},
                    onAssistantClick = {},
                    onFeedbackClick = {},
                    onStorageClick = {},
                    onPrivacyClick = {},
                    onDeveloperClick = {},
                )
            }
        }

        val expectedText = context.getString(R.string.settings_category_storage_title)
        val expectedSubtitle = context.getString(R.string.settings_category_storage_desc)

        // Assert that both the title and subtitle are displayed and translated
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
    }
}
