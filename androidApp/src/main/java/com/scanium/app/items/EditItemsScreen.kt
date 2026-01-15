package com.scanium.app.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.items.state.ItemFieldUpdate
import com.scanium.app.model.toImageBitmap
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Draft state for editing an item's fields.
 */
private data class ItemDraft(
    val labelText: String,
    val recognizedText: String,
    val barcodeValue: String,
    val priceText: String,
    val condition: ItemCondition?,
    val category: com.scanium.app.ml.ItemCategory,
)

/**
 * Screen for editing one or more selected items.
 * Uses HorizontalPager for swipe navigation between items.
 *
 * @param itemIds List of item IDs to edit
 * @param onBack Callback when user cancels or completes editing
 * @param onNavigateToAssistant Callback to open AI assistant with the current item
 * @param itemsViewModel ViewModel for accessing and updating items
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditItemsScreen(
    itemIds: List<String>,
    onBack: () -> Unit,
    onNavigateToAssistant: (List<String>) -> Unit,
    itemsViewModel: ItemsViewModel,
) {
    val allItems by itemsViewModel.items.collectAsState()
    val selectedItems = remember(allItems, itemIds) {
        allItems.filter { it.id in itemIds }
    }

    // Draft state for each item (keyed by item ID)
    val drafts = remember(selectedItems) {
        mutableStateMapOf<String, ItemDraft>().apply {
            selectedItems.forEach { item ->
                // Pre-fill label from vision attributes if empty
                val suggestedLabel = if (item.labelText.isNullOrBlank()) {
                    buildSuggestedLabel(item)
                } else {
                    item.labelText
                }

                // Pre-fill recognized text from vision attributes if empty
                val suggestedRecognizedText = item.recognizedText ?: item.visionAttributes.ocrText ?: ""

                put(
                    item.id,
                    ItemDraft(
                        labelText = suggestedLabel.orEmpty(),
                        recognizedText = suggestedRecognizedText,
                        barcodeValue = item.barcodeValue.orEmpty(),
                        priceText = item.userPriceCents?.let { formatCentsToPrice(it) } ?: "",
                        condition = item.condition,
                        category = item.category,
                    ),
                )
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { selectedItems.size },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedItems.size > 1) {
                        Text(
                            stringResource(
                                R.string.edit_items_title,
                                pagerState.currentPage + 1,
                                selectedItems.size,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.edit_item_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel),
                        )
                    }
                },
                actions = {
                    // AI Assistant button - opens assistant with current item
                    IconButton(
                        onClick = {
                            val currentItem = selectedItems.getOrNull(pagerState.currentPage)
                            if (currentItem != null) {
                                onNavigateToAssistant(listOf(currentItem.id))
                            }
                        },
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.assistant_ai_assistant),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (selectedItems.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.edit_items_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Pager for swiping between items
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val item = selectedItems[page]
                    val draft = drafts[item.id] ?: ItemDraft(
                        labelText = "",
                        recognizedText = "",
                        barcodeValue = "",
                        priceText = "",
                        condition = null,
                        category = item.category,
                    )

                    ItemEditPage(
                        item = item,
                        draft = draft,
                        onDraftChange = { newDraft ->
                            drafts[item.id] = newDraft
                        },
                    )
                }

                // Page indicator for multiple items
                if (selectedItems.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(selectedItems.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        shape = MaterialTheme.shapes.small,
                                    ),
                            )
                        }
                    }
                }
            }

            // Bottom action buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    Button(
                        onClick = {
                            // Apply all drafts
                            val updates = drafts.mapValues { (itemId, draft) ->
                                val originalItem = selectedItems.find { it.id == itemId }
                                val priceCents = parsePriceToCents(draft.priceText)
                                val originalPriceCents = originalItem?.userPriceCents
                                val originalCondition = originalItem?.condition

                                val originalCategory = originalItem?.category
                                ItemFieldUpdate(
                                    labelText = draft.labelText.ifBlank { null },
                                    recognizedText = draft.recognizedText.ifBlank { null },
                                    barcodeValue = draft.barcodeValue.ifBlank { null },
                                    userPriceCents = priceCents,
                                    clearUserPriceCents = priceCents == null && originalPriceCents != null,
                                    condition = draft.condition,
                                    clearCondition = draft.condition == null && originalCondition != null,
                                    category = if (draft.category != originalCategory) draft.category else null,
                                )
                            }
                            itemsViewModel.updateItemsFields(updates)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}

/**
 * Single page for editing one item's fields.
 * Scrollable layout with consistent OutlinedTextField styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditPage(
    item: ScannedItem,
    draft: ItemDraft,
    onDraftChange: (ItemDraft) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Item thumbnail - constrained height for compact layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = stringResource(R.string.items_thumbnail),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.items_no_image),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Label / Name field
        OutlinedTextField(
            value = draft.labelText,
            onValueChange = { onDraftChange(draft.copy(labelText = it)) },
            label = { Text(stringResource(R.string.edit_item_label_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Category dropdown
        CategoryDropdown(
            selectedCategory = draft.category,
            onCategorySelected = { category ->
                onDraftChange(draft.copy(category = category))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Price input
        OutlinedTextField(
            value = draft.priceText,
            onValueChange = { newValue ->
                // Validate: only allow valid price characters
                val filtered = newValue.filter { it.isDigit() || it == '.' || it == ',' }
                // Prevent multiple decimal separators
                val decimalCount = filtered.count { it == '.' || it == ',' }
                if (decimalCount <= 1) {
                    onDraftChange(draft.copy(priceText = filtered))
                }
            },
            label = { Text(stringResource(R.string.edit_item_price_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = { Text(stringResource(R.string.edit_item_price_prefix)) },
            isError = draft.priceText.isNotEmpty() && parsePriceToCents(draft.priceText) == null && draft.priceText != "0",
        )

        // Condition dropdown
        ConditionDropdown(
            selectedCondition = draft.condition,
            onConditionSelected = { condition ->
                onDraftChange(draft.copy(condition = condition))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Recognized Text field - show OCR results (multi-line for full text)
        OutlinedTextField(
            value = draft.recognizedText,
            onValueChange = { onDraftChange(draft.copy(recognizedText = it)) },
            label = { Text(stringResource(R.string.edit_item_recognized_text_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 120.dp),
            singleLine = false,
            maxLines = 4,
        )

        // Barcode Value field
        OutlinedTextField(
            value = draft.barcodeValue,
            onValueChange = { onDraftChange(draft.copy(barcodeValue = it)) },
            label = { Text(stringResource(R.string.edit_item_barcode_value_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

/**
 * Dropdown menu for selecting item condition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionDropdown(
    selectedCondition: ItemCondition?,
    onConditionSelected: (ItemCondition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedCondition?.let { ItemLocalizer.getConditionName(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_item_condition_label)) },
            placeholder = { Text(stringResource(R.string.common_select)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Option to clear the condition
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_not_set), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {
                    onConditionSelected(null)
                    expanded = false
                },
            )

            // All condition options
            ItemCondition.entries.forEach { condition ->
                DropdownMenuItem(
                    text = { Text(ItemLocalizer.getConditionName(condition)) },
                    onClick = {
                        onConditionSelected(condition)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Format cents to a price string (e.g., 1250 -> "12.50").
 */
private fun formatCentsToPrice(cents: Long): String {
    val euros = cents / 100.0
    val formatter = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
    return formatter.format(euros)
}

/**
 * Parse a price string to cents (e.g., "12.50" -> 1250).
 * Returns null if the string is empty or invalid.
 */
private fun parsePriceToCents(priceText: String): Long? {
    if (priceText.isBlank()) return null

    // Normalize decimal separator
    val normalized = priceText.replace(',', '.')

    return try {
        val euros = normalized.toDouble()
        if (euros < 0 || euros > 1_000_000) {
            null // Invalid range
        } else {
            (euros * 100).toLong()
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Build a suggested label from vision attributes.
 * Prioritizes: brand + model > brand > primary label from vision.
 */
private fun buildSuggestedLabel(item: ScannedItem): String? {
    val brand = item.attributes["brand"]?.value ?: item.visionAttributes.primaryBrand
    val model = item.attributes["model"]?.value ?: item.visionAttributes.primaryModel
    val itemType = item.attributes["itemType"]?.value ?: item.visionAttributes.itemType

    return when {
        !brand.isNullOrBlank() && !model.isNullOrBlank() -> "$brand $model"
        !brand.isNullOrBlank() && !itemType.isNullOrBlank() -> "$brand $itemType"
        !itemType.isNullOrBlank() -> itemType
        !brand.isNullOrBlank() -> brand
        else -> null
    }
}

/**
 * Dropdown menu for selecting item category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: com.scanium.app.ml.ItemCategory,
    onCategorySelected: (com.scanium.app.ml.ItemCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedCategory.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            com.scanium.app.ml.ItemCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(ItemLocalizer.getCategoryName(category)) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
