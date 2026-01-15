package com.scanium.app.items.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.AttributeDisplayFormatter
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.summary.AttributeSummaryGenerator
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.LayerState
import com.scanium.app.config.FeatureFlags

/**
 * Redesigned edit screen for Phase 3: Item details UX.
 *
 * Features:
 * - Photo gallery at top (primary + additional photos)
 * - **Detected Attributes section** showing brand, color, type, OCR text IMMEDIATELY
 * - Enrichment status row (non-blocking, with retry)
 * - Suggested additions section (when user edited text and new attrs arrive)
 * - ONE BIG editable text box for attribute summary
 * - Actions: Save, AI Generate, Add Photos
 */
@Deprecated(
    "Use EditItemScreenV3. This screen is legacy and will be removed.",
    ReplaceWith("EditItemScreenV3(itemId, onBack, onAddPhotos, onAiGenerate, itemsViewModel, exportAssistantViewModelFactory)"),
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemScreenV2(
    itemId: String,
    onBack: () -> Unit,
    onAddPhotos: (String) -> Unit,
    onAiGenerate: (String) -> Unit,
    itemsViewModel: ItemsViewModel,
    /** Factory for creating ExportAssistantViewModel - if null, AI button falls back to onAiGenerate */
    exportAssistantViewModelFactory: ExportAssistantViewModel.Factory? = null,
) {
    val context = LocalContext.current
    val allItems by itemsViewModel.items.collectAsState()
    val item by remember(allItems, itemId) {
        derivedStateOf { allItems.find { it.id == itemId } }
    }

    // Export Assistant state
    var showExportAssistantSheet by remember { mutableStateOf(false) }
    val exportAssistantViewModel = remember(exportAssistantViewModelFactory, itemId) {
        exportAssistantViewModelFactory?.create(itemId, itemsViewModel)
    }

    // Summary text state (local draft)
    var summaryText by remember(item?.attributesSummaryText, item?.attributes) {
        mutableStateOf(
            item?.attributesSummaryText?.takeIf { it.isNotBlank() }
                ?: generateInitialSummary(item),
        )
    }
    var textEdited by remember { mutableStateOf(item?.summaryTextUserEdited ?: false) }

    // Track suggested additions (new attributes that arrived while user edited)
    var suggestedAdditions by remember { mutableStateOf<List<SuggestedAttribute>>(emptyList()) }

    // Watch for attribute changes when user has edited text
    LaunchedEffect(item?.attributes, textEdited) {
        if (textEdited && item != null) {
            suggestedAdditions = detectSuggestedAdditions(item!!, summaryText)
        }
    }

    // Auto-trigger enrichment if item is not yet enriched or stale
    LaunchedEffect(item) {
        val currentItem = item ?: return@LaunchedEffect
        if (shouldTriggerEnrichment(currentItem)) {
            itemsViewModel.extractVisionInsights(
                context = context,
                itemId = itemId,
                imageUri = currentItem.fullImageUri,
            )
        }
    }

    val currentItem = item
    if (currentItem == null) {
        // Item not found - show empty state
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_item_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.edit_item_not_found), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_item_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    // AI Generate button
                    OutlinedButton(
                        onClick = {
                            if (exportAssistantViewModel != null && FeatureFlags.allowAiAssistant) {
                                showExportAssistantSheet = true
                            } else {
                                onAiGenerate(itemId)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.common_ai))
                    }

                    // Save button
                    Button(
                        onClick = {
                            // Parse summary text and save
                            val parsed = AttributeSummaryGenerator.parseSummaryText(summaryText)
                            val newAttributes = AttributeSummaryGenerator.toAttributeMap(
                                parsed,
                                currentItem.attributes,
                            )

                            // Update via view model
                            itemsViewModel.updateSummaryText(itemId, summaryText, textEdited)

                            // Update individual attributes that changed
                            for ((key, value) in newAttributes) {
                                if (currentItem.attributes[key]?.value != value.value) {
                                    itemsViewModel.updateItemAttribute(
                                        itemId,
                                        key,
                                        value,
                                    )
                                }
                            }

                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Photo Gallery Section
            PhotoGallerySection(
                item = currentItem,
                onAddPhoto = { onAddPhotos(itemId) },
            )

            Spacer(Modifier.height(12.dp))

            // Enrichment Status Row (non-blocking)
            EnrichmentStatusRow(
                enrichmentStatus = currentItem.enrichmentStatus,
                onRetry = {
                    itemsViewModel.extractVisionInsights(
                        context = context,
                        itemId = itemId,
                        imageUri = currentItem.fullImageUri,
                    )
                },
            )

            // Detected Attributes Section (visible immediately)
            DetectedAttributesSection(
                attributes = currentItem.attributes,
                visionAttributes = currentItem.visionAttributes,
                detectedAttributes = currentItem.detectedAttributes,
            )

            // Suggested Additions Section (when user edited and new attrs arrived)
            if (textEdited && suggestedAdditions.isNotEmpty()) {
                SuggestedAdditionsSection(
                    suggestions = suggestedAdditions,
                    onAccept = { suggestion ->
                        // Insert the suggestion into the summary text
                        summaryText = insertSuggestionIntoText(summaryText, suggestion)
                        suggestedAdditions = suggestedAdditions - suggestion
                    },
                    onDismiss = { suggestion ->
                        suggestedAdditions = suggestedAdditions - suggestion
                    },
                )
            }

            // Main Summary Text Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                OutlinedTextField(
                    value = summaryText,
                    onValueChange = { newText ->
                        summaryText = newText
                        if (!textEdited) {
                            textEdited = true
                        }
                    },
                    label = { Text(stringResource(R.string.item_detail_title)) },
                    placeholder = {
                        Text(
                            "Category: ...\nBrand: ...\nColor: ...\nCondition: ...",
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 350.dp)
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            // Missing fields hint
            val missingFields = AttributeSummaryGenerator.getMissingFields(
                attributes = currentItem.attributes,
                category = currentItem.category,
                condition = currentItem.condition,
            )
            if (missingFields.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.edit_item_missing_fields, missingFields.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Export Assistant Bottom Sheet
    if (showExportAssistantSheet && exportAssistantViewModel != null) {
        val settingsRepository = remember { SettingsRepository(context) }
        val ttsManager = remember { com.scanium.app.assistant.tts.TtsManager(context, settingsRepository) }
        ExportAssistantSheet(
            viewModel = exportAssistantViewModel,
            settingsRepository = settingsRepository,
            ttsManager = ttsManager,
            onDismiss = { showExportAssistantSheet = false },
            onApply = { title, description, bullets ->
                // Apply export content to summary text (user can then edit/save)
                val builder = StringBuilder()
                title?.let {
                    builder.appendLine("Title: $it")
                    builder.appendLine()
                }
                description?.let {
                    builder.appendLine("Description:")
                    builder.appendLine(it)
                    builder.appendLine()
                }
                if (bullets.isNotEmpty()) {
                    builder.appendLine("Highlights:")
                    bullets.forEach { bullet ->
                        builder.appendLine("• $bullet")
                    }
                }
                // Keep existing attributes at the bottom
                if (summaryText.isNotBlank()) {
                    builder.appendLine()
                    builder.appendLine("---")
                    builder.appendLine("Attributes:")
                    builder.append(summaryText)
                }
                summaryText = builder.toString()
                textEdited = true
            },
        )
    }
}

/**
 * Check if enrichment should be triggered automatically.
 */
private fun shouldTriggerEnrichment(item: ScannedItem): Boolean {
    // Don't trigger if already enriching
    if (item.enrichmentStatus.isEnriching) return false

    // Don't trigger if user has edited (respect their edits)
    if (item.summaryTextUserEdited) return false

    // Don't trigger if no image available
    if (item.fullImageUri == null) return false

    // Trigger if never enriched
    val lastEnrichment = item.enrichmentStatus.lastUpdated
    if (lastEnrichment == null || lastEnrichment == 0L) return true

    // Trigger if stale (more than 5 minutes old)
    val age = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - lastEnrichment
    return age > 5 * 60 * 1000L
}

/**
 * Photo gallery showing primary and additional photos.
 */
@Composable
private fun PhotoGallerySection(
    item: ScannedItem,
    onAddPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_item_photos),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            // Primary thumbnail
            item {
                val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()
                PhotoThumbnail(
                    bitmap = thumbnailBitmap,
                    label = stringResource(R.string.edit_item_photo_primary),
                    isPrimary = true,
                )
            }

            // Additional photos
            items(item.additionalPhotos) { photo ->
                val photoBitmap = remember(photo.uri) {
                    photo.uri?.let { uri ->
                        try {
                            BitmapFactory.decodeFile(uri)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                PhotoThumbnail(
                    bitmap = photoBitmap?.asImageBitmap(),
                    label = null,
                    isPrimary = false,
                )
            }

            // Add photo button
            item {
                AddPhotoButton(onClick = onAddPhoto)
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    label: String?,
    isPrimary: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .size(100.dp)
                .then(
                    if (isPrimary) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.items_no_image),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AddPhotoButton(onClick: () -> Unit) {
    val addPhotoText = stringResource(R.string.edit_item_add_photo)
    val addText = stringResource(R.string.common_add)
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = addPhotoText,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    addText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Non-blocking enrichment status row.
 */
@Composable
private fun EnrichmentStatusRow(
    enrichmentStatus: EnrichmentLayerStatus,
    onRetry: () -> Unit,
) {
    val analyzingText = stringResource(R.string.edit_item_analyzing)
    val analyzeFailedText = stringResource(R.string.edit_item_analyze_failed)
    val analyzedText = stringResource(R.string.edit_item_analyzed)
    val noNewInfoText = stringResource(R.string.edit_item_no_new_info)
    val retryText = stringResource(R.string.common_retry)

    val statusMessage: String
    val statusColor: androidx.compose.ui.graphics.Color
    val showProgress: Boolean
    val showRetry: Boolean

    when {
        enrichmentStatus.isEnriching -> {
            statusMessage = analyzingText
            statusColor = MaterialTheme.colorScheme.primary
            showProgress = true
            showRetry = false
        }
        enrichmentStatus.layerB == LayerState.FAILED || enrichmentStatus.layerC == LayerState.FAILED -> {
            statusMessage = analyzeFailedText
            statusColor = MaterialTheme.colorScheme.error
            showProgress = false
            showRetry = true
        }
        enrichmentStatus.isComplete && enrichmentStatus.hasAnyResults -> {
            statusMessage = analyzedText
            statusColor = MaterialTheme.colorScheme.tertiary
            showProgress = false
            showRetry = false
        }
        enrichmentStatus.isComplete -> {
            statusMessage = noNewInfoText
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            showProgress = false
            showRetry = false
        }
        else -> {
            // Idle state - don't show anything
            return
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = statusColor,
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = when {
                        enrichmentStatus.layerB == LayerState.FAILED -> Icons.Default.Warning
                        enrichmentStatus.isComplete && enrichmentStatus.hasAnyResults -> Icons.Default.Check
                        else -> Icons.Default.Search
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor,
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                modifier = Modifier.weight(1f),
            )

            if (showRetry) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = retryText,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f),
            )
        }
    }
}

/**
 * Section showing detected attributes immediately (visible without extra clicks).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedAttributesSection(
    attributes: Map<String, ItemAttribute>,
    visionAttributes: com.scanium.shared.core.models.items.VisionAttributes,
    detectedAttributes: Map<String, ItemAttribute>,
) {
    // Build combined list of displayable attributes
    val displayAttrs = buildDisplayableAttributes(attributes, visionAttributes, detectedAttributes)

    if (displayAttrs.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.items_detected_attributes_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                displayAttrs.forEach { attr ->
                    AttributeChip(
                        attributeKey = attr.key,
                        label = attr.displayName,
                        value = attr.value,
                        isUserProvided = attr.isUserProvided,
                        evidenceType = attr.evidenceType,
                        confidence = attr.confidence,
                    )
                }
            }
        }
    }
}

/**
 * Single attribute chip with provenance indicator.
 */
@Composable
private fun AttributeChip(
    attributeKey: String,
    label: String,
    value: String,
    isUserProvided: Boolean,
    evidenceType: String?,
    confidence: Float,
) {
    val context = LocalContext.current
    val userProvidedText = stringResource(R.string.edit_item_user_provided)
    val displayValue = AttributeDisplayFormatter.formatForDisplay(context, attributeKey, value)
    val containerColor = when {
        isUserProvided -> MaterialTheme.colorScheme.primaryContainer
        confidence >= 0.8f -> MaterialTheme.colorScheme.tertiaryContainer
        confidence >= 0.5f -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    AssistChip(
        onClick = {},
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$label: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        leadingIcon = {
            if (isUserProvided) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = userProvidedText,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else if (evidenceType != null) {
                val icon = when (evidenceType) {
                    "OCR" -> Icons.Default.Search
                    "LOGO" -> Icons.Default.Check
                    "COLOR" -> Icons.Default.Check
                    else -> Icons.Default.AutoAwesome
                }
                Icon(
                    icon,
                    contentDescription = evidenceType,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor.copy(alpha = 0.6f),
        ),
    )
}

/**
 * Build list of displayable attributes from all sources.
 */
private fun buildDisplayableAttributes(
    attributes: Map<String, ItemAttribute>,
    visionAttributes: com.scanium.shared.core.models.items.VisionAttributes,
    detectedAttributes: Map<String, ItemAttribute>,
): List<DisplayableAttribute> {
    val result = mutableListOf<DisplayableAttribute>()
    val seen = mutableSetOf<String>()

    val displayNameMap = mapOf(
        "brand" to "Brand",
        "color" to "Color",
        "secondaryColor" to "Secondary Color",
        "itemType" to "Type",
        "model" to "Model",
        "material" to "Material",
        "size" to "Size",
        "ocrText" to "Text",
        "labelHints" to "Labels",
    )

    // Add from main attributes
    for ((key, attr) in attributes) {
        if (attr.value.isBlank()) continue
        val displayName = displayNameMap[key] ?: continue
        if (seen.contains(key)) continue
        seen.add(key)

        result.add(
            DisplayableAttribute(
                key = key,
                displayName = displayName,
                value = attr.value.take(50),
                isUserProvided = attr.source?.contains("user", ignoreCase = true) == true,
                evidenceType = attr.source?.let { extractEvidenceType(it) },
                confidence = attr.confidence,
            )
        )
    }

    // Add from vision attributes if not already present
    if (!seen.contains("brand")) {
        visionAttributes.logos.firstOrNull()?.let { logo ->
            result.add(
                DisplayableAttribute(
                    key = "brand",
                    displayName = "Brand",
                    value = logo.name,
                    isUserProvided = false,
                    evidenceType = "LOGO",
                    confidence = logo.score,
                )
            )
            seen.add("brand")
        }
    }

    if (!seen.contains("color")) {
        visionAttributes.colors.firstOrNull()?.let { color ->
            result.add(
                DisplayableAttribute(
                    key = "color",
                    displayName = "Color",
                    value = color.name,
                    isUserProvided = false,
                    evidenceType = "COLOR",
                    confidence = color.score,
                )
            )
            seen.add("color")
        }
    }

    if (!seen.contains("itemType")) {
        val itemType = visionAttributes.itemType ?: visionAttributes.labels.firstOrNull()?.name
        if (!itemType.isNullOrBlank()) {
            result.add(
                DisplayableAttribute(
                    key = "itemType",
                    displayName = "Type",
                    value = itemType,
                    isUserProvided = false,
                    evidenceType = "LABEL",
                    confidence = 0.7f,
                )
            )
            seen.add("itemType")
        }
    }

    // Add OCR snippet if present
    if (!seen.contains("ocrText")) {
        visionAttributes.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
            val snippet = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString(" | ")
                .take(60)
            if (snippet.isNotBlank()) {
                result.add(
                    DisplayableAttribute(
                        key = "ocrText",
                        displayName = "Text",
                        value = snippet,
                        isUserProvided = false,
                        evidenceType = "OCR",
                        confidence = 0.8f,
                    )
                )
            }
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
        source.contains("vision", ignoreCase = true) -> "VISION"
        else -> null
    }
}

/**
 * Display data for an attribute.
 */
private data class DisplayableAttribute(
    val key: String,
    val displayName: String,
    val value: String,
    val isUserProvided: Boolean,
    val evidenceType: String?,
    val confidence: Float,
)

/**
 * Section showing suggested attribute additions.
 */
@Composable
private fun SuggestedAdditionsSection(
    suggestions: List<SuggestedAttribute>,
    onAccept: (SuggestedAttribute) -> Unit,
    onDismiss: (SuggestedAttribute) -> Unit,
) {
    val newAttributesText = stringResource(R.string.edit_item_new_attributes)
    val acceptText = stringResource(R.string.common_accept)
    val dismissText = stringResource(R.string.common_dismiss)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = newAttributesText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(8.dp))

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${suggestion.displayName}: ${suggestion.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onAccept(suggestion) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = acceptText,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { onDismiss(suggestion) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = dismissText,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Suggested attribute to add to the summary text.
 */
data class SuggestedAttribute(
    val key: String,
    val displayName: String,
    val value: String,
)

/**
 * Generate initial summary text from item attributes.
 */
private fun generateInitialSummary(item: ScannedItem?): String {
    if (item == null) return ""

    return AttributeSummaryGenerator.generateSummaryText(
        attributes = item.attributes,
        category = item.category,
        condition = item.condition,
        includeEmptyFields = true,
    )
}

/**
 * Detect attributes that should be suggested to the user.
 */
private fun detectSuggestedAdditions(
    item: ScannedItem,
    currentSummaryText: String,
): List<SuggestedAttribute> {
    val suggestions = mutableListOf<SuggestedAttribute>()
    val summaryLower = currentSummaryText.lowercase()

    // Check for attributes that exist but aren't in the summary text
    for ((key, attr) in item.attributes) {
        if (attr.value.isBlank()) continue

        val displayName = when (key) {
            "brand" -> "Brand"
            "color" -> "Color"
            "model" -> "Model"
            "material" -> "Material"
            "size" -> "Size"
            "itemType" -> "Type"
            else -> continue // Only suggest standard attributes
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
                ),
            )
        }
    }

    return suggestions
}

/**
 * Insert a suggestion into the summary text, replacing (missing) if present.
 */
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
