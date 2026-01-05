package com.scanium.app.selling.assistant.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for AssistantHttpConfig to ensure all configurations
 * use the unified timeout policy.
 */
class AssistantHttpConfigTest {

    // ==================== Default Configuration Tests ====================

    @Test
    fun `DEFAULT config has correct production timeouts`() {
        val config = AssistantHttpConfig.DEFAULT

        assertThat(config.connectTimeoutSeconds).isEqualTo(15L)
        assertThat(config.readTimeoutSeconds).isEqualTo(60L)
        assertThat(config.writeTimeoutSeconds).isEqualTo(30L)
        assertThat(config.callTimeoutSeconds).isEqualTo(75L)
        assertThat(config.retryCount).isEqualTo(1)
    }

    @Test
    fun `DEFAULT config matches documented production defaults`() {
        // These values are documented in the class and in the networking policy doc
        // This test ensures they don't drift from documentation
        val config = AssistantHttpConfig.DEFAULT

        assertThat(config.connectTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS)
        assertThat(config.readTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_READ_TIMEOUT_SECONDS)
        assertThat(config.writeTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_WRITE_TIMEOUT_SECONDS)
        assertThat(config.callTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_CALL_TIMEOUT_SECONDS)
        assertThat(config.retryCount).isEqualTo(AssistantHttpConfig.DEFAULT_RETRY_COUNT)
    }

    // ==================== Preflight Configuration Tests ====================

    @Test
    fun `PREFLIGHT config has reasonable timeouts to avoid false negatives`() {
        val config = AssistantHttpConfig.PREFLIGHT

        // Preflight should be fast enough for health checks but not so tight that it falsely fails
        assertThat(config.connectTimeoutSeconds).isEqualTo(5L)
        assertThat(config.readTimeoutSeconds).isEqualTo(8L)
        assertThat(config.writeTimeoutSeconds).isEqualTo(5L)
        assertThat(config.callTimeoutSeconds).isEqualTo(10L)
        // No retries for preflight - it's just a health check
        assertThat(config.retryCount).isEqualTo(0)
    }

    @Test
    fun `PREFLIGHT config is faster than DEFAULT`() {
        val preflight = AssistantHttpConfig.PREFLIGHT
        val default = AssistantHttpConfig.DEFAULT

        assertThat(preflight.connectTimeoutSeconds).isLessThan(default.connectTimeoutSeconds)
        assertThat(preflight.readTimeoutSeconds).isLessThan(default.readTimeoutSeconds)
        assertThat(preflight.callTimeoutSeconds).isLessThan(default.callTimeoutSeconds)
    }

    // ==================== Warmup Configuration Tests ====================

    @Test
    fun `WARMUP config has moderate timeouts`() {
        val config = AssistantHttpConfig.WARMUP

        assertThat(config.connectTimeoutSeconds).isEqualTo(5L)
        assertThat(config.readTimeoutSeconds).isEqualTo(10L)
        assertThat(config.writeTimeoutSeconds).isEqualTo(5L)
        assertThat(config.callTimeoutSeconds).isEqualTo(15L)
        // No retries for warmup
        assertThat(config.retryCount).isEqualTo(0)
    }

    @Test
    fun `WARMUP config is between PREFLIGHT and DEFAULT in strictness`() {
        val preflight = AssistantHttpConfig.PREFLIGHT
        val warmup = AssistantHttpConfig.WARMUP
        val default = AssistantHttpConfig.DEFAULT

        // Warmup read timeout should be between preflight and default
        assertThat(warmup.readTimeoutSeconds).isGreaterThan(preflight.readTimeoutSeconds)
        assertThat(warmup.readTimeoutSeconds).isLessThan(default.readTimeoutSeconds)

        // Warmup call timeout should be between preflight and default
        assertThat(warmup.callTimeoutSeconds).isGreaterThan(preflight.callTimeoutSeconds)
        assertThat(warmup.callTimeoutSeconds).isLessThan(default.callTimeoutSeconds)

        // Connect timeout can be equal to preflight (both 5s) but should be less than default
        assertThat(warmup.connectTimeoutSeconds).isAtMost(default.connectTimeoutSeconds)
    }

    // ==================== Test Configuration Tests ====================

    @Test
    fun `TEST config has short timeouts for fast test execution`() {
        val config = AssistantHttpConfig.TEST

        assertThat(config.connectTimeoutSeconds).isEqualTo(5L)
        assertThat(config.readTimeoutSeconds).isEqualTo(5L)
        assertThat(config.writeTimeoutSeconds).isEqualTo(5L)
        assertThat(config.callTimeoutSeconds).isEqualTo(10L)
        // Tests should control retry behavior explicitly
        assertThat(config.retryCount).isEqualTo(0)
    }

    // ==================== Custom Configuration Tests ====================

    @Test
    fun `custom config can override individual values`() {
        val custom = AssistantHttpConfig(
            connectTimeoutSeconds = 20L,
            readTimeoutSeconds = 120L,
        )

        assertThat(custom.connectTimeoutSeconds).isEqualTo(20L)
        assertThat(custom.readTimeoutSeconds).isEqualTo(120L)
        // Other values should use defaults
        assertThat(custom.writeTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_WRITE_TIMEOUT_SECONDS)
        assertThat(custom.callTimeoutSeconds).isEqualTo(AssistantHttpConfig.DEFAULT_CALL_TIMEOUT_SECONDS)
        assertThat(custom.retryCount).isEqualTo(AssistantHttpConfig.DEFAULT_RETRY_COUNT)
    }

    @Test
    fun `custom config can disable retries`() {
        val noRetry = AssistantHttpConfig(retryCount = 0)
        assertThat(noRetry.retryCount).isEqualTo(0)
    }

    @Test
    fun `custom config can increase retries`() {
        val moreRetries = AssistantHttpConfig(retryCount = 3)
        assertThat(moreRetries.retryCount).isEqualTo(3)
    }

    // ==================== Logging Tests ====================

    @Test
    fun `toLogString includes all timeout values`() {
        val config = AssistantHttpConfig.DEFAULT
        val logString = config.toLogString()

        assertThat(logString).contains("connect=15s")
        assertThat(logString).contains("read=60s")
        assertThat(logString).contains("write=30s")
        assertThat(logString).contains("call=75s")
        assertThat(logString).contains("retries=1")
    }

    @Test
    fun `toLogString format is consistent`() {
        val preflight = AssistantHttpConfig.PREFLIGHT.toLogString()
        val warmup = AssistantHttpConfig.WARMUP.toLogString()
        val default = AssistantHttpConfig.DEFAULT.toLogString()

        // All should follow the same format
        listOf(preflight, warmup, default).forEach { logString ->
            assertThat(logString).startsWith("AssistantHttpConfig(")
            assertThat(logString).endsWith(")")
            assertThat(logString).contains("connect=")
            assertThat(logString).contains("read=")
            assertThat(logString).contains("write=")
            assertThat(logString).contains("call=")
            assertThat(logString).contains("retries=")
        }
    }

    // ==================== Data Class Behavior Tests ====================

    @Test
    fun `config equality works correctly`() {
        val config1 = AssistantHttpConfig(connectTimeoutSeconds = 10L, readTimeoutSeconds = 20L)
        val config2 = AssistantHttpConfig(connectTimeoutSeconds = 10L, readTimeoutSeconds = 20L)
        val config3 = AssistantHttpConfig(connectTimeoutSeconds = 10L, readTimeoutSeconds = 30L)

        assertThat(config1).isEqualTo(config2)
        assertThat(config1).isNotEqualTo(config3)
    }

    @Test
    fun `config copy works correctly`() {
        val original = AssistantHttpConfig.DEFAULT
        val modified = original.copy(readTimeoutSeconds = 120L)

        assertThat(modified.connectTimeoutSeconds).isEqualTo(original.connectTimeoutSeconds)
        assertThat(modified.readTimeoutSeconds).isEqualTo(120L)
        assertThat(modified.writeTimeoutSeconds).isEqualTo(original.writeTimeoutSeconds)
        assertThat(modified.callTimeoutSeconds).isEqualTo(original.callTimeoutSeconds)
    }

    // ==================== Invariant Tests ====================

    @Test
    fun `call timeout is greater than or equal to read timeout in all predefined configs`() {
        // Call timeout should encompass read timeout plus some overhead
        listOf(
            AssistantHttpConfig.DEFAULT,
            AssistantHttpConfig.PREFLIGHT,
            AssistantHttpConfig.WARMUP,
            AssistantHttpConfig.TEST,
        ).forEach { config ->
            assertThat(config.callTimeoutSeconds)
                .isAtLeast(config.readTimeoutSeconds)
        }
    }

    @Test
    fun `all predefined configs have non-negative timeouts`() {
        listOf(
            AssistantHttpConfig.DEFAULT,
            AssistantHttpConfig.PREFLIGHT,
            AssistantHttpConfig.WARMUP,
            AssistantHttpConfig.TEST,
        ).forEach { config ->
            assertThat(config.connectTimeoutSeconds).isAtLeast(0L)
            assertThat(config.readTimeoutSeconds).isAtLeast(0L)
            assertThat(config.writeTimeoutSeconds).isAtLeast(0L)
            assertThat(config.callTimeoutSeconds).isAtLeast(0L)
            assertThat(config.retryCount).isAtLeast(0)
        }
    }
}
