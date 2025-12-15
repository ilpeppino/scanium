package com.scanium.app.selling.data

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MockEbayConfigManagerTest {

    @Before
    fun setup() {
        // Reset to defaults before each test
        MockEbayConfigManager.resetToDefaults()
    }

    @Test
    fun `initial config has default values`() {
        val config = MockEbayConfigManager.config.value

        assertThat(config.simulateNetworkDelay).isTrue()
        assertThat(config.minDelayMs).isEqualTo(400)
        assertThat(config.maxDelayMs).isEqualTo(1200)
        assertThat(config.failureMode).isEqualTo(MockFailureMode.NONE)
        assertThat(config.failureRate).isEqualTo(0.0)
    }

    @Test
    fun `setNetworkDelayEnabled updates config`() {
        MockEbayConfigManager.setNetworkDelayEnabled(false)

        val config = MockEbayConfigManager.config.value
        assertThat(config.simulateNetworkDelay).isFalse()
    }

    @Test
    fun `setFailureMode updates config`() {
        MockEbayConfigManager.setFailureMode(MockFailureMode.NETWORK_TIMEOUT, 0.5)

        val config = MockEbayConfigManager.config.value
        assertThat(config.failureMode).isEqualTo(MockFailureMode.NETWORK_TIMEOUT)
        assertThat(config.failureRate).isEqualTo(0.5)
    }

    @Test
    fun `setFailureMode clamps rate to valid range`() {
        MockEbayConfigManager.setFailureMode(MockFailureMode.RANDOM, 1.5)

        val config = MockEbayConfigManager.config.value
        assertThat(config.failureRate).isEqualTo(1.0)

        MockEbayConfigManager.setFailureMode(MockFailureMode.RANDOM, -0.5)

        val config2 = MockEbayConfigManager.config.value
        assertThat(config2.failureRate).isEqualTo(0.0)
    }

    @Test
    fun `resetToDefaults restores initial state`() {
        // Change config
        MockEbayConfigManager.setNetworkDelayEnabled(false)
        MockEbayConfigManager.setFailureMode(MockFailureMode.VALIDATION_ERROR, 0.8)

        // Reset
        MockEbayConfigManager.resetToDefaults()

        // Verify defaults
        val config = MockEbayConfigManager.config.value
        assertThat(config.simulateNetworkDelay).isTrue()
        assertThat(config.failureMode).isEqualTo(MockFailureMode.NONE)
        assertThat(config.failureRate).isEqualTo(0.0)
    }

    @Test
    fun `updateConfig replaces entire config`() {
        val newConfig = MockEbayConfig(
            simulateNetworkDelay = false,
            minDelayMs = 100,
            maxDelayMs = 500,
            failureMode = MockFailureMode.IMAGE_TOO_SMALL,
            failureRate = 0.3
        )

        MockEbayConfigManager.updateConfig(newConfig)

        val config = MockEbayConfigManager.config.value
        assertThat(config).isEqualTo(newConfig)
    }
}
