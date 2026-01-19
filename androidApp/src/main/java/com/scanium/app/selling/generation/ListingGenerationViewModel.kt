package com.scanium.app.selling.generation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.ScannedItem
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.selling.assistant.AssistantBackendException
import com.scanium.app.selling.assistant.AssistantRepository
import com.scanium.shared.core.models.assistant.ConfidenceTier
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ExportProfileRepository
import com.scanium.shared.core.models.listing.ExportProfiles
import com.scanium.shared.core.models.listing.GeneratedListing
import com.scanium.shared.core.models.listing.GeneratedListingWarning
import com.scanium.shared.core.models.listing.WarningType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * State for listing generation.
 */
sealed class ListingGenerationState {
    /** Initial state, ready to generate */
    data object Idle : ListingGenerationState()

    /** Currently generating listing */
    data object Loading : ListingGenerationState()

    /** Generation successful */
    data class Success(
        val listing: GeneratedListing,
        val itemId: String,
    ) : ListingGenerationState()

    /** Generation failed */
    data class Error(
        val message: String,
        val retryable: Boolean,
        val itemId: String,
    ) : ListingGenerationState()
}

/**
 * ViewModel for generating marketplace listings from scanned items.
 *
 * Uses the Assistant API to generate optimized titles and descriptions
 * based on extracted item attributes.
 */
@HiltViewModel
class ListingGenerationViewModel
    @Inject
    constructor(
        private val assistantRepository: AssistantRepository,
        private val exportProfileRepository: ExportProfileRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ListingGenerationVM"
            private const val GENERATION_PROMPT =
                "Generate a marketplace listing for this item. " +
                    "Provide an optimized title (max 80 characters) and a detailed description. " +
                    "Use the extracted attributes (brand, model, color, etc.) to make the listing accurate and appealing. " +
                    "Format as follows:\n" +
                    "TITLE: [your title here]\n" +
                    "DESCRIPTION: [your description here]"
        }

        private val _state = MutableStateFlow<ListingGenerationState>(ListingGenerationState.Idle)
        val state: StateFlow<ListingGenerationState> = _state

        /** The item currently being processed */
        private var currentItem: ScannedItem? = null

        /**
         * Generate a marketplace listing for the given item.
         *
         * @param item The scanned item to generate a listing for
         */
        fun generateListing(item: ScannedItem) {
            if (_state.value is ListingGenerationState.Loading) {
                Log.d(TAG, "Already generating, ignoring request")
                return
            }

            currentItem = item
            _state.value = ListingGenerationState.Loading

            viewModelScope.launch {
                try {
                    val result = callAssistant(item)
                    _state.value =
                        ListingGenerationState.Success(
                            listing = result,
                            itemId = item.id,
                        )
                } catch (e: AssistantBackendException) {
                    Log.e(TAG, "Assistant backend error", e)
                    _state.value =
                        ListingGenerationState.Error(
                            message = e.failure.message ?: "Failed to generate listing",
                            retryable = e.failure.retryable,
                            itemId = item.id,
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error generating listing", e)
                    _state.value =
                        ListingGenerationState.Error(
                            message = "Unexpected error: ${e.message}",
                            retryable = true,
                            itemId = item.id,
                        )
                }
            }
        }

        /**
         * Retry the last generation attempt.
         */
        fun retry() {
            currentItem?.let { generateListing(it) }
        }

        /**
         * Reset state to idle.
         */
        fun reset() {
            _state.value = ListingGenerationState.Idle
            currentItem = null
        }

        private suspend fun callAssistant(item: ScannedItem): GeneratedListing {
            val itemContext = buildItemContext(item)
            val exportProfile =
                exportProfileRepository.getProfile(ExportProfileId.GENERIC)
                    ?: ExportProfiles.generic()

            val correlationId = UUID.randomUUID().toString()

            val response =
                assistantRepository.send(
                    items = listOf(itemContext),
                    history = emptyList(),
                    userMessage = GENERATION_PROMPT,
                    exportProfile = exportProfile,
                    correlationId = correlationId,
                )

            return parseResponse(response, item)
        }

        private fun buildItemContext(item: ScannedItem): ItemContextSnapshot {
            val attributes =
                item.attributes.map { (key, attr) ->
                    ItemAttributeSnapshot(
                        key = key,
                        value = attr.value,
                        confidence = attr.confidence,
                    )
                }

            return ItemContextSnapshot(
                itemId = item.id,
                title = item.displayLabel,
                description = null,
                category = item.category.displayName,
                confidence = item.confidence,
                attributes = attributes,
                priceEstimate = (item.priceRange.first + item.priceRange.second) / 2.0,
                photosCount = if (item.thumbnail != null || item.thumbnailRef != null) 1 else 0,
                exportProfileId = ExportProfileId.GENERIC,
            )
        }

        private fun parseResponse(
            response: com.scanium.app.model.AssistantResponse,
            item: ScannedItem,
        ): GeneratedListing {
            // First check for suggestedDraftUpdates
            val titleUpdate = response.suggestedDraftUpdates.find { it.field == "title" }
            val descriptionUpdate = response.suggestedDraftUpdates.find { it.field == "description" }

            val title: String
            val titleConfidence: ConfidenceTier
            val description: String
            val descriptionConfidence: ConfidenceTier

            val contentText = response.content.orEmpty()

            if (titleUpdate != null || descriptionUpdate != null) {
                // Use structured updates
                title = titleUpdate?.value ?: parseTitle(contentText) ?: item.displayLabel
                titleConfidence = titleUpdate?.confidence?.let { mapConfidence(it) } ?: ConfidenceTier.MED
                description = descriptionUpdate?.value ?: parseDescription(contentText) ?: ""
                descriptionConfidence = descriptionUpdate?.confidence?.let { mapConfidence(it) } ?: ConfidenceTier.MED
            } else {
                // Parse from content text
                title = parseTitle(contentText) ?: item.displayLabel
                titleConfidence = response.confidenceTier?.let { mapConfidence(it) } ?: ConfidenceTier.MED
                description = parseDescription(contentText) ?: contentText
                descriptionConfidence = response.confidenceTier?.let { mapConfidence(it) } ?: ConfidenceTier.MED
            }

            // Build warnings
            val warnings = mutableListOf<GeneratedListingWarning>()

            // Add warnings for low confidence attributes
            item.attributes.forEach { (key, attr) ->
                if (attr.confidence < 0.5f) {
                    warnings.add(
                        GeneratedListingWarning(
                            field = key,
                            message = "Please verify the ${formatAttributeLabel(key)}",
                            type = WarningType.NEEDS_VERIFICATION,
                        ),
                    )
                }
            }

            // Add warnings from suggestedDraftUpdates
            response.suggestedDraftUpdates
                .filter { it.requiresConfirmation }
                .forEach { update ->
                    warnings.add(
                        GeneratedListingWarning(
                            field = update.field,
                            message = "Please verify the ${update.field}",
                            type = WarningType.NEEDS_VERIFICATION,
                        ),
                    )
                }

            // Add low confidence warning if overall confidence is low
            if (titleConfidence == ConfidenceTier.LOW || descriptionConfidence == ConfidenceTier.LOW) {
                warnings.add(
                    GeneratedListingWarning(
                        field = "listing",
                        message = "Low confidence in generated content. Please review carefully.",
                        type = WarningType.LOW_CONFIDENCE,
                    ),
                )
            }

            return GeneratedListing(
                title = title,
                description = description,
                titleConfidence = titleConfidence,
                descriptionConfidence = descriptionConfidence,
                warnings = warnings,
                suggestedNextPhoto = response.suggestedNextPhoto,
            )
        }

        private fun parseTitle(content: String): String? {
            // Try to parse "TITLE: ..." format
            val titleMatch =
                Regex("TITLE:\\s*(.+?)(?:\\n|DESCRIPTION:|$)", RegexOption.IGNORE_CASE)
                    .find(content)
            return titleMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        private fun parseDescription(content: String): String? {
            // Try to parse "DESCRIPTION: ..." format
            val descMatch =
                Regex(
                    pattern = "DESCRIPTION:\\s*(.+)",
                    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
                ).find(content)
            return descMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        private fun mapConfidence(tier: com.scanium.app.model.ConfidenceTier): ConfidenceTier =
            when (tier) {
                com.scanium.app.model.ConfidenceTier.HIGH -> ConfidenceTier.HIGH
                com.scanium.app.model.ConfidenceTier.MED -> ConfidenceTier.MED
                com.scanium.app.model.ConfidenceTier.LOW -> ConfidenceTier.LOW
            }

        private fun formatAttributeLabel(key: String): String =
            when (key) {
                "brand" -> "brand"
                "model" -> "model number"
                "color" -> "color"
                "secondaryColor" -> "secondary color"
                "material" -> "material"
                else -> key
            }
    }
