package com.scanium.app.selling.assistant

import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.ItemContextSnapshot
import kotlinx.coroutines.delay

/**
 * Mock assistant repository for DEV builds when user is not signed in.
 *
 * This allows testing the assistant UI flow without Google authentication.
 * Returns helpful canned responses that simulate real assistant behavior.
 *
 * SECURITY NOTE: This is only used when ALL of these conditions are met:
 * 1. Build flavor is DEV (compile-time enforced via DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT)
 * 2. User is NOT signed in with Google
 *
 * If user IS signed in (even in dev), the real backend is used instead.
 * Beta/prod builds NEVER use this mock - they always require sign-in.
 */
class DevMockAssistantRepository : AssistantRepository {

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
        // Simulate network delay for realistic UX
        delay(300)

        // Generate contextual mock response based on user message
        val response = generateMockResponse(userMessage, items, exportProfile)

        return AssistantResponse(
            reply = response,
            actions = emptyList(),
            confidenceTier = ConfidenceTier.MED,
            evidence = emptyList(),
            suggestedAttributes = emptyList(),
            suggestedDraftUpdates = emptyList(),
        )
    }

    private fun generateMockResponse(
        userMessage: String,
        items: List<ItemContextSnapshot>,
        exportProfile: ExportProfileDefinition,
    ): String {
        val lowerMessage = userMessage.lowercase()
        val hasItem = items.isNotEmpty()
        val itemTitle = items.firstOrNull()?.title ?: "your item"
        val itemCategory = items.firstOrNull()?.category ?: "general"

        return when {
            // Title-related questions
            lowerMessage.contains("title") -> {
                buildTitleResponse(itemTitle, itemCategory)
            }
            // Description-related questions
            lowerMessage.contains("description") || lowerMessage.contains("describe") -> {
                buildDescriptionResponse(itemTitle, itemCategory)
            }
            // Price-related questions
            lowerMessage.contains("price") || lowerMessage.contains("worth") || lowerMessage.contains("value") -> {
                buildPriceResponse(itemTitle)
            }
            // Photo-related questions
            lowerMessage.contains("photo") || lowerMessage.contains("picture") || lowerMessage.contains("image") -> {
                buildPhotoResponse(itemCategory)
            }
            // Category questions
            lowerMessage.contains("category") -> {
                buildCategoryResponse(itemCategory)
            }
            // Help/general questions
            lowerMessage.contains("help") || lowerMessage.contains("what can") -> {
                buildHelpResponse()
            }
            // Default contextual response
            hasItem -> {
                buildContextualResponse(itemTitle, exportProfile.displayName)
            }
            else -> {
                buildGenericResponse()
            }
        }
    }

    private fun buildTitleResponse(itemTitle: String, category: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |For "$itemTitle", here are some title tips:
            |
            |1. Include the brand and model if known
            |2. Mention key features (size, color, material)
            |3. Note the condition (new, like new, good, used)
            |4. Keep it under 80 characters for best visibility
            |
            |Example: "Brand Name - Item Type - Size/Color - Condition"
        """.trimMargin()
    }

    private fun buildDescriptionResponse(itemTitle: String, category: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |For "$itemTitle", include these in your description:
            |
            |- Measurements and dimensions
            |- Materials and construction
            |- Condition details (any flaws or wear)
            |- What's included in the sale
            |- Why you're selling (builds trust)
            |
            |Tip: Be honest about condition - it prevents returns and builds your reputation.
        """.trimMargin()
    }

    private fun buildPriceResponse(itemTitle: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |To price "$itemTitle" effectively:
            |
            |1. Search sold listings on eBay/Marktplaats for similar items
            |2. Consider condition - used items typically sell for 30-70% of retail
            |3. Factor in shipping costs if offering free shipping
            |4. Price slightly higher to leave room for negotiation
            |
            |Note: Sign in with Google to get real-time market pricing data.
        """.trimMargin()
    }

    private fun buildPhotoResponse(category: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |Photo tips for better sales:
            |
            |1. Use natural light - avoid harsh shadows
            |2. Include 5-8 photos showing all angles
            |3. Show any flaws up close (builds trust)
            |4. Include size reference (ruler, coin, hand)
            |5. Photograph labels, tags, and serial numbers
            |
            |Good photos can increase your selling price by 20-30%!
        """.trimMargin()
    }

    private fun buildCategoryResponse(category: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |Current category: "$category"
            |
            |Choosing the right category:
            |- Pick the most specific category that applies
            |- Check where similar items are listed
            |- Wrong category = less visibility
            |
            |Tip: If unsure, check completed listings for similar items.
        """.trimMargin()
    }

    private fun buildHelpResponse(): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |I can help you with:
            |
            |- Writing compelling titles and descriptions
            |- Pricing your items competitively
            |- Photo tips for better listings
            |- Category selection advice
            |- Shipping and packaging guidance
            |
            |Just ask! For example: "Help me write a title" or "What price should I set?"
            |
            |Note: You're using dev mode without Google sign-in. Some features like real-time pricing require authentication.
        """.trimMargin()
    }

    private fun buildContextualResponse(itemTitle: String, profileName: String): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |I see you're listing "$itemTitle" for $profileName.
            |
            |Here's how I can help:
            |- Ask "help with title" for title suggestions
            |- Ask "help with description" for description tips
            |- Ask "what price" for pricing guidance
            |- Ask "photo tips" for photography advice
            |
            |What would you like help with?
        """.trimMargin()
    }

    private fun buildGenericResponse(): String {
        return """
            |[DEV MODE - Mock Response]
            |
            |I'm your AI listing assistant! I can help you:
            |
            |- Create effective titles and descriptions
            |- Price items competitively
            |- Take better product photos
            |- Choose the right categories
            |
            |Add an item to get personalized advice, or ask me anything about selling!
            |
            |Note: You're in dev mode without Google sign-in. Sign in for full functionality.
        """.trimMargin()
    }
}
