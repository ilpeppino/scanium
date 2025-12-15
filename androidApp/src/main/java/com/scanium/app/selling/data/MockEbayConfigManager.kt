package com.scanium.app.selling.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for MockEbayApi configuration.
 *
 * Provides a centralized way to configure mock behavior for testing and demos.
 * Changes to the configuration take effect on the next API call.
 */
object MockEbayConfigManager {
    private val _config = MutableStateFlow(
        MockEbayConfig(
            simulateNetworkDelay = true,
            minDelayMs = 400,
            maxDelayMs = 1200,
            failureMode = MockFailureMode.NONE,
            failureRate = 0.0
        )
    )

    val config: StateFlow<MockEbayConfig> = _config.asStateFlow()

    /**
     * Updates the current configuration.
     */
    fun updateConfig(newConfig: MockEbayConfig) {
        _config.value = newConfig
    }

    /**
     * Enables or disables network delay simulation.
     */
    fun setNetworkDelayEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(simulateNetworkDelay = enabled)
    }

    /**
     * Sets the failure mode and rate.
     *
     * @param mode The type of failure to simulate
     * @param rate Probability of failure (0.0 = never, 1.0 = always)
     */
    fun setFailureMode(mode: MockFailureMode, rate: Double = 0.0) {
        _config.value = _config.value.copy(
            failureMode = mode,
            failureRate = rate.coerceIn(0.0, 1.0)
        )
    }

    /**
     * Resets configuration to defaults (no failures, normal delays).
     */
    fun resetToDefaults() {
        _config.value = MockEbayConfig(
            simulateNetworkDelay = true,
            minDelayMs = 400,
            maxDelayMs = 1200,
            failureMode = MockFailureMode.NONE,
            failureRate = 0.0
        )
    }
}
