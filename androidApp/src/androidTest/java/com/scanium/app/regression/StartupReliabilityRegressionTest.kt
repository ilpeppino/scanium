package com.scanium.app.regression

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.scanium.app.MainActivity
import com.scanium.app.ScaniumApplication
import com.scanium.app.startup.StartupGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup Reliability Regression Tests
 *
 * Validates that the app starts reliably and reaches a usable state.
 * These tests serve as regression guards against startup crash-loops.
 *
 * Tests validate:
 * - Application initializes without throwing
 * - MainActivity reaches RESUMED state
 * - StartupGuard correctly records successful startup
 * - App survives fresh install scenario (cleared data)
 * - Telemetry and crash reporting initialize correctly
 */
@RunWith(AndroidJUnit4::class)
class StartupReliabilityRegressionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val STARTUP_DELAY_MS = 3000L
        private const val READY_STATE_TIMEOUT_MS = 5000L

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RegressionTestConfig.initialize()
        }
    }

    @Before
    fun setUp() {
        // Clear StartupGuard state before each test to simulate fresh start
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        StartupGuard.getInstance(context).reset()
    }

    /**
     * Core startup test: App launches and reaches RESUMED state without crash.
     *
     * This is the primary regression guard for the startup crash-loop bug.
     * If this test passes, the app successfully:
     * - Created Application instance
     * - Loaded locale settings (with fallback)
     * - Initialized crash reporting
     * - Rendered MainActivity
     */
    @Test
    fun testAppStartsSuccessfully() =
        runTest {
            // Wait for startup to complete
            delay(STARTUP_DELAY_MS)

            // Verify activity reached RESUMED state
            assertThat(composeTestRule.activityRule.scenario.state)
                .isEqualTo(Lifecycle.State.RESUMED)
        }

    /**
     * Validates StartupGuard correctly records a successful startup.
     *
     * After a successful startup, the crash counter should be reset
     * and safe mode should be disabled for the next launch.
     */
    @Test
    fun testStartupGuard_RecordsSuccess() =
        runTest {
            // Wait for startup and LaunchedEffect to run
            delay(STARTUP_DELAY_MS)

            // Get the StartupGuard instance
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val startupGuard = StartupGuard.getInstance(context)

            // Verify successful startup was recorded
            assertThat(startupGuard.consecutiveCrashes).isEqualTo(0)
            assertThat(startupGuard.isSafeMode).isFalse()
            assertThat(startupGuard.lastSuccessfulStartup).isGreaterThan(0L)
        }

    /**
     * Validates Application instance is properly initialized.
     *
     * Checks that all critical components are available after startup:
     * - Telemetry
     * - CrashPort
     * - DiagnosticsPort
     * - StartupGuard
     */
    @Test
    fun testApplicationComponents_Initialized() =
        runTest {
            // Wait for startup
            delay(STARTUP_DELAY_MS)

            // Get application instance
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val application = context.applicationContext as ScaniumApplication

            // Verify components are initialized (not throwing)
            assertThat(application.crashPort).isNotNull()
            assertThat(application.diagnosticsPort).isNotNull()
            assertThat(application.telemetry).isNotNull()
            assertThat(application.startupGuard).isNotNull()
        }

    /**
     * Validates app handles multiple rapid startups without entering safe mode.
     *
     * Simulates the scenario where a user opens the app multiple times
     * quickly (normal usage, not crash-loop).
     */
    @Test
    fun testMultipleSuccessfulStartups_NoSafeMode() =
        runTest {
            // Wait for first startup
            delay(STARTUP_DELAY_MS)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val startupGuard = StartupGuard.getInstance(context)

            // Record multiple successful startups
            repeat(5) {
                startupGuard.recordStartupAttempt()
                // Simulate successful startup by waiting briefly then recording success
                delay(100)
                startupGuard.recordStartupSuccess()
            }

            // Verify no safe mode triggered
            assertThat(startupGuard.isSafeMode).isFalse()
            assertThat(startupGuard.consecutiveCrashes).isEqualTo(0)
        }

    /**
     * Validates that Activity survives recreation (config change).
     *
     * This ensures that startup code paths are safe even when the Activity
     * is recreated due to configuration changes like rotation.
     */
    @Test
    fun testActivityRecreation_StartsSuccessfully() =
        runTest {
            // Wait for initial startup
            delay(STARTUP_DELAY_MS)

            // Trigger recreation (simulates rotation or config change)
            composeTestRule.activityRule.scenario.recreate()

            // Wait for recreation to complete
            delay(STARTUP_DELAY_MS)

            // Verify activity is in RESUMED state
            assertThat(composeTestRule.activityRule.scenario.state)
                .isEqualTo(Lifecycle.State.RESUMED)
        }

    /**
     * Validates that safe mode flag is properly checked during startup.
     *
     * This test simulates a crash-loop scenario and verifies the app
     * enters safe mode correctly.
     */
    @Test
    fun testSafeModeActivation_AfterCrashLoop() =
        runTest {
            // Wait for startup
            delay(STARTUP_DELAY_MS)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val startupGuard = StartupGuard.getInstance(context)

            // Reset to simulate fresh state
            startupGuard.reset()

            // Simulate crash loop: multiple startup attempts without success
            repeat(StartupGuard.CRASH_THRESHOLD) {
                startupGuard.recordStartupAttempt()
                // Don't call recordStartupSuccess - simulates crash
            }

            // Verify safe mode is now active
            assertThat(startupGuard.isSafeMode).isTrue()
            assertThat(startupGuard.consecutiveCrashes).isAtLeast(StartupGuard.CRASH_THRESHOLD)
        }

    /**
     * Validates that requesting safe mode exit works correctly.
     */
    @Test
    fun testSafeModeExit_RequestedSuccessfully() =
        runTest {
            delay(STARTUP_DELAY_MS)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val startupGuard = StartupGuard.getInstance(context)

            // Put into safe mode
            startupGuard.reset()
            repeat(StartupGuard.CRASH_THRESHOLD) {
                startupGuard.recordStartupAttempt()
            }
            assertThat(startupGuard.isSafeMode).isTrue()

            // Request exit
            startupGuard.requestExitSafeMode()

            // Verify safe mode is disabled
            assertThat(startupGuard.isSafeMode).isFalse()
            assertThat(startupGuard.consecutiveCrashes).isEqualTo(0)
        }

    /**
     * Validates diagnostics are available for debugging.
     */
    @Test
    fun testStartupDiagnostics_Available() =
        runTest {
            delay(STARTUP_DELAY_MS)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val startupGuard = StartupGuard.getInstance(context)

            val diagnostics = startupGuard.getDiagnostics()

            // Verify diagnostic keys are present
            assertThat(diagnostics).containsKey("safe_mode")
            assertThat(diagnostics).containsKey("consecutive_crashes")
            assertThat(diagnostics).containsKey("last_successful_startup")
            assertThat(diagnostics).containsKey("startup_timestamps")

            // Verify no secrets are leaked
            diagnostics.values.forEach { value ->
                assertThat(value).doesNotContain("api")
                assertThat(value).doesNotContain("key")
                assertThat(value).doesNotContain("token")
                assertThat(value).doesNotContain("secret")
            }
        }
}
