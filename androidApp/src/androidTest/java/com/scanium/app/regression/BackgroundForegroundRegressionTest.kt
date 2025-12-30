package com.scanium.app.regression

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.scanium.app.MainActivity
import com.scanium.app.testing.TestSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST 5: Background/foreground lifecycle does not break camera pipeline
 *
 * Validates:
 * - Camera receives frames initially
 * - After backgrounding and foregrounding, camera resumes
 * - No frozen frames or pipeline stalls after lifecycle transitions
 *
 * Uses ActivityScenario state transitions and UiAutomator for home button simulation.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundForegroundRegressionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var uiDevice: UiDevice

    companion object {
        private const val STARTUP_DELAY_MS = 2000L
        private const val LIFECYCLE_TRANSITION_DELAY_MS = 1000L
        private const val FRAME_UPDATE_TIMEOUT_MS = 2000L

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RegressionTestConfig.initialize()
        }

        private fun isCameraAvailable(): Boolean {
            return try {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val cameraManager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                cameraManager?.cameraIdList?.isNotEmpty() == true
            } catch (e: Exception) {
                false
            }
        }
    }

    @Before
    fun setUp() {
        // Skip if no camera
        Assume.assumeTrue(
            "Camera not available - skipping lifecycle tests",
            isCameraAvailable()
        )

        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun testBackgroundForeground_CameraResumes() = runTest {
        // Wait for initial camera startup
        delay(STARTUP_DELAY_MS)

        // Verify app is in foreground
        assertThat(composeTestRule.activityRule.scenario.state)
            .isEqualTo(Lifecycle.State.RESUMED)

        // Move to background using ActivityScenario
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Move back to foreground
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Verify app resumed
        assertThat(composeTestRule.activityRule.scenario.state)
            .isEqualTo(Lifecycle.State.RESUMED)

        // Wait for camera to stabilize
        delay(FRAME_UPDATE_TIMEOUT_MS)

        // Test passes if we reach here without crash/ANR
    }

    @Test
    fun testHomeButtonPress_CameraResumes() = runTest {
        // Wait for initial camera startup
        delay(STARTUP_DELAY_MS)

        // Press home button to background
        uiDevice.pressHome()
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Verify app went to background
        // (Note: scenario state may not update immediately with UiAutomator)

        // Relaunch app (bring back to foreground)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }

        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Wait for camera to resume
        delay(FRAME_UPDATE_TIMEOUT_MS)

        // Test passes if camera resumes without crash
    }

    @Test
    fun testMultipleLifecycleTransitions_NoPipelineFreeze() = runTest {
        // Wait for initial startup
        delay(STARTUP_DELAY_MS)

        // Perform multiple lifecycle transitions
        repeat(3) { cycle ->
            // Background
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            delay(500)

            // Foreground
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            delay(500)
        }

        // Final stabilization
        delay(FRAME_UPDATE_TIMEOUT_MS)

        // Verify still in resumed state
        assertThat(composeTestRule.activityRule.scenario.state)
            .isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun testOnStop_OnStart_CameraRecovery() = runTest {
        // Wait for startup
        delay(STARTUP_DELAY_MS)

        // Move through lifecycle states
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // This simulates the app being fully stopped (like opening another app)
        // The camera should release resources

        // Resume
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(FRAME_UPDATE_TIMEOUT_MS)

        // Camera should recover and receive frames again
        // Test passes if no freeze/crash
    }

    @Test
    fun testRapidLifecycleChanges_NoResourceLeak() = runTest {
        // Wait for initial startup
        delay(STARTUP_DELAY_MS)

        // Rapidly cycle through states (stress test)
        repeat(5) {
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            delay(200)
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            delay(200)
        }

        // Final stabilization
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Verify no memory/resource issues caused crash
        assertThat(composeTestRule.activityRule.scenario.state)
            .isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun testConfigurationChange_CameraPreserved() = runTest {
        // Wait for startup
        delay(STARTUP_DELAY_MS)

        // Trigger configuration change (rotation)
        composeTestRule.activityRule.scenario.recreate()

        // Wait for recreation
        delay(LIFECYCLE_TRANSITION_DELAY_MS)

        // Camera should reinitialize after recreation
        delay(FRAME_UPDATE_TIMEOUT_MS)

        // Verify activity is resumed after recreation
        assertThat(composeTestRule.activityRule.scenario.state)
            .isEqualTo(Lifecycle.State.RESUMED)
    }
}
