package com.scanium.app.items.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.enrichment.EnrichmentRepository
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.items.summary.AttributeSummaryGenerator
import com.scanium.app.ml.VisionInsightsPrefiller
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.items.LayerState
import com.scanium.shared.core.models.items.PhotoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.util.UUID

/**
 * Enrichment status for UI display.
 */
enum class EnrichmentUiStatus {
    /** No enrichment running */
    IDLE,
    /** Enrichment in progress */
    RUNNING,
    /** Enrichment completed with new attributes */
    UPDATED,
    /** Enrichment completed but no new information found */
    NO_CHANGE,
    /** Enrichment failed (retryable) */
    ERROR
}

/**
 * UI state for the item detail screen.
 */
data class ItemDetailUiState(
    /** The item being displayed */
    val item: ScannedItem? = null,
    /** Current draft of the summary text (local edits) */
    val summaryTextDraft: String = "",
    /** Detected attributes from vision/enrichment */
    val detectedAttributes: Map<String, DetectedAttribute> = emptyMap(),
    /** Suggested additions when user has edited text */
    val suggestions: List<SuggestedAttribute> = emptyList(),
    /** Current enrichment status */
    val enrichmentStatus: EnrichmentUiStatus = EnrichmentUiStatus.IDLE,
    /** Timestamp of last enrichment attempt */
    val lastEnrichmentAt: Long? = null,
    /** Error message if enrichment failed */
    val lastErrorMessage: String? = null,
    /** Whether user has made any edits */
    val hasUnsavedChanges: Boolean = false,
)

/**
 * A detected attribute with provenance information for display.
 */
data class DetectedAttribute(
    val key: String,
    val displayName: String,
    val value: String,
    val confidence: Float,
    val source: AttributeSourceType,
    val evidenceType: String? = null,
)

/**
 * Source type for attribute display.
 */
enum class AttributeSourceType {
    USER,
    DETECTED,
    DEFAULT
}

/**
 * ViewModel for item detail screen with enrichment state management.
 *
 * Note: This ViewModel is designed for manual construction, not Hilt injection.
 * The main EditItemScreenV2 uses ItemsViewModel directly.
 * This class can be used for testing or as a reference for the state machine pattern.
 */
class ItemDetailViewModel(
    private val stateManager: ItemsStateManager,
    private val visionPrefiller: VisionInsightsPrefiller,
    private val enrichmentRepository: EnrichmentRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "ItemDetailViewModel"
        // Re-enrich if last enrichment was more than 5 minutes ago
        private const val ENRICHMENT_STALE_MS = 5 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private var currentItemId: String? = null

    /**
     * Load an item by ID and set up state observation.
     */
    fun loadItem(itemId: String) {
        if (currentItemId == itemId) return
        currentItemId = itemId

        viewModelScope.launch {
            stateManager.items.collectLatest { items ->
                val item = items.find { it.id == itemId }
                if (item != null) {
                    updateStateFromItem(item)
                }
            }
        }
    }

    private fun updateStateFromItem(item: ScannedItem) {
        val currentState = _uiState.value

        // Build detected attributes for display
        val detectedAttrs = buildDetectedAttributes(item)

        // Build suggestions if user has edited
        val suggestions = if (item.summaryTextUserEdited) {
            buildSuggestions(item, currentState.summaryTextDraft)
        } else {
            emptyList()
        }

        // Determine enrichment UI status from layer status
        val enrichmentUiStatus = mapEnrichmentStatus(item.enrichmentStatus)

        _uiState.update { state ->
            state.copy(
                item = item,
                summaryTextDraft = if (!state.hasUnsavedChanges) {
                    item.attributesSummaryText?.takeIf { it.isNotBlank() }
                        ?: generateSummaryText(item)
                } else {
                    state.summaryTextDraft
                },
                detectedAttributes = detectedAttrs,
                suggestions = suggestions,
                enrichmentStatus = enrichmentUiStatus,
                lastEnrichmentAt = item.enrichmentStatus.lastUpdated,
            )
        }
    }

    private fun buildDetectedAttributes(item: ScannedItem): Map<String, DetectedAttribute> {
        val result = mutableMapOf<String, DetectedAttribute>()

        // Map attribute keys to display names
        val displayNames = mapOf(
            "brand" to "Brand",
            "color" to "Color",
            "secondaryColor" to "Secondary Color",
            "itemType" to "Product Type",
            "model" to "Model",
            "material" to "Material",
            "size" to "Size",
            "ocrText" to "Text Detected",
            "labelHints" to "Labels",
        )

        // Add attributes from the item
        for ((key, attr) in item.attributes) {
            if (attr.value.isBlank()) continue
            val displayName = displayNames[key] ?: key.replaceFirstChar { it.uppercase() }
            val source = attr.source ?: ""
            val sourceType = when {
                source.contains("user", ignoreCase = true) -> AttributeSourceType.USER
                source.contains("vision", ignoreCase = true) ||
                    source.contains("enrichment", ignoreCase = true) ||
                    source.contains("ocr", ignoreCase = true) -> AttributeSourceType.DETECTED
                else -> AttributeSourceType.DEFAULT
            }
            result[key] = DetectedAttribute(
                key = key,
                displayName = displayName,
                value = attr.value,
                confidence = attr.confidence,
                source = sourceType,
                evidenceType = extractEvidenceType(source),
            )
        }

        // Add vision attributes that might not be in the main attributes map
        val visionAttrs = item.visionAttributes
        if (!visionAttrs.isEmpty) {
            // Logos
            visionAttrs.logos.firstOrNull()?.let { logo ->
                if (!result.containsKey("brand") || result["brand"]?.value.isNullOrBlank()) {
                    result["brand"] = DetectedAttribute(
                        key = "brand",
                        displayName = "Brand",
                        value = logo.name,
                        confidence = logo.score,
                        source = AttributeSourceType.DETECTED,
                        evidenceType = "LOGO",
                    )
                }
            }

            // OCR text snippet
            visionAttrs.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
                val snippet = text.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(3)
                    .joinToString(" | ")
                    .take(100)
                if (snippet.isNotBlank() && !result.containsKey("ocrText")) {
                    result["ocrText"] = DetectedAttribute(
                        key = "ocrText",
                        displayName = "Text Detected",
                        value = snippet,
                        confidence = 0.8f,
                        source = AttributeSourceType.DETECTED,
                        evidenceType = "OCR",
                    )
                }
            }

            // Colors
            visionAttrs.colors.firstOrNull()?.let { color ->
                if (!result.containsKey("color") || result["color"]?.value.isNullOrBlank()) {
                    result["color"] = DetectedAttribute(
                        key = "color",
                        displayName = "Color",
                        value = color.name,
                        confidence = color.score,
                        source = AttributeSourceType.DETECTED,
                        evidenceType = "COLOR",
                    )
                }
            }

            // Item type from labels
            val itemType = visionAttrs.itemType ?: visionAttrs.labels.firstOrNull()?.name
            if (!itemType.isNullOrBlank() && !result.containsKey("itemType")) {
                result["itemType"] = DetectedAttribute(
                    key = "itemType",
                    displayName = "Product Type",
                    value = itemType,
                    confidence = 0.7f,
                    source = AttributeSourceType.DETECTED,
                    evidenceType = "LABEL",
                )
            }
        }

        return result
    }

    private fun extractEvidenceType(source: String): String? {
        return when {
            source.contains("ocr", ignoreCase = true) -> "OCR"
            source.contains("logo", ignoreCase = true) -> "LOGO"
            source.contains("color", ignoreCase = true) -> "COLOR"
            source.contains("label", ignoreCase = true) -> "LABEL"
            source.contains("llm", ignoreCase = true) -> "AI"
            else -> null
        }
    }

    private fun buildSuggestions(item: ScannedItem, currentSummaryText: String): List<SuggestedAttribute> {
        val suggestions = mutableListOf<SuggestedAttribute>()
        val summaryLower = currentSummaryText.lowercase()

        // Check detected attributes that might not be in the summary text
        for ((key, attr) in item.detectedAttributes) {
            if (attr.value.isBlank()) continue

            val displayName = when (key) {
                "brand" -> "Brand"
                "color" -> "Color"
                "model" -> "Model"
                "material" -> "Material"
                "size" -> "Size"
                "itemType" -> "Type"
                else -> continue
            }

            // Check if the value is missing from the summary
            val isInSummary = summaryLower.contains("$displayName:".lowercase()) &&
                summaryLower.contains(attr.value.lowercase())
            val isMissing = summaryLower.contains("$displayName: (missing)".lowercase())

            if (isMissing || !isInSummary) {
                suggestions.add(
                    SuggestedAttribute(
                        key = key,
                        displayName = displayName,
                        value = attr.value,
                    )
                )
            }
        }

        return suggestions
    }

    private fun mapEnrichmentStatus(status: EnrichmentLayerStatus): EnrichmentUiStatus {
        return when {
            status.isEnriching -> EnrichmentUiStatus.RUNNING
            status.layerC == LayerState.FAILED ||
                status.layerB == LayerState.FAILED -> EnrichmentUiStatus.ERROR
            status.isComplete -> {
                // Check if we have any meaningful results
                val item = _uiState.value.item
                if (item != null && item.attributes.isNotEmpty()) {
                    EnrichmentUiStatus.UPDATED
                } else {
                    EnrichmentUiStatus.NO_CHANGE
                }
            }
            else -> EnrichmentUiStatus.IDLE
        }
    }

    private fun generateSummaryText(item: ScannedItem): String {
        return AttributeSummaryGenerator.generateSummaryText(
            attributes = item.attributes,
            category = item.category,
            condition = item.condition,
            includeEmptyFields = true,
        )
    }

    /**
     * Update the summary text draft (local edit).
     */
    fun updateSummaryText(newText: String) {
        _uiState.update { state ->
            state.copy(
                summaryTextDraft = newText,
                hasUnsavedChanges = true,
            )
        }
    }

    /**
     * Save the current summary text to the item.
     */
    fun saveSummaryText() {
        val state = _uiState.value
        val itemId = currentItemId ?: return

        // Save the summary text
        stateManager.updateSummaryText(
            itemId = itemId,
            summaryText = state.summaryTextDraft,
            userEdited = true,
        )

        // Parse and save individual attributes
        val parsed = AttributeSummaryGenerator.parseSummaryText(state.summaryTextDraft)
        val newAttributes = AttributeSummaryGenerator.toAttributeMap(
            parsed,
            state.item?.attributes ?: emptyMap(),
        )

        for ((key, attr) in newAttributes) {
            if (state.item?.attributes?.get(key)?.value != attr.value) {
                stateManager.updateItemAttribute(itemId, key, attr)
            }
        }

        _uiState.update { it.copy(hasUnsavedChanges = false) }
    }

    /**
     * Apply a suggestion to the summary text.
     */
    fun applySuggestion(suggestion: SuggestedAttribute) {
        val currentText = _uiState.value.summaryTextDraft
        val newText = insertSuggestionIntoText(currentText, suggestion)

        _uiState.update { state ->
            state.copy(
                summaryTextDraft = newText,
                suggestions = state.suggestions.filter { it.key != suggestion.key },
                hasUnsavedChanges = true,
            )
        }
    }

    /**
     * Dismiss a suggestion without applying it.
     */
    fun dismissSuggestion(suggestion: SuggestedAttribute) {
        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.filter { it.key != suggestion.key },
            )
        }
    }

    /**
     * Trigger enrichment for the current item.
     */
    fun triggerEnrichment(context: Context) {
        val itemId = currentItemId ?: return
        val item = _uiState.value.item ?: return

        // Get the image URI
        val imageUri = item.fullImageUri
        if (imageUri == null) {
            Log.w(TAG, "No image URI available for enrichment")
            _uiState.update { it.copy(
                enrichmentStatus = EnrichmentUiStatus.ERROR,
                lastErrorMessage = "No image available",
            ) }
            return
        }

        // Update status to running
        _uiState.update { it.copy(
            enrichmentStatus = EnrichmentUiStatus.RUNNING,
            lastErrorMessage = null,
        ) }

        // Update enrichment layer status
        stateManager.updateEnrichmentStatus(itemId) { status ->
            status.copy(
                layerB = LayerState.IN_PROGRESS,
                layerC = LayerState.PENDING,
            )
        }

        // Trigger the extraction
        visionPrefiller.extractAndApply(
            context = context,
            scope = viewModelScope,
            stateManager = stateManager,
            itemId = itemId,
            imageUri = imageUri,
        )
    }

    /**
     * Check if enrichment should be triggered automatically.
     */
    fun shouldAutoEnrich(): Boolean {
        val state = _uiState.value
        val item = state.item ?: return false

        // Don't auto-enrich if already running
        if (state.enrichmentStatus == EnrichmentUiStatus.RUNNING) return false

        // Don't auto-enrich if user has edited (respect their edits)
        if (item.summaryTextUserEdited) return false

        // Enrich if never enriched
        val lastEnrichment = item.enrichmentStatus.lastUpdated
        if (lastEnrichment == null || lastEnrichment == 0L) return true

        // Enrich if stale
        val age = Clock.System.now().toEpochMilliseconds() - lastEnrichment
        return age > ENRICHMENT_STALE_MS
    }

    /**
     * Add a photo to the current item.
     */
    fun addPhoto(context: Context, photoUri: Uri) {
        val itemId = currentItemId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load the photo
                val bitmap = loadBitmapFromUri(context, photoUri)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load photo from URI")
                    return@launch
                }

                // Save to internal storage
                val photoFile = savePhotoToInternalStorage(context, bitmap, itemId)
                val savedUri = photoFile.absolutePath

                // Create ItemPhoto
                val photo = ItemPhoto(
                    id = UUID.randomUUID().toString(),
                    uri = savedUri,
                    bytes = null,
                    mimeType = "image/jpeg",
                    width = bitmap.width,
                    height = bitmap.height,
                    capturedAt = Clock.System.now().toEpochMilliseconds(),
                    photoHash = null,
                    photoType = PhotoType.CLOSEUP,
                )

                // Add to item
                withContext(Dispatchers.Main) {
                    stateManager.addPhotoToItem(itemId, photo)
                }

                // Trigger re-enrichment with the new photo
                withContext(Dispatchers.Main) {
                    triggerEnrichmentForPhoto(context, photoUri, itemId)
                }

                Log.i(TAG, "Added photo to item $itemId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add photo", e)
            }
        }
    }

    private fun triggerEnrichmentForPhoto(context: Context, photoUri: Uri, itemId: String) {
        // Update status to running
        _uiState.update { it.copy(
            enrichmentStatus = EnrichmentUiStatus.RUNNING,
            lastErrorMessage = null,
        ) }

        // Update enrichment layer status
        stateManager.updateEnrichmentStatus(itemId) { status ->
            status.copy(
                layerB = LayerState.IN_PROGRESS,
                layerC = LayerState.PENDING,
            )
        }

        // Trigger the extraction with the new photo
        visionPrefiller.extractAndApply(
            context = context,
            scope = viewModelScope,
            stateManager = stateManager,
            itemId = itemId,
            imageUri = photoUri,
        )
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }

    private fun savePhotoToInternalStorage(context: Context, bitmap: Bitmap, itemId: String): File {
        val photosDir = File(context.filesDir, "item_photos/$itemId")
        photosDir.mkdirs()

        val photoFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        photoFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return photoFile
    }

    private fun insertSuggestionIntoText(
        summaryText: String,
        suggestion: SuggestedAttribute,
    ): String {
        val missingPattern = "${suggestion.displayName}: (missing)"
        val existingPattern = Regex("${suggestion.displayName}:\\s*[^\n]*", RegexOption.IGNORE_CASE)

        return when {
            summaryText.contains(missingPattern, ignoreCase = true) -> {
                summaryText.replace(
                    missingPattern,
                    "${suggestion.displayName}: ${suggestion.value}",
                    ignoreCase = true,
                )
            }
            existingPattern.containsMatchIn(summaryText) -> {
                existingPattern.replace(summaryText) {
                    "${suggestion.displayName}: ${suggestion.value}"
                }
            }
            else -> {
                // Append at the end before Notes
                val notesIndex = summaryText.indexOf("Notes:", ignoreCase = true)
                if (notesIndex > 0) {
                    summaryText.substring(0, notesIndex) +
                        "${suggestion.displayName}: ${suggestion.value}\n" +
                        summaryText.substring(notesIndex)
                } else {
                    summaryText + "\n${suggestion.displayName}: ${suggestion.value}"
                }
            }
        }
    }
}
