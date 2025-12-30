package com.scanium.app.regression

import androidx.test.platform.app.InstrumentationRegistry
import com.scanium.app.testing.TestConfigOverride

/**
 * Configuration helper for regression tests.
 *
 * Initializes test configuration from instrumentation arguments
 * and provides common setup/teardown utilities.
 */
object RegressionTestConfig {
    private var initialized = false

    /**
     * Initialize test configuration from instrumentation arguments.
     *
     * Call this once at the start of your test class or in a @BeforeClass method:
     * ```kotlin
     * @BeforeClass
     * @JvmStatic
     * fun setUpClass() {
     *     RegressionTestConfig.initialize()
     * }
     * ```
     */
    fun initialize() {
        if (initialized) return

        val arguments = InstrumentationRegistry.getArguments()
        TestConfigOverride.initFromArguments(arguments)
        initialized = true
    }

    /**
     * Check if cloud mode is configured and available.
     */
    fun isCloudModeAvailable(): Boolean {
        initialize()
        return TestConfigOverride.hasCloudConfig()
    }

    /**
     * Get the effective backend base URL.
     */
    fun getBaseUrl(): String {
        initialize()
        return TestConfigOverride.getEffectiveBaseUrl()
    }

    /**
     * Reset configuration (for test isolation).
     */
    fun reset() {
        TestConfigOverride.reset()
        initialized = false
    }
}
