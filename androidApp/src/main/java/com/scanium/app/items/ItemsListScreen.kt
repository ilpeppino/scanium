package com.scanium.app.items

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.scanium.app.media.MediaStoreSaver
import com.scanium.app.model.ImageRef
import com.scanium.app.model.toImageBitmap
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen displaying all detected items in a list.
 *
 * Features:
 * - List of items with thumbnails, category, and price
 * - "Clear all" action in top bar
 * - Tap to select, long-press for details
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
    var selectedAction by remember { mutableStateOf(SelectedItemsAction.SELL_ON_EBAY) }
    var showActionMenu by remember { mutableStateOf(false) }
    var lastDeletedItem by remember { mutableStateOf<ScannedItem?>(null) }
    var lastDeletedWasSelected by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // SEC-010: Prevent screenshots of sensitive item data (prices, images)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun toggleSelection(item: ScannedItem) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }
        selectionMode = selectedIds.isNotEmpty()
    }

    fun deleteItem(item: ScannedItem) {
        val wasSelected = selectedIds.remove(item.id)
        selectionMode = selectedIds.isNotEmpty()
        if (selectedItem?.id == item.id) {
            selectedItem = null
        }

        itemsViewModel.removeItem(item.id)
        lastDeletedItem = item
        lastDeletedWasSelected = wasSelected

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Item deleted",
                actionLabel = "Undo"
            )
            if (result == SnackbarResult.ActionPerformed) {
                lastDeletedItem?.let { deleted ->
                    itemsViewModel.restoreItem(deleted)
                    if (lastDeletedWasSelected) {
                        selectedIds.add(deleted.id)
                    }
                    selectionMode = selectedIds.isNotEmpty()
                }
            } else {
                lastDeletedItem = null
            }
        }
    }

    fun executeAction() {
        when (selectedAction) {
            SelectedItemsAction.SAVE_TO_DEVICE -> {
                scope.launch {
                    // Get selected items with their images (prefer high-res URI, fallback to thumbnail)
                    val selectedItems = items.filter { selectedIds.contains(it.id) }
                    val imagesToSave = selectedItems.mapNotNull { item ->
                        val uri = item.fullImagePath?.let(Uri::parse) ?: item.fullImageUri
                        val imageRef = item.thumbnailRef ?: item.thumbnail
                        if (uri != null || imageRef != null) {
                            Triple(item.id, uri, imageRef)
                        } else null
                    }

                    if (imagesToSave.isEmpty()) {
                        snackbarHostState.showSnackbar("No images to save")
                        return@launch
                    }

                    // Save to gallery (uses high-res URIs when available)
                    val result = MediaStoreSaver.saveImagesToGallery(context, imagesToSave)

                    // Show result to user
                    snackbarHostState.showSnackbar(result.getStatusMessage())

                    // Clear selection after successful save
                    if (result.isSuccess) {
                        selectedIds.clear()
                        selectionMode = false
                    }
                }
            }
            SelectedItemsAction.SELL_ON_EBAY -> {
                onNavigateToSell(selectedIds.toList())
            }
        }
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

                        // Action dropdown button
                        Box {
                            FilledTonalButton(onClick = { showActionMenu = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedAction.displayName)
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select action",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(start = 4.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showActionMenu,
                                onDismissRequest = { showActionMenu = false }
                            ) {
                                SelectedItemsAction.values().forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.displayName) },
                                        onClick = {
                                            selectedAction = action
                                            showActionMenu = false
                                        },
                                        leadingIcon = if (selectedAction == action) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected"
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Execute button
                        TextButton(
                            onClick = { executeAction() },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Text("Go")
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                            val dismissState = rememberDismissState(
                                confirmValueChange = { value ->
                                    if (value == DismissValue.DismissedToEnd) {
                                        deleteItem(item)
                                    }
                                    value == DismissValue.DismissedToEnd
                                }
                            )

                            SwipeToDismiss(
                                state = dismissState,
                                directions = setOf(DismissDirection.StartToEnd),
                                background = {
                                    val color = if (dismissState.targetValue == DismissValue.Default) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    }
                                    val iconTint = if (dismissState.targetValue == DismissValue.Default) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = iconTint
                                        )
                                    }
                                },
                                dismissContent = {
                                    ItemRow(
                                        item = item,
                                        isSelected = selectedIds.contains(item.id),
                                        onClick = {
                                            toggleSelection(item)
                                        },
                                        onLongClick = {
                                            selectedItem = item
                                        },
                                        onRetryClassification = {
                                            itemsViewModel.retryClassification(item.id)
                                        }
                                    )
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
    onLongClick: () -> Unit,
    onRetryClassification: () -> Unit
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
            val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()

            thumbnailBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
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
                // Category with confidence badge and classification status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.category.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    ConfidenceBadge(confidenceLevel = item.confidenceLevel)
                    ClassificationStatusBadge(status = item.classificationStatus)
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

                // Classification error message and retry button
                if (item.classificationStatus == "FAILED" && item.classificationErrorMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = item.classificationErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TextButton(
                            onClick = onRetryClassification,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
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
 * Classification status badge with color coding.
 */
@Composable
private fun ClassificationStatusBadge(status: String) {
    val (backgroundColor, textColor, text) = when (status) {
        "PENDING" -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Classifying..."
        )
        "SUCCESS" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Cloud"
        )
        "FAILED" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Failed"
        )
        "NOT_STARTED" -> return // Don't show badge for not started
        else -> return // Unknown status
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
