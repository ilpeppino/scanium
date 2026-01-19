package com.scanium.shared.core.models.assistant

import com.scanium.shared.core.models.listing.DraftFieldKey
import com.scanium.shared.core.models.listing.DraftProvenance
import com.scanium.shared.core.models.listing.ExportProfileDefinition
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ListingDraft
import kotlinx.serialization.Serializable

enum class AssistantRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

// ============================================================================
// Personalization Preferences
// ============================================================================

/**
 * Tone preference for assistant responses.
 */
enum class AssistantTone {
    NEUTRAL,
    FRIENDLY,
    PROFESSIONAL,
    MARKETPLACE,
}

/**
 * Region preference (affects currency, marketplace mentions).
 */
enum class AssistantRegion {
    NL,
    DE,
    BE,
    FR,
    UK,
    US,
    EU,
}

/**
 * Unit system preference.
 */
enum class AssistantUnits {
    METRIC,
    IMPERIAL,
}

/**
 * Verbosity preference.
 */
enum class AssistantVerbosity {
    CONCISE,
    NORMAL,
    DETAILED,
}

/**
 * User's assistant personalization preferences.
 * Sent with each request, not stored server-side.
 */
@Serializable
data class AssistantPrefs(
    /** Language code (e.g., 'EN', 'NL', 'DE') */
    val language: String? = null,
    /** Response tone */
    val tone: AssistantTone? = null,
    /** Region for currency/marketplace context */
    val region: AssistantRegion? = null,
    /** Unit system */
    val units: AssistantUnits? = null,
    /** Response verbosity */
    val verbosity: AssistantVerbosity? = null,
)

/**
 * Pricing preferences for marketplace price insights.
 */
@Serializable
data class PricingPrefs(
    /** Country code for pricing (e.g., 'NL', 'DE', 'US') */
    val countryCode: String,
)

@Serializable
data class AssistantMessage(
    val role: AssistantRole,
    val content: String,
    val timestamp: Long,
    val itemContextIds: List<String> = emptyList(),
)

enum class AssistantActionType {
    ADD_ATTRIBUTES,
    APPLY_DRAFT_UPDATE,
    COPY_TEXT,
    OPEN_POSTING_ASSIST,
    OPEN_SHARE,
    OPEN_URL,
    SUGGEST_NEXT_PHOTO,
}

@Serializable
data class AssistantAction(
    val type: AssistantActionType,
    val payload: Map<String, String> = emptyMap(),
    /** Label to display on the action button/chip */
    val label: String? = null,
    /** Whether this action requires user confirmation (e.g., for LOW confidence) */
    val requiresConfirmation: Boolean = false,
)

/**
 * Safety information returned by the AI Gateway.
 * Contains stable reason codes for client handling.
 */
@Serializable
data class SafetyResponse(
    val blocked: Boolean = false,
    val reasonCode: String? = null,
    val requestId: String? = null,
)

/**
 * Confidence tier for assistant responses.
 * HIGH: Strong visual evidence supports the answer.
 * MED: Some evidence, but not conclusive.
 * LOW: Insufficient evidence; response is speculative.
 */
enum class ConfidenceTier {
    HIGH,
    MED,
    LOW,
}

/**
 * Evidence bullet derived from VisualFacts.
 */
@Serializable
data class EvidenceBullet(
    /** Type of evidence */
    val type: String,
    /** Human-readable evidence statement */
    val text: String,
)

/**
 * Suggested attribute with confidence.
 */
@Serializable
data class SuggestedAttribute(
    /** Attribute key (brand, model, color, etc.) */
    val key: String,
    /** Suggested value */
    val value: String,
    /** Confidence tier for this suggestion */
    val confidence: ConfidenceTier,
    /** Source of suggestion (ocr, logo, color, etc.) */
    val source: String? = null,
)

/**
 * Suggested draft updates (title, description).
 */
@Serializable
data class SuggestedDraftUpdate(
    /** Field to update */
    val field: String,
    /** Suggested value */
    val value: String,
    /** Confidence tier for this suggestion */
    val confidence: ConfidenceTier,
    /** Whether this requires user confirmation before applying */
    val requiresConfirmation: Boolean = false,
)

// ============================================================================
// Pricing Insights Models (Phase 3)
// ============================================================================

/**
 * Confidence level for pricing results (matches backend schema).
 */
enum class PricingConfidence {
    LOW,
    MED,
    HIGH,
}

/**
 * Price information with amount and currency.
 */
@Serializable
data class PriceInfo(
    /** Price amount */
    val amount: Double,
    /** 3-letter currency code */
    val currency: String,
)

/**
 * Price range (low/high) from market data.
 */
@Serializable
data class PriceRange(
    /** Lowest price found */
    val low: Double,
    /** Highest price found */
    val high: Double,
    /** Currency code (e.g., 'EUR', 'USD') */
    val currency: String,
)

/**
 * Single pricing result from a marketplace listing.
 */
@Serializable
data class PricingResult(
    /** Listing title */
    val title: String,
    /** Price information */
    val price: PriceInfo,
    /** Full URL to the listing */
    val url: String,
    /** Marketplace ID (e.g., 'marktplaats', 'amazon') */
    val sourceMarketplaceId: String,
)

/**
 * Marketplace that was queried for pricing data.
 */
@Serializable
data class MarketplaceUsed(
    /** Marketplace ID */
    val id: String,
    /** Display name */
    val name: String,
    /** Base URL/domain */
    val baseUrl: String,
)

/**
 * Complete pricing insights response from backend.
 */
@Serializable
data class PricingInsights(
    /** Status: OK, NOT_SUPPORTED, DISABLED, ERROR, TIMEOUT, NO_RESULTS */
    val status: String,
    /** Country code used for lookup */
    val countryCode: String,
    /** Marketplaces that were queried */
    val marketplacesUsed: List<MarketplaceUsed> = emptyList(),
    /** Summary of search query (no secrets) */
    val querySummary: String? = null,
    /** Top pricing results (max 5, only if status = OK) */
    val results: List<PricingResult> = emptyList(),
    /** Price range derived from results */
    val range: PriceRange? = null,
    /** Confidence in the pricing data */
    val confidence: PricingConfidence? = null,
    /** Error code if status is ERROR/TIMEOUT/NO_RESULTS */
    val errorCode: String? = null,
)

/**
 * Response from the AI Gateway.
 * The gateway may use 'reply' or 'content' for the text response.
 */
@Serializable
data class AssistantResponse(
    /** Primary response field from gateway (newer API) */
    val reply: String? = null,
    /** Legacy response field (for backwards compatibility) */
    val content: String? = null,
    val actions: List<AssistantAction> = emptyList(),
    val citationsMetadata: Map<String, String>? = null,
    val safety: SafetyResponse? = null,
    val correlationId: String? = null,
    /** Confidence tier for the response (when visual evidence is used) */
    val confidenceTier: ConfidenceTier? = null,
    /** Evidence bullets referencing VisualFacts */
    val evidence: List<EvidenceBullet> = emptyList(),
    /** Suggested attributes derived from visual evidence */
    val suggestedAttributes: List<SuggestedAttribute> = emptyList(),
    /** Suggested draft updates (title, description) */
    val suggestedDraftUpdates: List<SuggestedDraftUpdate> = emptyList(),
    /** Suggested next photo instruction when evidence is insufficient */
    val suggestedNextPhoto: String? = null,
    /** Market price insights from marketplace data (Phase 4) */
    val marketPrice: PricingInsights? = null,
    /** Legacy field (backward compatibility) - prefer marketPrice */
    @Deprecated("Use marketPrice instead", replaceWith = ReplaceWith("marketPrice"))
    val pricingInsights: PricingInsights? = null,
) {
    /** Get the response text, preferring 'reply' over 'content' */
    val text: String get() = reply ?: content ?: ""
}

/**
 * Source/provenance of an attribute value.
 * Used to indicate whether a value is user-provided (authoritative) or system-detected.
 */
enum class AttributeSource {
    /** Value was manually entered or edited by the user - use as authoritative */
    USER,

    /** Value was detected by ML/vision system */
    DETECTED,

    /** Default/system value */
    DEFAULT,

    /** Unknown source */
    UNKNOWN,
}

@Serializable
data class ItemAttributeSnapshot(
    val key: String,
    val value: String,
    val confidence: Float? = null,
    /** Source/provenance of this attribute - USER means user-provided (authoritative) */
    val source: AttributeSource? = null,
)

@Serializable
data class ItemContextSnapshot(
    val itemId: String,
    val title: String?,
    val description: String?,
    val category: String?,
    val confidence: Float?,
    val attributes: List<ItemAttributeSnapshot> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: ExportProfileId = ExportProfileId.GENERIC,
)

@Serializable
data class ExportProfileSnapshot(
    val id: ExportProfileId,
    val displayName: String,
)

@Serializable
data class AssistantPromptRequest(
    val items: List<ItemContextSnapshot>,
    val conversation: List<AssistantMessage>,
    val userMessage: String,
    val exportProfile: ExportProfileSnapshot,
    val systemPrompt: String,
    /** User's personalization preferences */
    val assistantPrefs: AssistantPrefs? = null,
    /** Whether to include pricing insights in the response */
    val includePricing: Boolean = false,
    /** Pricing preferences (country code, etc.) */
    val pricingPrefs: PricingPrefs? = null,
)

object AssistantPromptBuilder {
    private const val SYSTEM_PROMPT = """
You are Scanium Seller Assistant. Provide safe, honest guidance for listing items.
- Do not scrape third-party sites without official APIs.
- Do not auto-fill forms, automate posting, or ask for passwords.
- Use provided item context and export profile; if data is missing, ask clarifying questions.
"""

    fun buildRequest(
        items: List<ItemContextSnapshot>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        conversation: List<AssistantMessage> = emptyList(),
        assistantPrefs: AssistantPrefs? = null,
    ): AssistantPromptRequest {
        return AssistantPromptRequest(
            items = items,
            conversation = conversation,
            userMessage = userMessage.trim(),
            exportProfile =
                ExportProfileSnapshot(
                    id = exportProfile.id,
                    displayName = exportProfile.displayName,
                ),
            systemPrompt = SYSTEM_PROMPT.trim(),
            assistantPrefs = assistantPrefs,
        )
    }
}

object ItemContextSnapshotBuilder {
    fun fromDraft(draft: ListingDraft): ItemContextSnapshot {
        val sortedAttributes =
            draft.fields.entries
                .sortedBy { it.key.wireValue }
                .mapNotNull { (key, field) ->
                    val value = field.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ItemAttributeSnapshot(
                        key = key.wireValue,
                        value = value,
                        confidence = field.confidence,
                        source = mapSource(field.source),
                    )
                }

        val categoryField = draft.fields[DraftFieldKey.CATEGORY]
        val confidence = categoryField?.confidence ?: draft.title.confidence

        return ItemContextSnapshot(
            itemId = draft.itemId,
            title = draft.title.value?.takeIf { it.isNotBlank() },
            description = draft.description.value?.takeIf { it.isNotBlank() },
            category = categoryField?.value?.takeIf { it.isNotBlank() },
            confidence = confidence,
            attributes = sortedAttributes,
            priceEstimate = draft.price.value,
            photosCount = draft.photos.size,
            exportProfileId = draft.profile,
        )
    }

    /**
     * Map DraftProvenance to AttributeSource.
     * USER_EDITED becomes USER (authoritative), all others map accordingly.
     */
    private fun mapSource(provenance: DraftProvenance): AttributeSource {
        return when (provenance) {
            DraftProvenance.USER_EDITED -> AttributeSource.USER
            DraftProvenance.DETECTED -> AttributeSource.DETECTED
            DraftProvenance.DEFAULT -> AttributeSource.DEFAULT
            DraftProvenance.UNKNOWN -> AttributeSource.UNKNOWN
        }
    }
}
