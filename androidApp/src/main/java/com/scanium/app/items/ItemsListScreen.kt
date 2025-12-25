package com.scanium.app.items

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.scanium.app.model.ImageRef
import com.scanium.app.model.toImageBitmap
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.ftue.tourTarget
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAssistant: (List<String>) -> Unit,
    draftStore: ListingDraftStore,
    itemsViewModel: ItemsViewModel = viewModel(),
    tourViewModel: com.scanium.app.ftue.TourViewModel? = null
) {
    val items by itemsViewModel.items.collectAsState()
    var previewDraft by remember { mutableStateOf<ListingDraft?>(null) }
    
    // Press-and-hold preview state
    var previewItem by remember { mutableStateOf<ScannedItem?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }
    var lastDeletedItem by remember { mutableStateOf<ScannedItem?>(null) }
    var lastDeletedWasSelected by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val exportPayload by itemsViewModel.exportPayload.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // FTUE tour state
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

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

    LaunchedEffect(itemsViewModel) {
        itemsViewModel.cloudClassificationAlerts.collect { alert ->
            val result = snackbarHostState.showSnackbar(
                message = alert.message,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                itemsViewModel.retryClassification(alert.itemId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = if (selectionMode && selectedIds.isNotEmpty()) 96.dp else 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = items,
                                key = { it.id }
                            ) { item ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.StartToEnd) {
                                            deleteItem(item)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                val isFirstItem = items.firstOrNull() == item

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    modifier = Modifier
                                        .animateItemPlacement(
                                            animationSpec = spring(
                                                stiffness = 300f,
                                                dampingRatio = 0.8f
                                            )
                                        )
                                        .then(
                                            if (tourViewModel != null && isFirstItem) {
                                                Modifier.tourTarget("items_first_item", tourViewModel)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    backgroundContent = {
                                        val isDismissing = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                                        val containerColor = if (isDismissing) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val iconTint = if (isDismissing) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(containerColor)
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = iconTint
                                            )
                                        }
                                    }
                                ) {
                                    ItemRow(
                                        item = item,
                                        isSelected = selectedIds.contains(item.id),
                                        onClick = {
                                            toggleSelection(item)
                                        },
                                        onPreviewStart = { item, bounds ->
                                            previewItem = item
                                            previewBounds = bounds
                                        },
                                        onPreviewEnd = {
                                            previewItem = null
                                        },
                                        onRetryClassification = {
                                            itemsViewModel.retryClassification(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay controls when items are selected
        if (selectionMode && selectedIds.isNotEmpty()) {
            // AI Assistant button - bottom-right
            FloatingActionButton(
                onClick = {
                    val selected = selectedIds.toList()
                    if (selected.isNotEmpty()) {
                        onNavigateToAssistant(selected)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Select items to ask about") }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
                    .then(
                        if (tourViewModel != null) {
                            Modifier.tourTarget("items_ai_assistant", tourViewModel)
                        } else {
                            Modifier
                        }
                    ),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI assistant"
                )
            }

            // Export CTA
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(0.65f)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (tourViewModel != null) {
                                Modifier.tourTarget("items_action_fab", tourViewModel)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                val payload = itemsViewModel.createExportPayload(selectedIds.toList())
                                if (payload != null) {
                                    showExportDialog = true
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("Select items to export") }
                                }
                            }
                        ) {
                            Text("Export")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use items outside Scanium",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Full screen draft preview overlay
        DraftPreviewOverlay(
            item = previewItem,
            sourceBounds = previewBounds,
            isVisible = previewItem != null
        )

        // FTUE Tour Overlays
        if (isTourActive && currentTourStep?.screen == com.scanium.app.ftue.TourScreen.ITEMS_LIST) {
            when (currentTourStep?.key) {
                com.scanium.app.ftue.TourStepKey.ITEMS_ACTION_FAB,
                com.scanium.app.ftue.TourStepKey.ITEMS_AI_ASSISTANT,
                com.scanium.app.ftue.TourStepKey.ITEMS_SWIPE_DELETE,
                com.scanium.app.ftue.TourStepKey.ITEMS_SELECTION -> {
                    currentTourStep?.let { step ->
                        val bounds = step.targetKey?.let { targetBounds[it] }
                        if (bounds != null || step.targetKey == null) {
                            com.scanium.app.ftue.SpotlightTourOverlay(
                                step = step,
                                targetBounds = bounds,
                                onNext = { tourViewModel?.nextStep() },
                                onBack = { tourViewModel?.previousStep() },
                                onSkip = { tourViewModel?.skipTour() }
                            )
                        }
                    }
                }
                com.scanium.app.ftue.TourStepKey.COMPLETION -> {
                    com.scanium.app.ftue.CompletionOverlay(
                        onDismiss = { tourViewModel?.completeTour() }
                    )
                }
                else -> { /* Camera steps */ }
            }
        }
    }

    // Draft preview dialog (legacy/fallback if needed, but primary is overlay now)
    previewDraft?.let { draft ->
        DraftPreviewDialog(
            draft = draft,
            onDismiss = { previewDraft = null }
        )
    }

    if (showExportDialog) {
        val exportCount = exportPayload?.items?.size ?: selectedIds.size
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export formats coming next") },
            text = {
                Text(
                    "Selected $exportCount item${if (exportCount == 1) "" else "s"}."
                )
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("OK")
                }
            }
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
    onPreviewStart: (ScannedItem, Rect) -> Unit,
    onPreviewEnd: () -> Unit,
    onRetryClassification: () -> Unit
) {
    var currentBounds by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                currentBounds = coordinates.boundsInWindow()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        val released = tryAwaitRelease()
                        if (released) {
                            interactionSource.emit(PressInteraction.Release(press))
                        } else {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        }
                        // Always trigger preview end on release/cancel
                        onPreviewEnd()
                    },
                    onLongPress = {
                        currentBounds?.let { onPreviewStart(item, it) }
                    },
                    onTap = { 
                        onClick() 
                    }
                )
            },
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
                        )
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit
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
                        text = item.displayLabel,
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

@Composable
private fun DraftPreviewDialog(
    draft: ListingDraft,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Draft preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Title: ${draft.title.value.orEmpty()}")
                Text("Description: ${draft.description.value.orEmpty()}")
                Text("Price: ${formatPrice(draft.price.value)}")
                Text("Condition: ${draft.fields[com.scanium.app.listing.DraftFieldKey.CONDITION]?.value.orEmpty()}")
                Text("Status: ${draft.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
            }
        }
    )
}

private fun formatPrice(value: Double?): String {
    if (value == null) return ""
    val formatter = java.text.DecimalFormat("0.***REMOVED******REMOVED***")
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 0
    return formatter.format(value)
}
