package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.model.ItemContextSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevMockAssistantRepositoryTest {
    private val repository = DevMockAssistantRepository()

    @Test
    fun `send returns mock response with DEV MODE marker`() = runTest {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Test Item",
            description = null,
            category = "Electronics",
            confidence = 0.8f,
            attributes = emptyList(),
            priceEstimate = null,
            photosCount = 1,
        )

        val response = repository.send(
            items = listOf(snapshot),
            history = emptyList(),
            userMessage = "Help me with this item",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-correlation-id",
        )

        assertThat(response.text).contains("DEV MODE")
    }

    @Test
    fun `title question returns title-specific response`() = runTest {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Vintage Lamp",
            description = null,
            category = "Home",
            confidence = 0.6f,
            attributes = emptyList(),
            priceEstimate = null,
            photosCount = 1,
        )

        val response = repository.send(
            items = listOf(snapshot),
            history = emptyList(),
            userMessage = "Help me with the title",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response.text.lowercase()).contains("title")
        assertThat(response.text).contains("DEV MODE")
    }

    @Test
    fun `price question returns pricing guidance`() = runTest {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Camera",
            description = null,
            category = "Electronics",
            confidence = 0.7f,
            attributes = emptyList(),
            priceEstimate = null,
            photosCount = 1,
        )

        val response = repository.send(
            items = listOf(snapshot),
            history = emptyList(),
            userMessage = "What price should I set?",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response.text.lowercase()).contains("price")
        assertThat(response.text).contains("DEV MODE")
    }

    @Test
    fun `help question returns list of capabilities`() = runTest {
        val response = repository.send(
            items = emptyList(),
            history = emptyList(),
            userMessage = "What can you help me with?",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response.text.lowercase()).contains("help")
        assertThat(response.text).contains("DEV MODE")
    }

    @Test
    fun `empty items returns generic response`() = runTest {
        val response = repository.send(
            items = emptyList(),
            history = emptyList(),
            userMessage = "Hello",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response.text).contains("DEV MODE")
        assertThat(response.text.lowercase()).contains("listing assistant")
    }

    @Test
    fun `photo question returns photo tips`() = runTest {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Shoes",
            description = null,
            category = "Fashion",
            confidence = 0.6f,
            attributes = emptyList(),
            priceEstimate = null,
            photosCount = 1,
        )

        val response = repository.send(
            items = listOf(snapshot),
            history = emptyList(),
            userMessage = "How should I take photos?",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response.text.lowercase()).contains("photo")
        assertThat(response.text).contains("DEV MODE")
    }
}
