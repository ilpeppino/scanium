package com.scanium.app.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.scanium.app.items.state.ItemFieldUpdate
import com.scanium.app.model.toImageBitmap

/**
 * Draft state for editing an item's fields.
 */
private data class ItemDraft(
    val labelText: String,
    val recognizedText: String,
    val barcodeValue: String,
)

/**
 * Screen for editing one or more selected items.
 * Uses HorizontalPager for swipe navigation between items.
 *
 * @param itemIds List of item IDs to edit
 * @param onBack Callback when user cancels or completes editing
 * @param itemsViewModel ViewModel for accessing and updating items
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditItemsScreen(
    itemIds: List<String>,
    onBack: () -> Unit,
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
                put(
                    item.id,
                    ItemDraft(
                        labelText = item.labelText.orEmpty(),
                        recognizedText = item.recognizedText.orEmpty(),
                        barcodeValue = item.barcodeValue.orEmpty(),
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
                        Text("Edit Items (${pagerState.currentPage + 1}/${selectedItems.size})")
                    } else {
                        Text("Edit Item")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Cancel",
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
                        text = "No items to edit",
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
                    val draft = drafts[item.id] ?: ItemDraft("", "", "")

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
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Apply all drafts
                            val updates = drafts.mapValues { (_, draft) ->
                                ItemFieldUpdate(
                                    labelText = draft.labelText.ifBlank { null },
                                    recognizedText = draft.recognizedText.ifBlank { null },
                                    barcodeValue = draft.barcodeValue.ifBlank { null },
                                )
                            }
                            itemsViewModel.updateItemsFields(updates)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/**
 * Single page for editing one item's fields.
 */
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Item thumbnail
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
                    contentDescription = "Item thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No image",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Category info (read-only)
        Text(
            text = "Category: ${item.category.displayName}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Editable fields
        OutlinedTextField(
            value = draft.labelText,
            onValueChange = { onDraftChange(draft.copy(labelText = it)) },
            label = { Text("Label / Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = draft.recognizedText,
            onValueChange = { onDraftChange(draft.copy(recognizedText = it)) },
            label = { Text("Recognized Text") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        OutlinedTextField(
            value = draft.barcodeValue,
            onValueChange = { onDraftChange(draft.copy(barcodeValue = it)) },
            label = { Text("Barcode Value") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
