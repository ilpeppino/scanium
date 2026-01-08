package com.scanium.app.camera

import android.util.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.testing.TestSemantics
import com.scanium.app.ui.motion.MotionEnhancedOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the detection overlay visibility gating based on the showDetectionBoxes setting.
 *
 * Verifies:
 * - Overlay is visible when showDetectionBoxes is true
 * - Overlay is not present when showDetectionBoxes is false
 * - Overlay visibility can be toggled dynamically
 */
@RunWith(AndroidJUnit4::class)
class DetectionOverlayGatingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenShowDetectionBoxesTrue_overlayExists() {
        composeTestRule.setContent {
            TestOverlayGating(showDetectionBoxes = true)
        }

        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertExists()
    }

    @Test
    fun whenShowDetectionBoxesFalse_overlayDoesNotExist() {
        composeTestRule.setContent {
            TestOverlayGating(showDetectionBoxes = false)
        }

        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertDoesNotExist()
    }

    @Test
    fun whenToggleFromTrueToFalse_overlayDisappears() {
        var showDetectionBoxes by mutableStateOf(true)

        composeTestRule.setContent {
            TestOverlayGating(showDetectionBoxes = showDetectionBoxes)
        }

        // Initially visible
        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertExists()

        // Toggle off
        showDetectionBoxes = false
        composeTestRule.waitForIdle()

        // Should no longer exist
        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertDoesNotExist()
    }

    @Test
    fun whenToggleFromFalseToTrue_overlayAppears() {
        var showDetectionBoxes by mutableStateOf(false)

        composeTestRule.setContent {
            TestOverlayGating(showDetectionBoxes = showDetectionBoxes)
        }

        // Initially not visible
        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertDoesNotExist()

        // Toggle on
        showDetectionBoxes = true
        composeTestRule.waitForIdle()

        // Should now exist
        composeTestRule.onNodeWithTag(TestSemantics.DETECTION_OVERLAY).assertExists()
    }

    /**
     * Test composable that mimics CameraScreen's overlay gating pattern.
     * This allows us to test the conditional rendering logic in isolation
     * without requiring full camera setup.
     */
    @Composable
    private fun TestOverlayGating(showDetectionBoxes: Boolean) {
        val imageSize = Size(1280, 720)
        val previewSize = Size(1080, 1920)

        Box(modifier = Modifier.fillMaxSize()) {
            // Same gating pattern as CameraScreen.kt
            if (showDetectionBoxes && previewSize.width > 0 && previewSize.height > 0) {
                MotionEnhancedOverlay(
                    detections = emptyList(),
                    imageSize = imageSize,
                    previewSize = previewSize,
                    rotationDegrees = 90,
                    showGeometryDebug = false,
                    overlayAccuracyStep = 0,
                    modifier = Modifier.testTag(TestSemantics.DETECTION_OVERLAY),
                )
            }
        }
    }
}
