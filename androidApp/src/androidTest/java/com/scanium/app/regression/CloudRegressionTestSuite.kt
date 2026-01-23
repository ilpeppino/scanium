package com.scanium.app.regression

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Android 15 Cloud Regression Test Suite
 *
 * This suite runs all regression tests that require cloud backend connectivity.
 * Tests are designed to:
 * - Validate camera lifecycle across navigation and backgrounding
 * - Verify cloud classification works end-to-end
 * - Ensure share/export produces valid attachments
 *
 * ## Prerequisites
 * - Cloud backend must be reachable (tests skip if not)
 * - Physical device recommended for camera tests (emulator may work but less reliable)
 * - Android 15 target but compatible with minSdk 24+
 *
 * ## Running the Suite
 * ```
 * ./gradlew :androidApp:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.SCANIUM_BASE_URL=https://api.example.com \
 *     -Pandroid.testInstrumentationRunnerArguments.SCANIUM_API_KEY=your-api-key
 * ```
 *
 * Or run individual test classes via Android Studio.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    BackendHealthRegressionTest::class,
    CameraLifecycleRegressionTest::class,
    CloudClassificationRegressionTest::class,
    ShareExportRegressionTest::class,
    BackgroundForegroundRegressionTest::class,
)
class CloudRegressionTestSuite
