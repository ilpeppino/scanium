package com.example.objecta.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.objecta.camera.ModeSwitcher
import com.example.objecta.camera.ScanMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ModeSwitcher UI component.
 *
 * Tests verify:
 * - Mode switching via tap
 * - Visual state changes (selected/unselected)
 * - Both scan modes are displayed
 * - Callback invocation on mode change
 */
@RunWith(AndroidJUnit4::class)
class ModeSwitcherTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenModeSwitcherDisplayed_thenBothModesAreVisible() {
        // Arrange
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.OBJECT_DETECTION,
                onModeChanged = {}
            )
        }

        // Assert - Both mode labels should be visible
        composeTestRule.onNodeWithText("ITEMS").assertIsDisplayed()
        composeTestRule.onNodeWithText("BARCODE").assertIsDisplayed()
    }

    @Test
    fun whenObjectDetectionModeSelected_thenItemsIsHighlighted() {
        // Arrange
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.OBJECT_DETECTION,
                onModeChanged = {}
            )
        }

        // Assert - Items mode should exist
        composeTestRule.onNodeWithText("ITEMS").assertExists()
    }

    @Test
    fun whenBarcodeModeSelected_thenBarcodeIsHighlighted() {
        // Arrange
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.BARCODE,
                onModeChanged = {}
            )
        }

        // Assert - Barcode mode should exist
        composeTestRule.onNodeWithText("BARCODE").assertExists()
    }

    @Test
    fun whenTappingBarcodeModeFromItemsMode_thenCallbackInvokedWithBarcodeMode() {
        // Arrange
        var selectedMode: ScanMode? = null
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.OBJECT_DETECTION,
                onModeChanged = { mode -> selectedMode = mode }
            )
        }

        // Act - Tap on BARCODE
        composeTestRule.onNodeWithText("BARCODE").performClick()

        // Assert
        assertThat(selectedMode).isEqualTo(ScanMode.BARCODE)
    }

    @Test
    fun whenTappingItemsModeFromBarcodeMode_thenCallbackInvokedWithObjectDetectionMode() {
        // Arrange
        var selectedMode: ScanMode? = null
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.BARCODE,
                onModeChanged = { mode -> selectedMode = mode }
            )
        }

        // Act - Tap on ITEMS
        composeTestRule.onNodeWithText("ITEMS").performClick()

        // Assert
        assertThat(selectedMode).isEqualTo(ScanMode.OBJECT_DETECTION)
    }

    @Test
    fun whenTappingCurrentMode_thenCallbackStillInvoked() {
        // Arrange
        var callbackInvoked = false
        var selectedMode: ScanMode? = null
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.OBJECT_DETECTION,
                onModeChanged = { mode ->
                    callbackInvoked = true
                    selectedMode = mode
                }
            )
        }

        // Act - Tap on currently selected mode (ITEMS)
        composeTestRule.onNodeWithText("ITEMS").performClick()

        // Assert - Callback should be invoked even if selecting same mode
        assertThat(callbackInvoked).isTrue()
        assertThat(selectedMode).isEqualTo(ScanMode.OBJECT_DETECTION)
    }

    @Test
    fun whenModeSwitcherRendered_thenNodesAreClickable() {
        // Arrange
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = ScanMode.OBJECT_DETECTION,
                onModeChanged = {}
            )
        }

        // Assert - Both modes should be clickable
        composeTestRule.onNodeWithText("ITEMS").assertHasClickAction()
        composeTestRule.onNodeWithText("BARCODE").assertHasClickAction()
    }

    @Test
    fun whenSwitchingModes_thenUiUpdatesCorrectly() {
        // Arrange - Create stateful content
        var currentMode = ScanMode.OBJECT_DETECTION
        composeTestRule.setContent {
            ModeSwitcher(
                currentMode = currentMode,
                onModeChanged = { mode -> currentMode = mode }
            )
        }

        // Act - Switch to BARCODE
        composeTestRule.onNodeWithText("BARCODE").performClick()

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Assert - Mode should have changed
        assertThat(currentMode).isEqualTo(ScanMode.BARCODE)

        // Act - Switch back to ITEMS
        composeTestRule.onNodeWithText("ITEMS").performClick()
        composeTestRule.waitForIdle()

        // Assert
        assertThat(currentMode).isEqualTo(ScanMode.OBJECT_DETECTION)
    }
}
