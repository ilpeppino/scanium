package com.scanium.app.selling.assistant

import com.scanium.app.auth.AuthRepository
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ItemContextSnapshot

/**
 * Delegating repository that handles dev bypass for Google sign-in.
 *
 * In DEV builds when dev bypass is enabled:
 * - If user IS signed in → uses real backend repository
 * - If user is NOT signed in → uses mock repository (local responses)
 *
 * In BETA/PROD builds, this class is never used (see AssistantRepositoryFactory).
 *
 * This design ensures:
 * 1. Backend security is NOT weakened - real backend still requires auth
 * 2. Dev builds can test assistant UI without Google sign-in
 * 3. Sign-in state is checked per-request (supports sign-in/out during session)
 */
class DevBypassAssistantRepository(
    private val realRepository: AssistantRepository,
    private val mockRepository: AssistantRepository,
    private val authRepository: AuthRepository,
) : AssistantRepository {

    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?,
        includePricing: Boolean,
        pricingCountryCode: String?,
    ): AssistantResponse {
        // Check sign-in status at request time (supports sign-in/out during session)
        val isSignedIn = authRepository.isSignedIn()

        return if (isSignedIn) {
            // User is signed in - use real backend
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
        } else {
            // User is NOT signed in - use mock (dev bypass)
            mockRepository.send(
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
