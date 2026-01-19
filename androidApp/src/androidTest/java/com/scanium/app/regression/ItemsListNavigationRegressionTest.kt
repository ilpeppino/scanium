package com.scanium.app.regression

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for crash when navigating from camera to items list after taking a picture.
 *
 * Bug: ItemsListScreen was using `viewModel()` instead of `hiltViewModel()` for ExportViewModel,
 * causing a crash because ExportViewModel is a @HiltViewModel that requires dependency injection.
 *
 * Fix: Changed `viewModel()` to `hiltViewModel()` in ItemsListScreen.kt line 84.
 *
 * This test verifies that:
 * 1. The items list screen can be opened without crashing
 * 2. The ExportViewModel is properly injected via Hilt
 *
 * Related fix: ItemsListScreen.kt - changed ExportViewModel parameter from viewModel() to hiltViewModel()
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ItemsListNavigationRegressionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Regression test: Verify items list screen opens without crashing.
     *
     * This test catches the bug where using viewModel() instead of hiltViewModel()
     * for @HiltViewModel classes causes a crash due to missing dependency injection.
     *
     * Steps:
     * 1. App starts on camera screen
     * 2. Tap the items list icon (should navigate to items list)
     * 3. Verify items list screen is displayed (no crash)
     */
    @Test
    fun whenTappingItemsListIcon_thenItemsListScreenOpensWithoutCrash() {
        // Wait for the camera screen to load
        composeTestRule.waitForIdle()

        // Find and tap the items list button
        // The button has content description "Items" or similar
        composeTestRule
            .onNodeWithContentDescription("Items", substring = true, ignoreCase = true)
            .performClick()

        // Wait for navigation to complete
        composeTestRule.waitForIdle()

        // Verify the items list screen is displayed (proves no crash occurred)
        // The screen title should be "Detected Items" or similar
        composeTestRule
            .onNodeWithText("Detected Items", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * Regression test: Verify items list screen opens after adding items.
     *
     * This specifically tests the scenario where:
     * 1. User takes a picture (items are added)
     * 2. User taps items list icon
     * 3. Screen should open and display items (no crash)
     *
     * Note: Since we can't easily simulate camera capture in instrumented tests,
     * we just verify the empty state works, which exercises the same code path
     * for ExportViewModel initialization.
     */
    @Test
    fun whenItemsListOpened_thenEmptyStateDisplayedWithoutCrash() {
        // Wait for the camera screen to load
        composeTestRule.waitForIdle()

        // Navigate to items list
        composeTestRule
            .onNodeWithContentDescription("Items", substring = true, ignoreCase = true)
            .performClick()

        composeTestRule.waitForIdle()

        // Verify empty state is displayed (this proves ExportViewModel initialized correctly)
        // The empty state shows "No items detected"
        composeTestRule
            .onNodeWithText("No items", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * Regression test: Navigate back and forth between camera and items list.
     *
     * This test verifies that:
     * 1. Navigation to items list works
     * 2. Navigation back to camera works
     * 3. Repeat navigation still works (no state corruption)
     */
    @Test
    fun whenNavigatingBackAndForth_thenNocrash() {
        composeTestRule.waitForIdle()

        // First navigation to items list
        composeTestRule
            .onNodeWithContentDescription("Items", substring = true, ignoreCase = true)
            .performClick()

        composeTestRule.waitForIdle()

        // Navigate back to camera
        composeTestRule
            .onNodeWithContentDescription("Back", substring = true, ignoreCase = true)
            .performClick()

        composeTestRule.waitForIdle()

        // Second navigation to items list (tests that state is properly reset)
        composeTestRule
            .onNodeWithContentDescription("Items", substring = true, ignoreCase = true)
            .performClick()

        composeTestRule.waitForIdle()

        // Verify items list is still accessible
        composeTestRule
            .onNodeWithText("Detected Items", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}
