package com.scanium.app.enrichment

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for EnrichmentRepository configuration validation.
 * Tests the error handling for missing API keys and configuration.
 */
@RunWith(RobolectricTestRunner::class)
class EnrichmentRepositoryHttpTest {
    @Test
    fun `submitEnrichment fails without API key`() =
        runBlocking {
            // Create repository without API key
            val repository =
                EnrichmentRepository(
                    baseUrlProvider = { "https://example.com" },
                    apiKeyProvider = { null },
                    getDeviceId = { "test-device" },
                )

            val bitmap = createTestBitmap()

            // Act
            val result = repository.submitEnrichment(bitmap, "item-001")

            // Assert
            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull() as EnrichmentException
            assertThat(exception.errorCode).isEqualTo("CONFIG_ERROR")
            assertThat(exception.userMessage).contains("API key")
            assertThat(exception.retryable).isFalse()
        }

    @Test
    fun `submitEnrichment fails with empty API key`() =
        runBlocking {
            val repository =
                EnrichmentRepository(
                    baseUrlProvider = { "https://example.com" },
                    apiKeyProvider = { "" },
                    getDeviceId = { "test-device" },
                )

            val bitmap = createTestBitmap()

            // Act
            val result = repository.submitEnrichment(bitmap, "item-001")

            // Assert
            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull() as EnrichmentException
            assertThat(exception.errorCode).isEqualTo("CONFIG_ERROR")
        }

    @Test
    fun `getStatus fails without API key`() =
        runBlocking {
            val repository =
                EnrichmentRepository(
                    baseUrlProvider = { "https://example.com" },
                    apiKeyProvider = { null },
                    getDeviceId = { "test-device" },
                )

            // Act
            val result = repository.getStatus("550e8400-e29b-41d4-a716-446655440000")

            // Assert
            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull() as EnrichmentException
            assertThat(exception.errorCode).isEqualTo("CONFIG_ERROR")
            assertThat(exception.userMessage).contains("API key")
        }

    @Test
    fun `EnrichmentException properties`() {
        val exception =
            EnrichmentException(
                errorCode = "TEST_ERROR",
                userMessage = "Test error message",
                retryable = true,
            )

        assertThat(exception.errorCode).isEqualTo("TEST_ERROR")
        assertThat(exception.userMessage).isEqualTo("Test error message")
        assertThat(exception.retryable).isTrue()
        assertThat(exception.message).isEqualTo("Test error message")
    }

    @Test
    fun `EnrichmentException non-retryable by default`() {
        val exception =
            EnrichmentException(
                errorCode = "TEST",
                userMessage = "Test",
            )

        assertThat(exception.retryable).isFalse()
    }

    private fun createTestBitmap(): Bitmap {
        // Create a minimal 2x2 pixel bitmap for testing
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.GREEN)
            setPixel(0, 1, Color.BLUE)
            setPixel(1, 1, Color.WHITE)
        }
    }
}
