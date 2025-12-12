package com.scanium.app.items

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen displaying all detected items in a list.
 *
 * Features:
 * - List of items with thumbnails, category, and price
 * - "Clear all" action in top bar
 * - Tap item to see detail dialog
 * - Empty state when no items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSell: (List<String>) -> Unit,
    itemsViewModel: ItemsViewModel = viewModel()
) {
    val items by itemsViewModel.items.collectAsState()
    var selectedItem by remember { mutableStateOf<ScannedItem?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }

    fun toggleSelection(item: ScannedItem) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }
        selectionMode = selectedIds.isNotEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "Select items" else "Detected Items") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(onClick = {
                            selectedIds.clear()
                            selectionMode = false
                        }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = { onNavigateToSell(selectedIds.toList()) }) {
                            Text("Sell on eBay")
                        }
                    } else if (items.isNotEmpty()) {
                        IconButton(onClick = { itemsViewModel.clearAllItems() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear all"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                items.isEmpty() -> {
                    // Empty state
                    EmptyItemsContent()
                }
                else -> {
                    // Items list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = items, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                isSelected = selectedIds.contains(item.id),
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelection(item)
                                    } else {
                                        selectedItem = item
                                    }
                                },
                                onLongClick = {
                                    toggleSelection(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail dialog
    selectedItem?.let { item ->
        ItemDetailDialog(
            item = item,
            onDismiss = { selectedItem = null }
        )
    }
}

/**
 * Single item row in the list.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: ScannedItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            item.thumbnail?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Item thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentScale = ContentScale.Crop
                )
            } ?: run {
                // Placeholder if no thumbnail
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Item info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Category with confidence badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.category.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    ConfidenceBadge(confidenceLevel = item.confidenceLevel)
                }

                Text(
                    text = item.formattedPriceRange,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // Timestamp and confidence percentage
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.formattedConfidence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Listing status badge and view listing button
                if (item.listingStatus != ItemListingStatus.NOT_LISTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        ListingStatusBadge(status = item.listingStatus)

                        // "View listing" button for active listings
                        if (item.listingStatus == ItemListingStatus.LISTED_ACTIVE && item.listingUrl != null) {
                            val context = LocalContext.current
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.listingUrl))
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "View listing",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state content.
 */
@Composable
private fun BoxScope.EmptyItemsContent() {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No items detected yet",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use the camera to scan objects",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Confidence level badge with color coding.
 */
@Composable
private fun ConfidenceBadge(confidenceLevel: ConfidenceLevel) {
    val (backgroundColor, textColor) = when (confidenceLevel) {
        ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ConfidenceLevel.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor
    ) {
        Text(
            text = confidenceLevel.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Listing status badge with color coding.
 */
@Composable
private fun ListingStatusBadge(status: ItemListingStatus) {
    val (backgroundColor, textColor, text) = when (status) {
        ItemListingStatus.LISTED_ACTIVE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            status.displayName
        )
        ItemListingStatus.LISTING_IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            status.displayName
        )
        ItemListingStatus.LISTING_FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            status.displayName
        )
        ItemListingStatus.NOT_LISTED -> return // Don't show badge for not listed
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Formats timestamp to readable string.
 */
private fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
