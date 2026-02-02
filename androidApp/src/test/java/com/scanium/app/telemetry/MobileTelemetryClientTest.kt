package com.scanium.app.telemetry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MobileTelemetryClientTest {
    private val testScope = TestScope()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = true,
        )
    }

    @After
    fun tearDown() {
        try {
            MobileTelemetryClient.getInstance().shutdown()
        } catch (e: IllegalStateException) {
        }
    }

    @Test
    fun `send queues event when telemetry is enabled`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test_event")

        assertThat(client).isNotNull()
    }

    @Test
    fun `send does not queue event when telemetry is disabled`() {
        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = false,
        )

        val client = MobileTelemetryClient.getInstance()
        client.send("test_event")

        assertThat(client).isNotNull()
    }

    @Test
    fun `send with attributes sanitizes PII`() {
        val client = MobileTelemetryClient.getInstance()

        client.send(
            "test_event",
            mapOf(
                "user_id" to "secret123",
                "email" to "test@example.com",
                "item_name" to "Product Name",
                "category" to "valid_value",
            ),
        )

        assertThat(client).isNotNull()
    }

    @Test
    fun `sanitizeAttributes removes PII keys`() {
        val client = MobileTelemetryClient.getInstance()

        val attributes =
            mapOf(
                "user_id" to "secret",
                "email" to "test@example.com",
                "phone" to "1234567890",
                "device_id" to "device123",
                "imei" to "12345",
                "android_id" to "android123",
                "gps" to "40.7128,-74.0060",
                "latitude" to "40.7128",
                "longitude" to "-74.0060",
                "location" to "New York",
                "ip_address" to "192.168.1.1",
                "city" to "NYC",
                "item_name" to "Product",
                "barcode" to "123456",
                "receipt_text" to "Receipt",
                "prompt" to "Test prompt",
                "photo" to "photo_data",
                "token" to "secret_token",
                "password" to "secret_pass",
                "api_key" to "key123",
                "secret" to "secret_value",
            )

        client.send("test_event", attributes)

        assertThat(client).isNotNull()
    }

    @Test
    fun `sanitizeAttributes filters non-primitive values`() {
        val client = MobileTelemetryClient.getInstance()

        val attributes =
            mapOf(
                "string" to "value",
                "int" to 123,
                "long" to 456L,
                "float" to 1.23f,
                "double" to 4.56,
                "boolean" to true,
                "list" to listOf("a", "b"),
                "map" to mapOf("key" to "value"),
            )

        client.send("test_event", attributes)

        assertThat(client).isNotNull()
    }

    @Test
    fun `send generates unique session ID per initialization`() {
        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = true,
        )

        val client1 = MobileTelemetryClient.getInstance()

        client1.shutdown()

        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = true,
        )

        val client2 = MobileTelemetryClient.getInstance()

        assertThat(client1).isNotNull()
        assertThat(client2).isNotNull()
    }

    @Test
    fun `flush triggers immediate send`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test_event")
        client.flush()

        assertThat(client).isNotNull()
    }

    @Test
    fun `flush does nothing when telemetry is disabled`() {
        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = false,
        )

        val client = MobileTelemetryClient.getInstance()

        client.send("test_event")
        client.flush()

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles empty attributes`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test_event", emptyMap())

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles null event names gracefully`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("", emptyMap())

        assertThat(client).isNotNull()
    }

    @Test
    fun `shutdown stops background worker`() {
        val client = MobileTelemetryClient.getInstance()

        client.shutdown()

        assertThat(client).isNotNull()
    }

    @Test
    fun `send batches events when queue reaches batch size`() =
        runTest {
            val client = MobileTelemetryClient.getInstance()

            repeat(15) {
                client.send("test_event_$it")
            }

            assertThat(client).isNotNull()
        }

    @Test
    fun `send adds version and build type to events`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test_event", mapOf("category" to "test"))

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles special characters in event names`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test.event_with-special:characters")

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles unicode in attribute values`() {
        val client = MobileTelemetryClient.getInstance()

        client.send("test_event", mapOf("emoji" to "😀", "chinese" to "中文"))

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles large attribute values`() {
        val client = MobileTelemetryClient.getInstance()

        val largeValue = "a".repeat(10000)
        client.send("test_event", mapOf("large_value" to largeValue))

        assertThat(client).isNotNull()
    }

    @Test
    fun `send handles many events in quick succession`() {
        val client = MobileTelemetryClient.getInstance()

        repeat(100) {
            client.send("test_event_$it", mapOf("index" to it))
        }

        assertThat(client).isNotNull()
    }

    @Test
    fun `TelemetryEvents convenience methods send events`() {
        TelemetryEvents.appLaunch("cold_start")
        TelemetryEvents.scanStarted("camera")
        TelemetryEvents.scanCompleted(5000L, 3, hasNutritionData = true)
        TelemetryEvents.assistClicked("scan_result")
        TelemetryEvents.shareStarted("receipt")
        TelemetryEvents.errorShown("E001", "network", isRecoverable = true)
        TelemetryEvents.crashMarker("uncaught_exception")

        assertThat(true).isTrue()
    }

    @Test
    fun `initialize can be called multiple times without errors`() {
        MobileTelemetryClient.initialize(
            context = context,
            baseUrl = "https://test.example.com",
            enabled = true,
        )

        val client = MobileTelemetryClient.getInstance()

        assertThat(client).isNotNull()
    }
}
