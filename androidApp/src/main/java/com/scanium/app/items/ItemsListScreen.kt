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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.scanium.app.R
import com.scanium.app.audio.AppSound
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.config.FeatureFlags
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.tourTarget
import com.scanium.app.ui.shimmerEffect
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
 *
 * IMPORTANT: ViewModels must use hiltViewModel() not viewModel() since they are @HiltViewModel
 * classes that require dependency injection. Using viewModel() will cause a crash because the
 * default ViewModelProvider cannot instantiate ViewModels that have injected dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAssistant: (List<String>) -> Unit,
    onNavigateToEdit: (List<String>) -> Unit,
    onNavigateToGenerateListing: (String) -> Unit = {},
    draftStore: ListingDraftStore,
    itemsViewModel: ItemsViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
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
    val haptic = LocalHapticFeedback.current
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
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun enterSelectionMode(item: ScannedItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedIds.clear()
            selectedIds.add(item.id)
            soundManager.play(AppSound.SELECT)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                    message = context.getString(R.string.items_snackbar_item_deleted),
                    actionLabel = context.getString(R.string.common_undo),
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
        val chooser = Intent.createChooser(intent, context.getString(R.string.items_share_csv_title))
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.items_share_csv_failed))
                }
            }
    }

    LaunchedEffect(itemsViewModel) {
        itemsViewModel.cloudClassificationAlerts.collect { alert ->
            val result =
                snackbarHostState.showSnackbar(
                    message = alert.message,
                    actionLabel = context.getString(R.string.common_retry),
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
        val chooser = Intent.createChooser(intent, context.getString(R.string.items_share_zip_title))
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.items_share_zip_failed))
                }
            }
    }

    /**
     * Share selected items via system share sheet.
     * Shares images if available, otherwise shares a text summary.
     */
    suspend fun shareItems(selectedItems: List<ScannedItem>) {
        if (selectedItems.isEmpty()) {
            snackbarHostState.showSnackbar(context.getString(R.string.items_select_items_to_share))
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
                appendLine(context.getString(R.string.items_share_summary_title, selectedItems.size))
                appendLine()
                selectedItems.forEachIndexed { index, item ->
                    appendLine("${index + 1}. ${item.displayLabel}")
                    if (item.formattedPriceRange.isNotBlank()) {
                        appendLine(context.getString(R.string.items_share_summary_price, item.formattedPriceRange))
                    }
                    item.labelText?.let { label ->
                        appendLine(context.getString(R.string.items_share_summary_category, label))
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

        val chooser = Intent.createChooser(intent, context.getString(R.string.items_share_items_title))
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { context.startActivity(chooser) }
            .onFailure {
                soundManager.play(AppSound.ERROR)
                snackbarHostState.showSnackbar(context.getString(R.string.items_share_items_failed))
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (selectionMode) {
                                stringResource(R.string.items_select_items_title)
                            } else {
                                stringResource(R.string.items_detected_items_title)
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            TextButton(onClick = {
                                selectedIds.clear()
                                selectionMode = false
                            }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        } else if (items.isNotEmpty()) {
                            IconButton(onClick = { itemsViewModel.clearAllItems() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.items_clear_all),
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            // UI state holder for content
            val listState = ItemsListState(
                selectedIds = selectedIds.toSet(),
                selectionMode = selectionMode,
            )

            ItemsListContent(
                items = items,
                state = listState,
                onItemClick = { item ->
                    if (selectionMode) {
                        toggleSelection(item)
                    } else {
                        // Navigate to edit screen for this item
                        onNavigateToEdit(listOf(item.id))
                    }
                },
                onItemLongPress = { item ->
                    // Long press enters selection mode and selects this item
                    enterSelectionMode(item)
                },
                onDeleteItem = { item ->
                    deleteItem(item)
                },
                onRetryClassification = { item ->
                    itemsViewModel.retryClassification(item.id)
                },
                tourViewModel = tourViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }

        // Overlay controls when items are selected
        if (selectionMode && selectedIds.isNotEmpty()) {
            val selectAllLabel = stringResource(R.string.items_select_all)
            val deselectAllLabel = stringResource(R.string.items_deselect_all)
            val deleteSelectedLabel = stringResource(R.string.items_delete_selected)

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
                                deselectAllLabel
                            } else {
                                selectAllLabel
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
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.items_snackbar_items_deleted, selected.size),
                            )
                        }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp)
                        .semantics { contentDescription = deleteSelectedLabel },
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
                    modifier = if (tourViewModel != null) {
                        Modifier.tourTarget("items_share_button", tourViewModel)
                    } else {
                        Modifier
                    }
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
                            contentDescription = stringResource(R.string.common_share),
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
                        text = { Text(stringResource(R.string.items_share_ellipsis)) },
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
                        text = { Text(stringResource(R.string.items_export_csv)) },
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
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.items_select_items_to_export),
                                    )
                                }
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
                                            context.getString(R.string.items_export_csv_ready)
                                        },
                                        onFailure = {
                                            soundManager.play(AppSound.ERROR)
                                            context.getString(R.string.items_export_csv_failed)
                                        },
                                    )
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )

                    // Export ZIP
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_export_zip)) },
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
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.items_select_items_to_export),
                                    )
                                }
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
                                        onSuccess = { exportResult ->
                                            shareZip(exportResult.zipFile)
                                            if (exportResult.photosSkipped > 0) {
                                                context.getString(
                                                    R.string.items_export_zip_partial,
                                                    exportResult.photosWritten,
                                                    exportResult.photosRequested,
                                                )
                                            } else {
                                                context.getString(R.string.items_export_zip_ready)
                                            }
                                        },
                                        onFailure = {
                                            soundManager.play(AppSound.ERROR)
                                            context.getString(R.string.items_export_zip_failed)
                                        },
                                    )
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )

                    HorizontalDivider()

                    // Export Listings (new Phase 5 feature)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.items_export_listings_ellipsis)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FolderZip,
                                contentDescription = stringResource(R.string.items_export_marketplace),
                            )
                        },
                        onClick = {
                            showShareMenu = false
                            if (selectedIds.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.items_select_items_to_export),
                                    )
                                }
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
                com.scanium.app.ftue.TourStepKey.SHARE_BUNDLE -> {
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
                else -> { /* Camera or EditItem steps */ }
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
                        if (copied) {
                            context.getString(R.string.common_copied_to_clipboard)
                        } else {
                            context.getString(R.string.common_copy_failed)
                        },
                    )
                }
            },
            onShareZip = { zipFile ->
                val intent = exportViewModel.createZipShareIntent(zipFile)
                val chooser = Intent.createChooser(intent, context.getString(R.string.items_share_zip_title))
                runCatching { context.startActivity(chooser) }
                    .onFailure {
                        soundManager.play(AppSound.ERROR)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.items_share_zip_failed))
                        }
                    }
            },
            onShareText = { text ->
                val intent = exportViewModel.createTextShareIntent(text)
                val chooser = Intent.createChooser(intent, context.getString(R.string.items_share_listings_title))
                runCatching { context.startActivity(chooser) }
                    .onFailure {
                        soundManager.play(AppSound.ERROR)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.items_share_text_failed))
                        }
                    }
            },
        )
    }
}
