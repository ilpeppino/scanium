package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.auth.AuthRepository
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ItemContextSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevBypassAssistantRepositoryTest {
    private val realRepository = mockk<AssistantRepository>()
    private val mockRepository = mockk<AssistantRepository>()
    private val authRepository = mockk<AuthRepository>()

    private lateinit var repository: DevBypassAssistantRepository

    private val testSnapshot = ItemContextSnapshot(
        itemId = "item-1",
        title = "Test Item",
        description = null,
        category = "Electronics",
        confidence = 0.8f,
        attributes = emptyList(),
        priceEstimate = null,
        photosCount = 1,
    )

    private val realResponse = AssistantResponse(
        reply = "Real backend response",
    )

    private val mockResponse = AssistantResponse(
        reply = "[DEV MODE] Mock response",
    )

    @Before
    fun setup() {
        repository = DevBypassAssistantRepository(
            realRepository = realRepository,
            mockRepository = mockRepository,
            authRepository = authRepository,
        )

        coEvery {
            realRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns realResponse

        coEvery {
            mockRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns mockResponse
    }

    @Test
    fun `when signed in - delegates to real repository`() = runTest {
        every { authRepository.isSignedIn() } returns true

        val response = repository.send(
            items = listOf(testSnapshot),
            history = emptyList(),
            userMessage = "Test message",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response).isEqualTo(realResponse)
        coVerify(exactly = 1) {
            realRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        coVerify(exactly = 0) {
            mockRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `when not signed in - delegates to mock repository`() = runTest {
        every { authRepository.isSignedIn() } returns false

        val response = repository.send(
            items = listOf(testSnapshot),
            history = emptyList(),
            userMessage = "Test message",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id",
        )

        assertThat(response).isEqualTo(mockResponse)
        coVerify(exactly = 0) {
            realRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        coVerify(exactly = 1) {
            mockRepository.send(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `sign-in state is checked per request`() = runTest {
        // First request: not signed in
        every { authRepository.isSignedIn() } returns false

        val response1 = repository.send(
            items = listOf(testSnapshot),
            history = emptyList(),
            userMessage = "First message",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id-1",
        )
        assertThat(response1).isEqualTo(mockResponse)

        // Second request: now signed in
        every { authRepository.isSignedIn() } returns true

        val response2 = repository.send(
            items = listOf(testSnapshot),
            history = emptyList(),
            userMessage = "Second message",
            exportProfile = ExportProfiles.generic(),
            correlationId = "test-id-2",
        )
        assertThat(response2).isEqualTo(realResponse)

        // Verify both repositories were called once
        coVerify(exactly = 1) { mockRepository.send(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { realRepository.send(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `passes all parameters to delegated repository`() = runTest {
        every { authRepository.isSignedIn() } returns true

        val items = listOf(testSnapshot)
        val history = listOf(
            AssistantMessage(
                role = com.scanium.app.model.AssistantRole.USER,
                content = "Previous message",
                timestamp = System.currentTimeMillis(),
            )
        )
        val userMessage = "Test message"
        val exportProfile = ExportProfiles.generic()
        val correlationId = "test-correlation-id"
        val imageAttachments = emptyList<ItemImageAttachment>()
        val assistantPrefs = AssistantPrefs(language = "EN")
        val includePricing = true
        val pricingCountryCode = "NL"

        repository.send(
            items = items,
            history = history,
            userMessage = userMessage,
            exportProfile = exportProfile,
            correlationId = correlationId,
            imageAttachments = imageAttachments,
            assistantPrefs = assistantPrefs,
            includePricing = includePricing,
            pricingCountryCode = pricingCountryCode,
        )

        coVerify {
            realRepository.send(
                items = items,
                history = history,
                userMessage = userMessage,
                exportProfile = exportProfile,
                correlationId = correlationId,
                imageAttachments = imageAttachments,
                assistantPrefs = assistantPrefs,
                includePricing = includePricing,
                pricingCountryCode = pricingCountryCode,
            )
        }
    }
}
