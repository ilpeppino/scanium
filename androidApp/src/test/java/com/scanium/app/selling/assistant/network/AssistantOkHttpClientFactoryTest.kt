package com.scanium.app.selling.assistant.network

import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for AssistantOkHttpClientFactory.
 * Verifies that clients are created with correct configurations.
 */
class AssistantOkHttpClientFactoryTest {
    // ==================== Client Creation Tests ====================

    @Test
    fun `create with DEFAULT config applies correct timeouts`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.DEFAULT,
                logStartupPolicy = false,
            )

        assertThat(client.connectTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS))
        assertThat(client.readTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.DEFAULT_READ_TIMEOUT_SECONDS))
        assertThat(client.writeTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.DEFAULT_WRITE_TIMEOUT_SECONDS))
        assertThat(client.callTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.DEFAULT_CALL_TIMEOUT_SECONDS))
    }

    @Test
    fun `create with PREFLIGHT config applies tight timeouts`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.PREFLIGHT,
                logStartupPolicy = false,
            )

        assertThat(client.connectTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_CONNECT_TIMEOUT_SECONDS))
        assertThat(client.readTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_READ_TIMEOUT_SECONDS))
        assertThat(client.writeTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_WRITE_TIMEOUT_SECONDS))
        assertThat(client.callTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_CALL_TIMEOUT_SECONDS))
    }

    @Test
    fun `create with WARMUP config applies moderate timeouts`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.WARMUP,
                logStartupPolicy = false,
            )

        assertThat(client.connectTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.WARMUP_CONNECT_TIMEOUT_SECONDS))
        assertThat(client.readTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.WARMUP_READ_TIMEOUT_SECONDS))
    }

    @Test
    fun `create with TEST config applies short timeouts`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.TEST,
                logStartupPolicy = false,
            )

        // Test config should have 5s timeouts
        assertThat(client.connectTimeoutMillis).isEqualTo(5000L)
        assertThat(client.readTimeoutMillis).isEqualTo(5000L)
        assertThat(client.writeTimeoutMillis).isEqualTo(5000L)
        assertThat(client.callTimeoutMillis).isEqualTo(10000L)
    }

    @Test
    fun `create with custom config applies custom timeouts`() {
        val customConfig =
            AssistantHttpConfig(
                connectTimeoutSeconds = 20L,
                readTimeoutSeconds = 120L,
                writeTimeoutSeconds = 45L,
                callTimeoutSeconds = 150L,
            )

        val client =
            AssistantOkHttpClientFactory.create(
                config = customConfig,
                logStartupPolicy = false,
            )

        assertThat(client.connectTimeoutMillis).isEqualTo(20000L)
        assertThat(client.readTimeoutMillis).isEqualTo(120000L)
        assertThat(client.writeTimeoutMillis).isEqualTo(45000L)
        assertThat(client.callTimeoutMillis).isEqualTo(150000L)
    }

    // ==================== Retry Interceptor Tests ====================

    @Test
    fun `create with retryCount greater than 0 adds retry interceptor`() {
        val configWithRetry = AssistantHttpConfig(retryCount = 1)

        val client =
            AssistantOkHttpClientFactory.create(
                config = configWithRetry,
                logStartupPolicy = false,
            )

        val hasRetryInterceptor = client.interceptors.any { it is AssistantRetryInterceptor }
        assertThat(hasRetryInterceptor).isTrue()
    }

    @Test
    fun `create with retryCount 0 does not add retry interceptor`() {
        val configWithoutRetry = AssistantHttpConfig(retryCount = 0)

        val client =
            AssistantOkHttpClientFactory.create(
                config = configWithoutRetry,
                logStartupPolicy = false,
            )

        val hasRetryInterceptor = client.interceptors.any { it is AssistantRetryInterceptor }
        assertThat(hasRetryInterceptor).isFalse()
    }

    @Test
    fun `PREFLIGHT config does not add retry interceptor`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.PREFLIGHT,
                logStartupPolicy = false,
            )

        val hasRetryInterceptor = client.interceptors.any { it is AssistantRetryInterceptor }
        assertThat(hasRetryInterceptor).isFalse()
    }

    @Test
    fun `DEFAULT config adds retry interceptor`() {
        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.DEFAULT,
                logStartupPolicy = false,
            )

        val hasRetryInterceptor = client.interceptors.any { it is AssistantRetryInterceptor }
        assertThat(hasRetryInterceptor).isTrue()
    }

    // ==================== Additional Interceptors Tests ====================

    @Test
    fun `create with additional interceptors adds them to client`() {
        val customInterceptor =
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    return chain.proceed(chain.request())
                }
            }

        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.TEST,
                additionalInterceptors = listOf(customInterceptor),
                logStartupPolicy = false,
            )

        assertThat(client.interceptors).contains(customInterceptor)
    }

    @Test
    fun `create with multiple additional interceptors adds all`() {
        val interceptor1 =
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
            }
        val interceptor2 =
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
            }

        val client =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.TEST,
                additionalInterceptors = listOf(interceptor1, interceptor2),
                logStartupPolicy = false,
            )

        assertThat(client.interceptors).contains(interceptor1)
        assertThat(client.interceptors).contains(interceptor2)
    }

    // ==================== Derive Tests ====================

    @Test
    fun `derive creates client with new timeout config`() {
        val baseClient =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.DEFAULT,
                logStartupPolicy = false,
            )

        val derivedClient =
            AssistantOkHttpClientFactory.derive(
                baseClient = baseClient,
                config = AssistantHttpConfig.PREFLIGHT,
            )

        // Derived client should have preflight timeouts
        assertThat(derivedClient.connectTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_CONNECT_TIMEOUT_SECONDS))
        assertThat(derivedClient.readTimeoutMillis)
            .isEqualTo(TimeUnit.SECONDS.toMillis(AssistantHttpConfig.PREFLIGHT_READ_TIMEOUT_SECONDS))
    }

    @Test
    fun `derive preserves base client interceptors`() {
        val customInterceptor =
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
            }

        val baseClient =
            AssistantOkHttpClientFactory.create(
                config = AssistantHttpConfig.DEFAULT,
                additionalInterceptors = listOf(customInterceptor),
                logStartupPolicy = false,
            )

        val derivedClient =
            AssistantOkHttpClientFactory.derive(
                baseClient = baseClient,
                config = AssistantHttpConfig.PREFLIGHT,
            )

        // Custom interceptor should be preserved
        assertThat(derivedClient.interceptors).contains(customInterceptor)
    }

    // ==================== Diagnostic Info Tests ====================

    @Test
    fun `getDiagnosticInfo returns all predefined configs`() {
        val info = AssistantOkHttpClientFactory.getDiagnosticInfo()

        assertThat(info).containsKey("default")
        assertThat(info).containsKey("preflight")
        assertThat(info).containsKey("warmup")
        assertThat(info).containsKey("test")
    }

    @Test
    fun `getDiagnosticInfo values match config toLogString`() {
        val info = AssistantOkHttpClientFactory.getDiagnosticInfo()

        assertThat(info["default"]).isEqualTo(AssistantHttpConfig.DEFAULT.toLogString())
        assertThat(info["preflight"]).isEqualTo(AssistantHttpConfig.PREFLIGHT.toLogString())
        assertThat(info["warmup"]).isEqualTo(AssistantHttpConfig.WARMUP.toLogString())
        assertThat(info["test"]).isEqualTo(AssistantHttpConfig.TEST.toLogString())
    }

    // ==================== Consistency Tests ====================

    @Test
    fun `all configs produce valid clients`() {
        listOf(
            AssistantHttpConfig.DEFAULT,
            AssistantHttpConfig.PREFLIGHT,
            AssistantHttpConfig.WARMUP,
            AssistantHttpConfig.TEST,
        ).forEach { config ->
            val client =
                AssistantOkHttpClientFactory.create(
                    config = config,
                    logStartupPolicy = false,
                )

            // Client should be created successfully with positive timeouts
            assertThat(client.connectTimeoutMillis).isGreaterThan(0)
            assertThat(client.readTimeoutMillis).isGreaterThan(0)
            assertThat(client.writeTimeoutMillis).isGreaterThan(0)
            assertThat(client.callTimeoutMillis).isGreaterThan(0)
        }
    }
}
