package com.scanium.app.items

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.R
import com.scanium.app.audio.AppSound
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.config.FeatureFlags
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.components.AttributeChipsRow
import com.scanium.app.items.components.AttributeEditDialog
import com.scanium.app.items.export.CsvExportWriter
import com.scanium.app.items.export.ZipExportWriter
import com.scanium.app.items.export.bundle.ExportBottomSheet
import com.scanium.app.items.export.bundle.ExportState
import com.scanium.app.items.export.bundle.ExportViewModel
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.app.model.resolveBytes
import com.scanium.app.model.toImageBitmap
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.launch
import java.io.File
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
    onNavigateToEdit: (List<String>) -> Unit,
    onNavigateToGenerateListing: (String) -> Unit = {},
    draftStore: ListingDraftStore,
    itemsViewModel: ItemsViewModel = viewModel(),
    exportViewModel: ExportViewModel = viewModel(),
    tourViewModel: com.scanium.app.ftue.TourViewModel? = null,
) {
    val items by itemsViewModel.items.collectAsState()

    // Item detail sheet state
    var detailSheetItem by remember { mutableStateOf<ScannedItem?>(null) }

    // Attribute edit dialog state
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var editingAttributeKey by remember { mutableStateOf<String?>(null) }
    var editingAttribute by remember { mutableStateOf<ItemAttribute?>(null) }

    val selectedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }
    var lastDeletedItem by remember { mutableStateOf<ScannedItem?>(null) }
    var lastDeletedWasSelected by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val exportPayload by itemsViewModel.exportPayload.collectAsState()

    // Export bundle state
    var showExportSheet by remember { mutableStateOf(false) }
    val exportState by exportViewModel.exportState.collectAsState()
    val bundleResult by exportViewModel.bundleResult.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val soundManager = LocalSoundManager.current
    val csvExportWriter = remember { CsvExportWriter() }
    val zipExportWriter = remember { ZipExportWriter() }

    // FTUE tour state
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // SEC-010: Prevent screenshots of sensitive item data (prices, images)
    // Beta/prod: FLAG_SECURE always applied (FeatureFlags.allowScreenshots = false)
    // Dev: Respects developer mode setting to allow screenshots when enabled
    val settingsRepository = remember { SettingsRepository(context) }
    val userAllowScreenshots by settingsRepository.devAllowScreenshotsFlow.collectAsState(initial = false)
    // Effective value: only allow if both feature flag AND user preference are true
    val allowScreenshots = FeatureFlags.allowScreenshots && userAllowScreenshots

    DisposableEffect(allowScreenshots) {
        val window = (context as? Activity)?.window
        if (!allowScreenshots) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            // Only clear if we set it
            if (!allowScreenshots) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    fun toggleSelection(item: ScannedItem) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }
        selectionMode = selectedIds.isNotEmpty()
        soundManager.play(AppSound.SELECT)
    }

    fun enterSelectionMode(item: ScannedItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedIds.clear()
            selectedIds.add(item.id)
            soundManager.play(AppSound.SELECT)
        }
    }

    fun toggleSelectAll() {
        if (selectedIds.size == items.size) {
            // All selected - clear selection
            selectedIds.clear()
            selectionMode = false
        } else {
            // Select all
            selectedIds.clear()
            selectedIds.addAll(items.map { it.id })
        }
        soundManager.play(AppSound.SELECT)
    }

    fun deleteItem(item: ScannedItem) {
        val wasSelected = selectedIds.remove(item.id)
        selectionMode = selectedIds.isNotEmpty()

        soundManager.play(AppSound.DELETE)
        itemsViewModel.removeItem(item.id)
        lastDeletedItem = item
        lastDeletedWasSelected = wasSelected

        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = "Item deleted",
                    actionLabel = "Undo",
                )
            if (result == SnackbarResult.ActionPerformed) {
                lastDeletedItem?.let { deleted ->
                    itemsViewModel.restoreItem(deleted)
                    if (lastDeletedWasSelected) {
                        selectedIds.add(deleted.id)
                    }
                    selectionMode = selectedIds.isNotEmpty()
                    soundManager.play(AppSound.ITEM_ADDED)
                }
            } else {
                lastDeletedItem = null
            }
        }
    }

    fun shareCsv(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            }
        val chooser = Intent.createChooser(intent, "Share CSV")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                scope.launch {
                    snackbarHostState.showSnackbar("Unable to share CSV")
                }
            }
    }

    LaunchedEffect(itemsViewModel) {
        itemsViewModel.cloudClassificationAlerts.collect { alert ->
            val result =
                snackbarHostState.showSnackbar(
                    message = alert.message,
                    actionLabel = "Retry",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                itemsViewModel.retryClassification(alert.itemId)
            }
        }
    }

    LaunchedEffect(itemsViewModel) {
        itemsViewModel.persistenceAlerts.collect { alert ->
            snackbarHostState.showSnackbar(
                message = alert.message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    fun shareZip(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            }
        val chooser = Intent.createChooser(intent, "Share ZIP")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                scope.launch {
                    snackbarHostState.showSnackbar("Unable to share ZIP")
                }
            }
    }

    /**
     * Share selected items via system share sheet.
     * Shares images if available, otherwise shares a text summary.
     */
    suspend fun shareItems(selectedItems: List<ScannedItem>) {
        if (selectedItems.isEmpty()) {
            snackbarHostState.showSnackbar("Select items to share")
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val shareDir =
            File(context.cacheDir, "share_items").apply {
                if (!exists()) mkdirs()
                // Clean old files
                listFiles()?.forEach { it.delete() }
            }

        // Collect image URIs from items
        val imageUris = mutableListOf<Uri>()
        selectedItems.forEachIndexed { index, item ->
            val imageRef = item.thumbnailRef ?: item.thumbnail
            // Use resolveBytes() to handle both CacheKey (in-memory) and Bytes types
            val bytes = imageRef.resolveBytes()
            if (bytes != null) {
                val shareFile = File(shareDir, "item_${index + 1}.jpg")
                shareFile.writeBytes(bytes.bytes)
                val uri = FileProvider.getUriForFile(context, authority, shareFile)
                imageUris.add(uri)
            }
        }

        // Build text summary
        val textSummary =
            buildString {
                appendLine("Scanium Items (${selectedItems.size})")
                appendLine()
                selectedItems.forEachIndexed { index, item ->
                    appendLine("${index + 1}. ${item.displayLabel}")
                    if (item.formattedPriceRange.isNotBlank()) {
                        appendLine("   Price: ${item.formattedPriceRange}")
                    }
                    item.labelText?.let { label ->
                        appendLine("   Category: $label")
                    }
                }
            }

        val intent =
            if (imageUris.size > 1) {
                // Multiple images: use ACTION_SEND_MULTIPLE
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                    putExtra(Intent.EXTRA_TEXT, textSummary)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Grant permissions for all URIs
                    clipData =
                        ClipData.newRawUri("", imageUris.first()).apply {
                            imageUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                        }
                }
            } else if (imageUris.size == 1) {
                // Single image: use ACTION_SEND
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUris.first())
                    putExtra(Intent.EXTRA_TEXT, textSummary)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(context.contentResolver, "item", imageUris.first())
                }
            } else {
                // No images: share text only
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textSummary)
                }
            }

        val chooser = Intent.createChooser(intent, "Share items")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                snackbarHostState.showSnackbar("Unable to share items")
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
                                contentDescription = "Back",
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
                                    contentDescription = "Clear all",
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = if (selectionMode && selectedIds.isNotEmpty()) 96.dp else 16.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = items,
                                key = { it.id },
                            ) { item ->
                                val dismissState =
                                    rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                deleteItem(item)
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    )

                                val isFirstItem = items.firstOrNull() == item

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    modifier =
                                        Modifier
                                            .animateItemPlacement(
                                                animationSpec =
                                                    spring(
                                                        stiffness = 300f,
                                                        dampingRatio = 0.8f,
                                                    ),
                                            )
                                            .then(
                                                if (tourViewModel != null && isFirstItem) {
                                                    Modifier.tourTarget("items_first_item", tourViewModel)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    backgroundContent = {
                                        val isDismissing = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                                        val containerColor =
                                            if (isDismissing) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        val iconTint =
                                            if (isDismissing) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(containerColor)
                                                    .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = iconTint,
                                            )
                                        }
                                    },
                                ) {
                                    ItemRow(
                                        item = item,
                                        isSelected = selectedIds.contains(item.id),
                                        selectionMode = selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                toggleSelection(item)
                                            } else {
                                                // Navigate to edit screen for this item
                                                onNavigateToEdit(listOf(item.id))
                                            }
                                        },
                                        onLongPress = {
                                            // Long press enters selection mode and selects this item
                                            enterSelectionMode(item)
                                        },
                                        onRetryClassification = {
                                            itemsViewModel.retryClassification(item.id)
                                        },
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
            // Select All button - bottom-left
            FloatingActionButton(
                onClick = { toggleSelectAll() },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp)
                        .semantics {
                            contentDescription = if (selectedIds.size == items.size) {
                                "Deselect all items"
                            } else {
                                "Select all items"
                            }
                        },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                )
            }

            // Delete selected items button - bottom-center
            FloatingActionButton(
                onClick = {
                    val selected = selectedIds.toList()
                    if (selected.isNotEmpty()) {
                        // Delete all selected items
                        selected.forEach { id ->
                            items.find { it.id == id }?.let { item ->
                                itemsViewModel.removeItem(item.id)
                            }
                        }
                        selectedIds.clear()
                        selectionMode = false
                        soundManager.play(AppSound.DELETE)
                        scope.launch {
                            snackbarHostState.showSnackbar("${selected.size} item(s) deleted")
                        }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp)
                        .semantics { contentDescription = "Delete selected items" },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            }

            // Share button with dropdown menu - bottom-right
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
            ) {
                FloatingActionButton(
                    onClick = { showShareMenu = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                        )
                    }
                }

                // Share menu dropdown
                DropdownMenu(
                    expanded = showShareMenu,
                    onDismissRequest = { showShareMenu = false },
                ) {
                    // Share... (system share sheet)
                    DropdownMenuItem(
                        text = { Text("Share…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.cd_share),
                            )
                        },
                        onClick = {
                            showShareMenu = false
                            val selectedItems = items.filter { selectedIds.contains(it.id) }
                            scope.launch {
                                soundManager.play(AppSound.EXPORT)
                                isExporting = true
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    shareItems(selectedItems)
                                }
                                isExporting = false
                            }
                        },
                    )

                    HorizontalDivider()

                    // Export CSV
                    DropdownMenuItem(
                        text = { Text("Export CSV") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = stringResource(R.string.cd_export_csv),
                            )
                        },
                        onClick = {
                            showShareMenu = false
                            val payload = itemsViewModel.createExportPayload(selectedIds.toList())
                            if (payload == null) {
                                scope.launch { snackbarHostState.showSnackbar("Select items to export") }
                                return@DropdownMenuItem
                            }
                            scope.launch {
                                soundManager.play(AppSound.EXPORT)
                                isExporting = true
                                val result =
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        csvExportWriter.writeToCache(context, payload)
                                    }
                                isExporting = false
                                val message =
                                    result.fold(
                                        onSuccess = { file ->
                                            shareCsv(file)
                                            "CSV ready to share"
                                        },
                                        onFailure = {
                                            soundManager.play(AppSound.ERROR)
                                            "Failed to export CSV"
                                        },
                                    )
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )

                    // Export ZIP
                    DropdownMenuItem(
                        text = { Text("Export ZIP") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FolderZip,
                                contentDescription = stringResource(R.string.cd_export_zip),
                            )
                        },
                        onClick = {
                            showShareMenu = false
                            val payload = itemsViewModel.createExportPayload(selectedIds.toList())
                            if (payload == null) {
                                scope.launch { snackbarHostState.showSnackbar("Select items to export") }
                                return@DropdownMenuItem
                            }
                            scope.launch {
                                soundManager.play(AppSound.EXPORT)
                                isExporting = true
                                val result =
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        zipExportWriter.writeToCache(context, payload)
                                    }
                                isExporting = false
                                val message =
                                    result.fold(
                                        onSuccess = { file ->
                                            shareZip(file)
                                            "ZIP ready to share"
                                        },
                                        onFailure = {
                                            soundManager.play(AppSound.ERROR)
                                            "Failed to export ZIP"
                                        },
                                    )
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )

                    HorizontalDivider()

                    // Export Listings (new Phase 5 feature)
                    DropdownMenuItem(
                        text = { Text("Export Listings…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FolderZip,
                                contentDescription = "Export marketplace listings",
                            )
                        },
                        onClick = {
                            showShareMenu = false
                            if (selectedIds.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Select items to export") }
                                return@DropdownMenuItem
                            }
                            soundManager.play(AppSound.EXPORT)
                            // Prepare bundles and show export sheet
                            exportViewModel.prepareExport(
                                items = items,
                                selectedIds = selectedIds.toSet(),
                            )
                            showExportSheet = true
                        },
                    )
                }
            }
        }

        // FTUE Tour Overlays
        if (isTourActive && currentTourStep?.screen == com.scanium.app.ftue.TourScreen.ITEMS_LIST) {
            when (currentTourStep?.key) {
                com.scanium.app.ftue.TourStepKey.ITEMS_ACTION_FAB,
                com.scanium.app.ftue.TourStepKey.ITEMS_SWIPE_DELETE,
                com.scanium.app.ftue.TourStepKey.ITEMS_SELECTION,
                -> {
                    currentTourStep?.let { step ->
                        val bounds = step.targetKey?.let { targetBounds[it] }
                        if (bounds != null || step.targetKey == null) {
                            com.scanium.app.ftue.SpotlightTourOverlay(
                                step = step,
                                targetBounds = bounds,
                                onNext = { tourViewModel?.nextStep() },
                                onBack = { tourViewModel?.previousStep() },
                                onSkip = { tourViewModel?.skipTour() },
                            )
                        }
                    }
                }
                com.scanium.app.ftue.TourStepKey.COMPLETION -> {
                    com.scanium.app.ftue.CompletionOverlay(
                        onDismiss = { tourViewModel?.completeTour() },
                    )
                }
                else -> { /* Camera steps */ }
            }
        }
    }

    // Item detail sheet for viewing all attributes
    detailSheetItem?.let { item ->
        ItemDetailSheet(
            item = item,
            onDismiss = { detailSheetItem = null },
            onAttributeEdit = { key, attribute ->
                // Open attribute edit dialog
                editingItemId = item.id
                editingAttributeKey = key
                editingAttribute = attribute
            },
            onGenerateListing = if (item.attributes.isNotEmpty()) {
                {
                    // Navigate to generate listing screen with this item
                    detailSheetItem = null
                    onNavigateToGenerateListing(item.id)
                }
            } else {
                null
            },
        )
    }

    // Attribute edit dialog
    if (editingItemId != null && editingAttributeKey != null && editingAttribute != null) {
        // Get the item's vision attributes and detected value for suggestions
        val editingItem = items.find { it.id == editingItemId }
        val detectedValue = editingItem?.detectedAttributes?.get(editingAttributeKey)?.value

        AttributeEditDialog(
            attributeKey = editingAttributeKey!!,
            attribute = editingAttribute!!,
            visionAttributes = editingItem?.visionAttributes
                ?: com.scanium.shared.core.models.items.VisionAttributes.EMPTY,
            detectedValue = detectedValue,
            onDismiss = {
                editingItemId = null
                editingAttributeKey = null
                editingAttribute = null
            },
            onConfirm = { updatedAttribute ->
                // Update the attribute in the ViewModel
                itemsViewModel.updateItemAttribute(
                    itemId = editingItemId!!,
                    attributeKey = editingAttributeKey!!,
                    attribute = updatedAttribute,
                )
                // Close the dialog
                editingItemId = null
                editingAttributeKey = null
                editingAttribute = null
                // Refresh the detail sheet if it's still open
                detailSheetItem?.let { currentItem ->
                    detailSheetItem = itemsViewModel.getItem(currentItem.id)
                }
            },
        )
    }

    // Export bottom sheet
    if (showExportSheet) {
        ExportBottomSheet(
            bundleResult = bundleResult,
            exportState = exportState,
            onDismiss = {
                showExportSheet = false
                exportViewModel.reset()
            },
            onExportText = {
                exportViewModel.exportText()
            },
            onExportZip = {
                exportViewModel.exportZip()
            },
            onCopyText = {
                val copied = exportViewModel.copyTextToClipboard()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (copied) "Copied to clipboard" else "Failed to copy",
                    )
                }
            },
            onShareZip = { zipFile ->
                val intent = exportViewModel.createZipShareIntent(zipFile)
                val chooser = Intent.createChooser(intent, "Share ZIP")
                runCatching { context.startActivity(chooser) }
                    .onFailure {
                        soundManager.play(AppSound.ERROR)
                        scope.launch {
                            snackbarHostState.showSnackbar("Unable to share ZIP")
                        }
                    }
            },
            onShareText = { text ->
                val intent = exportViewModel.createTextShareIntent(text)
                val chooser = Intent.createChooser(intent, "Share Listings")
                runCatching { context.startActivity(chooser) }
                    .onFailure {
                        soundManager.play(AppSound.ERROR)
                        scope.launch {
                            snackbarHostState.showSnackbar("Unable to share text")
                        }
                    }
            },
        )
    }
}

/**
 * Single item row in the list.
 *
 * Gesture behavior:
 * - Tap: If in selection mode, toggles selection. Otherwise, opens edit screen.
 * - Long press: Enters selection mode and selects this item.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: ScannedItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRetryClassification: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        buildString {
                            append(item.displayLabel)
                            append(". ")
                            append(item.formattedPriceRange)
                            // Diagnostic info only in dev builds
                            if (FeatureFlags.showItemDiagnostics) {
                                append(". ")
                                append("Confidence: ${item.confidenceLevel.displayName}")
                                when (item.classificationStatus) {
                                    "PENDING" -> append(". Classification in progress")
                                    "FAILED" -> append(". Classification failed")
                                    else -> {}
                                }
                            }
                            if (isSelected) {
                                append(". Selected")
                            }
                            if (selectionMode) {
                                append(". Tap to toggle selection")
                            } else {
                                append(". Tap to edit, long press to select")
                            }
                        }
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
                        },
                        onLongPress = {
                            onLongPress()
                        },
                        onTap = {
                            onClick()
                        },
                    )
                },
        colors =
            if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                CardDefaults.cardColors()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Thumbnail
            val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()

            thumbnailBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Item thumbnail",
                    modifier =
                        Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit,
                )
            } ?: run {
                // Placeholder if no thumbnail
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Item info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Category with confidence badge and classification status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Diagnostic badges only shown in dev builds
                    if (FeatureFlags.showItemDiagnostics) {
                        ConfidenceBadge(confidenceLevel = item.confidenceLevel)
                        ClassificationStatusBadge(status = item.classificationStatus)
                    }
                    // Enrichment status badge (always visible when enriching)
                    EnrichmentStatusBadge(status = item.enrichmentStatus)
                }

                // Price display: user price if set, otherwise estimated range
                val priceText = item.formattedUserPrice ?: item.formattedPriceRange
                if (priceText.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = priceText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.userPriceCents != null) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        // Show condition badge if set
                        item.condition?.let { condition ->
                            ConditionBadge(condition = condition)
                        }
                    }
                } else {
                    // Show only condition badge if no price
                    item.condition?.let { condition ->
                        ConditionBadge(condition = condition)
                    }
                }

                // Attribute chips (show up to 3 attributes)
                if (item.attributes.isNotEmpty()) {
                    AttributeChipsRow(
                        attributes = item.attributes,
                        maxVisible = 3,
                        compact = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Timestamp (and confidence percentage in dev builds)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Confidence percentage only shown in dev builds
                    if (FeatureFlags.showItemDiagnostics) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.formattedConfidence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Classification error message and retry button
                if (item.classificationStatus == "FAILED") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = stringResource(R.string.cd_classification_failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = item.classificationErrorMessage ?: "Classification failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TextButton(
                            onClick = onRetryClassification,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier =
                                Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .semantics {
                                        contentDescription = "Retry classification for this item"
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_retry),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Listing status badge and view listing button
                if (item.listingStatus != ItemListingStatus.NOT_LISTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
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
                                modifier =
                                    Modifier
                                        .sizeIn(minHeight = 48.dp)
                                        .semantics {
                                            contentDescription = "View listing on marketplace"
                                        },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = stringResource(R.string.cd_view_external),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelSmall,
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
        modifier =
            Modifier
                .align(Alignment.Center)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No items detected yet",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use the camera to scan objects",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Export to spreadsheets, chat apps, or marketplaces.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Confidence level badge with color coding.
 */
@Composable
private fun ConfidenceBadge(confidenceLevel: ConfidenceLevel) {
    val (backgroundColor, textColor) =
        when (confidenceLevel) {
            ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            ConfidenceLevel.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = confidenceLevel.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Listing status badge with color coding.
 */
@Composable
private fun ListingStatusBadge(status: ItemListingStatus) {
    val (backgroundColor, textColor, text) =
        when (status) {
            ItemListingStatus.LISTED_ACTIVE ->
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    status.displayName,
                )
            ItemListingStatus.LISTING_IN_PROGRESS ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    status.displayName,
                )
            ItemListingStatus.LISTING_FAILED ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    status.displayName,
                )
            ItemListingStatus.NOT_LISTED -> return // Don't show badge for not listed
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Classification status badge with color coding.
 */
@Composable
private fun ClassificationStatusBadge(status: String) {
    val (backgroundColor, textColor, text) =
        when (status) {
            "PENDING" ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    "Classifying...",
                )
            "SUCCESS" ->
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    "Cloud",
                )
            "FAILED" ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    "Failed",
                )
            "NOT_STARTED" -> return // Don't show badge for not started
            else -> return // Unknown status
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Item condition badge with color coding.
 */
@Composable
private fun ConditionBadge(condition: ItemCondition) {
    val (backgroundColor, textColor) =
        when (condition) {
            ItemCondition.NEW ->
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            ItemCondition.AS_GOOD_AS_NEW ->
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            ItemCondition.USED ->
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            ItemCondition.REFURBISHED ->
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = condition.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Enrichment status badge showing enriching/enriched state.
 */
@Composable
private fun EnrichmentStatusBadge(
    status: com.scanium.shared.core.models.items.EnrichmentLayerStatus,
) {
    val (backgroundColor, textColor, text) =
        when {
            status.isEnriching -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                "Enriching...",
            )
            status.isComplete && status.hasAnyResults -> Triple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Enriched",
            )
            status.layerA == com.scanium.shared.core.models.items.LayerState.FAILED &&
                status.layerB == com.scanium.shared.core.models.items.LayerState.FAILED -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Failed",
            )
            else -> return // Don't show badge for pending state
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
