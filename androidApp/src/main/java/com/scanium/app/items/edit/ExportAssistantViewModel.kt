package com.scanium.app.items.edit

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.BuildConfig
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.selling.assistant.AssistantBackendException
import com.scanium.app.selling.assistant.AssistantRepository
import com.scanium.app.selling.assistant.ItemImageAttachment
import com.scanium.shared.core.models.assistant.AssistantMessage
import com.scanium.shared.core.models.assistant.AssistantRole
import com.scanium.shared.core.models.assistant.ConfidenceTier
import com.scanium.shared.core.models.assistant.ItemContextSnapshot
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.model.ImageRef
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ExportAssistant"

/**
 * Export prompt sent to the AI assistant.
 * This prompt asks the assistant to generate marketplace-ready content.
 */
private const val EXPORT_PROMPT = """Generate a marketplace-ready listing for this item.

Please provide:
1. A compelling title (max 80 chars) that highlights brand, key features, and condition
2. A detailed description (2-3 paragraphs) covering features, condition, and buyer benefits
3. 3-5 bullet points highlighting key selling points

Use the detected attributes (brand, color, type, condition) and any OCR text for accuracy.
Be professional but engaging. Focus on what makes this item valuable to buyers."""

/**
 * State for the Export Assistant UI.
 */
sealed class ExportAssistantState {
    /** Initial state, ready to generate */
    data object Idle : ExportAssistantState()

    /** Currently generating export content */
    data class Generating(
        val startedAt: Long = System.currentTimeMillis(),
        val correlationId: String? = null,
    ) : ExportAssistantState()

    /** Successfully generated export content */
    data class Success(
        val title: String?,
        val description: String?,
        val bullets: List<String>,
        val confidenceTier: ConfidenceTier?,
        val fromCache: Boolean,
        val model: String?,
        val generatedAt: Long = System.currentTimeMillis(),
    ) : ExportAssistantState()

    /** Error generating export content */
    data class Error(
        val message: String,
        val isRetryable: Boolean = true,
        val occurredAt: Long = System.currentTimeMillis(),
    ) : ExportAssistantState()

    val isLoading: Boolean get() = this is Generating
    val isError: Boolean get() = this is Error
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns a user-friendly status message for this state.
     * Used to show progress indicators without blocking the UI.
     */
    fun getStatusMessage(): String? = when (this) {
        is Idle -> null
        is Generating -> "Drafting description…"
        is Success -> null // Don't show message when complete
        is Error -> message
    }

    /**
     * Returns true if this is a long-running operation that needs a "Still working…" indicator.
     * Shown after 10 seconds of generation.
     */
    fun isLongRunning(): Boolean = when (this) {
        is Generating -> (System.currentTimeMillis() - startedAt) > 10_000
        else -> false
    }
}

/**
 * Result of applying export content to an item.
 */
sealed class ExportApplyResult {
    data object Success : ExportApplyResult()
    data class Error(val message: String) : ExportApplyResult()
}

/**
 * ViewModel for the Export Assistant feature (Phase 4).
 *
 * Handles generating marketplace-ready titles and descriptions using AI.
 * The export content is stored in the item's export fields and can be
 * applied/copied from the UI.
 *
 * LOCALIZATION:
 * - Uses the user's assistant language preference from SettingsRepository
 * - Localizes attribute values in the context sent to the AI backend
 * - Backend generates output in the requested language
 */
class ExportAssistantViewModel
    @AssistedInject
    constructor(
        @Assisted private val itemId: String,
        @Assisted private val itemsViewModel: ItemsViewModel,
        @ApplicationContext private val context: Context,
        private val assistantRepository: AssistantRepository,
        private val exportProfileRepository: ExportProfileRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            itemId: String,
            itemsViewModel: ItemsViewModel,
        ): ExportAssistantViewModel
    }

    private val _state = MutableStateFlow<ExportAssistantState>(ExportAssistantState.Idle)
    val state: StateFlow<ExportAssistantState> = _state.asStateFlow()

    /** Current item being edited */
    private val _item = MutableStateFlow<ScannedItem?>(null)
    val item: StateFlow<ScannedItem?> = _item.asStateFlow()

    init {
        // Load item on creation
        loadItem()
    }

    private fun loadItem() {
        val current = itemsViewModel.getItem(itemId)
        _item.value = current

        // If item already has export content, show it
        if (current != null && current.exportTitle != null) {
            _state.value = ExportAssistantState.Success(
                title = current.exportTitle,
                description = current.exportDescription,
                bullets = current.exportBullets,
                confidenceTier = current.exportConfidenceTier?.let {
                    runCatching { ConfidenceTier.valueOf(it) }.getOrNull()
                },
                fromCache = current.exportFromCache,
                model = current.exportModel,
                generatedAt = current.exportGeneratedAt ?: System.currentTimeMillis(),
            )
        }
    }

    /**
     * Generate export listing content for the item.
     * Sends item context to the assistant and parses the response.
     *
     * LOCALIZATION:
     * - Fetches the user's assistant language preference
     * - Localizes attribute values in the context before sending
     * - Passes language preference to backend for localized output
     */
    fun generateExport() {
        val currentItem = _item.value ?: run {
            _state.value = ExportAssistantState.Error("Item not found", isRetryable = false)
            return
        }

        val correlationId = CorrelationIds.newAssistRequestId()
        _state.update { ExportAssistantState.Generating(correlationId = correlationId) }

        viewModelScope.launch {
            try {
                // Get user's assistant preferences including language
                val assistantPrefs = settingsRepository.assistantPrefsFlow.first()
                val languageTag = assistantPrefs.language ?: "en"

                Log.i(TAG, "Generating export for item ${currentItem.id} correlationId=$correlationId languageTag=$languageTag")

                // Build item context snapshot with localized attribute values
                val snapshot = buildItemContextSnapshot(currentItem, languageTag)

                // Build image attachment if available
                val imageAttachments = buildImageAttachments(currentItem)

                // Get export profile
                val defaultProfileId = exportProfileRepository.getDefaultProfileId()
                val profile = exportProfileRepository.getProfile(defaultProfileId)
                    ?: com.scanium.shared.core.models.listing.ExportProfiles.generic()

                // Send request with language preferences
                val response = assistantRepository.send(
                    items = listOf(snapshot),
                    history = emptyList(),
                    userMessage = EXPORT_PROMPT,
                    exportProfile = profile,
                    correlationId = correlationId,
                    imageAttachments = imageAttachments,
                    assistantPrefs = assistantPrefs,
                )

                // DEV-only logging (safe - no raw response body in release builds)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, """
                        Export response debug:
                        - correlationId=$correlationId
                        - suggestedDraftUpdates count=${response.suggestedDraftUpdates.size}
                        - title length=${response.suggestedDraftUpdates.find { it.field == "title" }?.value?.length ?: 0}
                        - description length=${response.suggestedDraftUpdates.find { it.field == "description" }?.value?.length ?: 0}
                        - bullets count=${response.suggestedDraftUpdates.count { it.field.startsWith("bullet") }}
                        - response.text length=${response.text.length}
                    """.trimIndent())
                    Log.d(TAG, "Raw response.text: '${response.text}'")
                }

                Log.i(TAG, "Export response received: suggestedDraftUpdates=${response.suggestedDraftUpdates.size}")

                // Parse response from structured suggestedDraftUpdates
                val title = response.suggestedDraftUpdates
                    .find { it.field == "title" }?.value
                val description = response.suggestedDraftUpdates
                    .find { it.field == "description" }?.value
                val bullets = response.suggestedDraftUpdates
                    .filter { it.field.startsWith("bullet") }
                    .sortedBy { it.field }
                    .map { it.value }

                // CRITICAL: If description is null/empty (even with structured data),
                // DO NOT apply empty content. Treat as error to prevent data loss.
                // Only use text fallback if description field is completely missing AND
                // no other structured data exists (to prevent JSON leakage).
                val hasStructuredData = response.suggestedDraftUpdates.isNotEmpty()

                val finalTitle = if (hasStructuredData && title != null) {
                    title
                } else if (hasStructuredData) {
                    // Structured data exists but title is missing - try fallback
                    parseTitle(response.text)
                } else {
                    title ?: parseTitle(response.text)
                }

                val finalDescription = if (hasStructuredData && description != null) {
                    description
                } else if (hasStructuredData) {
                    // Structured data exists but description is missing - try fallback
                    parseDescription(response.text)
                } else {
                    description ?: parseDescription(response.text)
                }

                val finalBullets = if (hasStructuredData && bullets.isNotEmpty()) {
                    bullets
                } else if (hasStructuredData) {
                    // Structured data exists but bullets are missing - try fallback
                    parseBullets(response.text)
                } else {
                    bullets.ifEmpty { parseBullets(response.text) }
                }

                // DEBUG: Log parsed values
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Parsed values - finalTitle: ${finalTitle?.take(50)}, finalDescription: ${finalDescription?.take(100)}, finalBullets: ${finalBullets.size}")
                }

                // Defensive check: Detect JSON leakage in description
                if (finalDescription != null && containsJsonPattern(finalDescription)) {
                    Log.e(TAG, "CRITICAL: JSON detected in description field - rejecting response")
                    _state.value = ExportAssistantState.Error(
                        message = "Invalid response format. Please try again.",
                        isRetryable = true,
                    )
                    return@launch
                }

                // CRITICAL: Do not apply empty description - treat as generation failure
                // This prevents data loss when backend returns success but no usable content
                if (finalDescription.isNullOrBlank()) {
                    Log.e(TAG, "CRITICAL: Generated description is empty - rejecting response to prevent data loss")
                    Log.e(TAG, "Debug: hasStructuredData=$hasStructuredData, structuredUpdatesCount=${response.suggestedDraftUpdates.size}, responseTextLength=${response.text.length}")
                    _state.value = ExportAssistantState.Error(
                        message = "Failed to generate description. Please try again.",
                        isRetryable = true,
                    )
                    return@launch
                }

                // Update item with export fields
                itemsViewModel.updateExportFields(
                    itemId = itemId,
                    exportTitle = finalTitle,
                    exportDescription = finalDescription,
                    exportBullets = finalBullets,
                    exportFromCache = false, // Backend cache info not exposed
                    exportModel = null, // Model info not exposed
                    exportConfidenceTier = response.confidenceTier?.name,
                )

                // Refresh item
                _item.value = itemsViewModel.getItem(itemId)

                _state.value = ExportAssistantState.Success(
                    title = finalTitle,
                    description = finalDescription,
                    bullets = finalBullets,
                    confidenceTier = response.confidenceTier,
                    fromCache = false,
                    model = null,
                )

                Log.i(TAG, "Export generated successfully: title=${finalTitle?.take(30)}...")
            } catch (e: AssistantBackendException) {
                Log.e(TAG, "Export generation failed: ${e.failure.message}", e)
                _state.value = ExportAssistantState.Error(
                    message = e.failure.message ?: "Failed to generate export",
                    isRetryable = e.failure.retryable,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Export generation failed", e)
                _state.value = ExportAssistantState.Error(
                    message = e.message ?: "An unexpected error occurred",
                    isRetryable = true,
                )
            }
        }
    }

    /**
     * Retry generating export after an error.
     */
    fun retry() {
        generateExport()
    }

    /**
     * Reset to idle state.
     */
    fun dismiss() {
        _state.value = ExportAssistantState.Idle
    }

    /**
     * Build item context snapshot for the assistant request.
     *
     * LOCALIZATION:
     * - Localizes attribute values (color, material, condition) for the specified language
     * - Uses ItemAttributeLocalizer to convert canonical English values to localized display values
     * - This ensures the LLM sees content in the user's language, improving output quality
     *
     * @param item The scanned item to build context for
     * @param languageTag BCP-47 language tag (e.g., "it", "de", "fr")
     */
    private fun buildItemContextSnapshot(item: ScannedItem, languageTag: String): ItemContextSnapshot {
        // Helper to localize attribute values for constrained vocabularies
        fun localizeValue(key: String, value: String): String {
            // Only localize for non-English languages
            if (languageTag.startsWith("en", ignoreCase = true)) {
                return value
            }
            return ItemAttributeLocalizer.localizeAttributeValue(context, key, value)
        }

        // Build attribute snapshots from structured attributes with localization
        val attributes = item.attributes.entries.map { (key, attr) ->
            ItemAttributeSnapshot(
                key = key,
                value = localizeValue(key, attr.value),
                confidence = attr.confidence,
            )
        }.toMutableList()

        // Add vision attributes as additional context (also localized)
        item.visionAttributes.ocrText?.let { ocr ->
            if (ocr.isNotBlank()) {
                attributes.add(ItemAttributeSnapshot(key = "ocrText", value = ocr.take(500), confidence = 0.8f))
            }
        }
        item.visionAttributes.primaryBrand?.let { brand ->
            if (attributes.none { it.key == "brand" }) {
                attributes.add(ItemAttributeSnapshot(key = "brand", value = brand, confidence = 0.7f))
            }
        }
        item.visionAttributes.primaryColor?.let { color ->
            if (attributes.none { it.key == "color" }) {
                val localizedColor = localizeValue("color", color.name)
                attributes.add(ItemAttributeSnapshot(key = "color", value = localizedColor, confidence = color.score))
            }
        }
        item.visionAttributes.itemType?.let { type ->
            if (attributes.none { it.key == "itemType" }) {
                attributes.add(ItemAttributeSnapshot(key = "itemType", value = type, confidence = 0.7f))
            }
        }

        // Add user's summary if they edited it
        if (item.summaryTextUserEdited && item.attributesSummaryText.isNotBlank()) {
            attributes.add(ItemAttributeSnapshot(key = "userNotes", value = item.attributesSummaryText, confidence = 1.0f))
        }

        // DEBUG: Log language violation detection
        if (!languageTag.startsWith("en", ignoreCase = true)) {
            val attrMap = attributes.associate { it.key to it.value }
            val violations = ItemAttributeLocalizer.detectEnglishViolations(attrMap, languageTag)
            if (violations.isNotEmpty()) {
                Log.w(TAG, "PROMPT_LANGUAGE_VIOLATION: Found ${violations.size} English values in $languageTag context: $violations")
            }
        }

        return ItemContextSnapshot(
            itemId = item.id,
            title = item.displayLabel,
            description = null,
            category = item.category.name,
            confidence = item.confidence,
            attributes = attributes,
            priceEstimate = item.estimatedPriceRange?.let { (it.low.amount + it.high.amount) / 2.0 },
            photosCount = 1 + item.additionalPhotos.size,
            exportProfileId = ExportProfileId.GENERIC,
        )
    }

    /**
     * Build image attachment for the assistant request.
     */
    private fun buildImageAttachments(item: ScannedItem): List<ItemImageAttachment> {
        val thumbnail = item.thumbnail ?: item.thumbnailRef ?: return emptyList()

        return when (thumbnail) {
            is ImageRef.Bytes -> listOf(
                ItemImageAttachment(
                    itemId = item.id,
                    imageBytes = thumbnail.bytes,
                    mimeType = thumbnail.mimeType,
                    filename = "thumbnail.jpg",
                )
            )
            is ImageRef.CacheKey -> {
                // Try to get bytes from cache
                val cached = com.scanium.app.items.ThumbnailCache.get(thumbnail.key)
                if (cached != null) {
                    listOf(
                        ItemImageAttachment(
                            itemId = item.id,
                            imageBytes = cached.bytes,
                            mimeType = cached.mimeType,
                            filename = "thumbnail.jpg",
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }
    }

    // ==================== Text Parsing Fallbacks ====================

    /**
     * Parse title from response text if not provided in structured format.
     */
    private fun parseTitle(text: String): String? {
        // Look for "Title:" or "**Title:**" pattern
        val titlePatterns = listOf(
            Regex("""(?:Title|TITLE):\s*(.+?)(?:\n|$)"""),
            Regex("""\*\*Title\*\*:\s*(.+?)(?:\n|$)"""),
            Regex("""^***REMOVED***\s*(.+?)(?:\n|$)""", RegexOption.MULTILINE),
        )

        for (pattern in titlePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim().take(80)
            }
        }

        // Fallback: first line if it's short enough
        val firstLine = text.lines().firstOrNull()?.trim()
        if (firstLine != null && firstLine.length <= 80 && firstLine.isNotEmpty()) {
            return firstLine
        }

        return null
    }

    /**
     * Parse description from response text if not provided in structured format.
     */
    private fun parseDescription(text: String): String? {
        // Look for "Description:" pattern
        val descPatterns = listOf(
            Regex("""(?:Description|DESCRIPTION):\s*(.+?)(?:\n\n|Bullet|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""\*\*Description\*\*:\s*(.+?)(?:\n\n|Bullet|$)""", RegexOption.DOT_MATCHES_ALL),
        )

        for (pattern in descPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        // Fallback: text after first line, excluding bullets
        val lines = text.lines()
        if (lines.size > 1) {
            val descriptionLines = lines.drop(1)
                .takeWhile { !it.trim().startsWith("-") && !it.trim().startsWith("•") }
            val description = descriptionLines.joinToString("\n").trim()
            if (description.length > 50) {
                return description
            }
        }

        return null
    }

    /**
     * Parse bullet points from response text if not provided in structured format.
     */
    private fun parseBullets(text: String): List<String> {
        val bullets = mutableListOf<String>()

        // Look for lines starting with - or •
        val bulletPattern = Regex("""^[-•]\s*(.+?)$""", RegexOption.MULTILINE)
        bulletPattern.findAll(text).forEach { match ->
            val bullet = match.groupValues[1].trim()
            if (bullet.isNotEmpty() && bullet.length > 10) {
                bullets.add(bullet)
            }
        }

        return bullets.take(5)
    }

    /**
     * Detect JSON patterns in text that indicate leakage.
     * Returns true if the text contains JSON keys/syntax that should not be in user-visible content.
     */
    private fun containsJsonPattern(text: String): Boolean {
        // Check for common JSON patterns that indicate raw response leakage
        val jsonPatterns = listOf(
            """"suggestedDraftUpdates"""",
            """"confidence"""",
            """"requiresConfirmation"""",
            """": \{""",
            """": \[""",
            """\{[\s]*"field"""",
            """\{[\s]*"value"""",
        )

        return jsonPatterns.any { pattern ->
            Regex(pattern).containsMatchIn(text)
        }
    }
}
