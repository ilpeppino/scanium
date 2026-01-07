package com.scanium.app.items.edit

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.summary.AttributeSummaryGenerator
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.items.LayerState
import java.io.File

/**
 * Redesigned edit screen for multi-object scanning flow.
 *
 * Features:
 * - Photo gallery at top (primary + additional photos)
 * - Status chips showing detection status ("Brand detected", "Enriching...")
 * - Suggested additions section (when user edited text and new attrs arrive)
 * - ONE BIG editable text box for attribute summary
 * - Actions: Save, AI Generate, Add Photos
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemScreenV2(
    itemId: String,
    onBack: () -> Unit,
    onAddPhotos: (String) -> Unit,
    onAiGenerate: (String) -> Unit,
    itemsViewModel: ItemsViewModel,
) {
    val allItems by itemsViewModel.items.collectAsState()
    val item by remember(allItems, itemId) {
        derivedStateOf { allItems.find { it.id == itemId } }
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

    val currentItem = item
    if (currentItem == null) {
        // Item not found - show empty state
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Item") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text("Item not found", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Text("Cancel")
                    }

                    // AI Generate button
                    OutlinedButton(
                        onClick = { onAiGenerate(itemId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("AI")
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
                        Text("Save")
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

            // Enrichment Status Chips
            EnrichmentStatusChips(
                enrichmentStatus = currentItem.enrichmentStatus,
                attributes = currentItem.attributes,
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
                    label = { Text("Item Details") },
                    placeholder = {
                        Text(
                            "Category: ...\nBrand: ...\nColor: ...\nCondition: ...",
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 250.dp, max = 400.dp)
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
                    text = "Missing: ${missingFields.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Photo gallery showing primary and additional photos.
 */
@Composable
private fun PhotoGallerySection(
    item: ScannedItem,
    onAddPhoto: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Photos",
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
                    label = "Primary",
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
                        "No image",
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
                    contentDescription = "Add photo",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Enrichment status chips showing detection progress and results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnrichmentStatusChips(
    enrichmentStatus: EnrichmentLayerStatus,
    attributes: Map<String, com.scanium.shared.core.models.items.ItemAttribute>,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Enrichment in progress chip
        if (enrichmentStatus.isEnriching) {
            AssistChip(
                onClick = {},
                label = { Text("Enriching...") },
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                },
            )
        }

        // Brand detected chip
        val brand = attributes["brand"]?.value
        if (!brand.isNullOrBlank()) {
            AssistChip(
                onClick = {},
                label = { Text("Brand: $brand") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
            )
        }

        // Color detected chip
        val color = attributes["color"]?.value
        if (!color.isNullOrBlank()) {
            AssistChip(
                onClick = {},
                label = { Text("Color: $color") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ),
            )
        }

        // Missing brand warning
        if (brand.isNullOrBlank() && enrichmentStatus.isComplete) {
            AssistChip(
                onClick = {},
                label = { Text("Brand missing") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ),
            )
        }

        // Missing color warning
        if (color.isNullOrBlank() && enrichmentStatus.isComplete) {
            AssistChip(
                onClick = {},
                label = { Text("Color missing") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/**
 * Section showing suggested attribute additions.
 */
@Composable
private fun SuggestedAdditionsSection(
    suggestions: List<SuggestedAttribute>,
    onAccept: (SuggestedAttribute) -> Unit,
    onDismiss: (SuggestedAttribute) -> Unit,
) {
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
                text = "New attributes detected",
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
                            contentDescription = "Accept",
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
                            contentDescription = "Dismiss",
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
