package com.scanium.app.items.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.AttributeDisplayFormatter
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemCondition
import com.scanium.shared.core.models.model.ImageRef

/**
 * Redesigned Edit Item screen with structured labeled fields (Phase 3 UX redesign).
 *
 * Features:
 * - Photos row at top
 * - Scrollable form with labeled single-line editable fields
 * - Inline clear "X" per field
 * - Return/Next moves focus to next field
 * - Notes is multiline and allows newline
 * - AI export uses structured attributes + photos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreenV3(
    itemId: String,
    onBack: () -> Unit,
    onAddPhotos: (String) -> Unit,
    onAiGenerate: (String) -> Unit,
    itemsViewModel: ItemsViewModel,
    exportAssistantViewModelFactory: ExportAssistantViewModel.Factory? = null,
    tourViewModel: com.scanium.app.ftue.TourViewModel? = null,
) {
    val context = LocalContext.current
    val allItems by itemsViewModel.items.collectAsState()
    val item by remember(allItems, itemId) {
        derivedStateOf { allItems.find { it.id == itemId } }
    }

    // FTUE Tour State
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // Export Assistant state
    var showExportAssistantSheet by remember { mutableStateOf(false) }
    val exportAssistantViewModel = remember(exportAssistantViewModelFactory, itemId) {
        exportAssistantViewModelFactory?.create(itemId, itemsViewModel)
    }

    // Photo Gallery Dialog state
    var showPhotoGallery by remember { mutableStateOf(false) }
    var galleryStartIndex by remember { mutableStateOf(0) }

    // Photo Selection state for multi-select deletion
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeletingPhotos by remember { mutableStateOf(false) }

    // Field state (local draft, synchronized with item.attributes)
    // LOCALIZATION: Display values are localized for UI; save canonicalizes back to English
    var brandField by remember(item) { mutableStateOf(item?.attributes?.get("brand")?.value ?: "") }
    var productTypeField by remember(item) { mutableStateOf(item?.attributes?.get("itemType")?.value ?: "") }
    var modelField by remember(item) { mutableStateOf(item?.attributes?.get("model")?.value ?: "") }
    // Color: localize canonical value for display
    var colorField by remember(item) {
        val rawColor = item?.attributes?.get("color")?.value ?: ""
        mutableStateOf(if (rawColor.isNotEmpty()) ItemAttributeLocalizer.localizeColor(context, rawColor) else "")
    }
    var sizeField by remember(item) { mutableStateOf(item?.attributes?.get("size")?.value ?: "") }
    // Material: localize canonical value for display
    var materialField by remember(item) {
        val rawMaterial = item?.attributes?.get("material")?.value ?: ""
        mutableStateOf(if (rawMaterial.isNotEmpty()) ItemAttributeLocalizer.localizeMaterial(context, rawMaterial) else "")
    }
    // Condition: parse from attributes or item.condition
    var conditionField by remember(item) {
        val rawCondition = item?.attributes?.get("condition")?.value ?: item?.condition?.name ?: ""
        val parsedCondition = if (rawCondition.isNotEmpty()) {
            runCatching { ItemCondition.valueOf(rawCondition.uppercase()) }.getOrNull()
        } else {
            null
        }
        mutableStateOf(parsedCondition)
    }
    var notesField by remember(item) { mutableStateOf(item?.attributesSummaryText ?: "") }

    // Price field (Phase 2 - Pricing)
    var priceField by remember(item) {
        mutableStateOf(item?.userPriceCents?.let { cents -> "%.2f".format(cents / 100.0) } ?: "")
    }

    // Pricing insights from AI assistant (Phase 2 - transient, not persisted)
    var pricingInsights by remember { mutableStateOf<com.scanium.shared.core.models.assistant.PricingInsights?>(null) }

    // Observe Export Assistant state and extract pricing insights (Phase 2)
    if (exportAssistantViewModel != null) {
        val exportState by exportAssistantViewModel.state.collectAsState()
        LaunchedEffect(exportState) {
            if (exportState is ExportAssistantState.Success) {
                val successState = exportState as ExportAssistantState.Success
                // Update pricing insights
                pricingInsights = successState.pricingInsights

                // Auto-populate price with median (USER DECISION)
                val range = pricingInsights?.range
                if (pricingInsights?.status?.uppercase() == "OK" && range != null) {
                    val median = (range.low + range.high) / 2.0
                    priceField = "%.2f".format(median)
                }
            }
        }
    }

    val currentItem = item
    if (currentItem == null) {
        // Item not found - show empty state
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
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

    val focusManager = LocalFocusManager.current

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
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
                            // Save fields to attributes BEFORE calling AI
                            saveFieldsToAttributes(
                                context = context,
                                itemsViewModel = itemsViewModel,
                                itemId = itemId,
                                brandField = brandField,
                                productTypeField = productTypeField,
                                modelField = modelField,
                                colorField = colorField,
                                sizeField = sizeField,
                                materialField = materialField,
                                conditionField = conditionField,
                                notesField = notesField,
                            )

                            if (exportAssistantViewModel != null && FeatureFlags.allowAiAssistant) {
                                showExportAssistantSheet = true
                            } else {
                                onAiGenerate(itemId)
                            }
                        },
                        modifier = if (tourViewModel != null) {
                            Modifier.weight(1f).tourTarget("edit_ai_button", tourViewModel)
                        } else {
                            Modifier.weight(1f)
                        }
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
                            saveFieldsToAttributes(
                                context = context,
                                itemsViewModel = itemsViewModel,
                                itemId = itemId,
                                brandField = brandField,
                                productTypeField = productTypeField,
                                modelField = modelField,
                                colorField = colorField,
                                sizeField = sizeField,
                                materialField = materialField,
                                conditionField = conditionField,
                                notesField = notesField,
                            )
                            // Advance tour if on SAVE_CHANGES step
                            if (currentTourStep?.key == com.scanium.app.ftue.TourStepKey.SAVE_CHANGES) {
                                tourViewModel?.nextStep()
                            }
                            onBack()
                        },
                        modifier = if (tourViewModel != null) {
                            Modifier.weight(1f).tourTarget("edit_save_button", tourViewModel)
                        } else {
                            Modifier.weight(1f)
                        }
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
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Photo Gallery Section
            Text(
                text = stringResource(R.string.edit_item_photos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Contextual Action Bar for Selection Mode
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_item_photos_selected, selectedPhotoIds.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Delete button
                            IconButton(
                                onClick = {
                                    if (selectedPhotoIds.isNotEmpty()) {
                                        showDeleteConfirmation = true
                                    }
                                },
                                enabled = selectedPhotoIds.isNotEmpty() && !isDeletingPhotos,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = if (selectedPhotoIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                                    }
                                )
                            }
                            // Cancel/Close button
                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedPhotoIds = setOf()
                                },
                                enabled = !isDeletingPhotos,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_cancel),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Primary thumbnail - not deletable for now
                item {
                    val thumbnailBitmap = (currentItem.thumbnailRef ?: currentItem.thumbnail).toImageBitmap()
                    PhotoThumbnailV3(
                        bitmap = thumbnailBitmap,
                        label = stringResource(R.string.edit_item_photo_primary),
                        isPrimary = true,
                        onClick = {
                            if (!isSelectionMode) {
                                galleryStartIndex = 0
                                showPhotoGallery = true
                            }
                        },
                        // Primary photo doesn't participate in selection
                        isSelectionMode = false,
                        isSelected = false,
                    )
                }

                // Additional photos
                itemsIndexed(currentItem.additionalPhotos) { index, photo ->
                    val photoBitmap = remember(photo.uri) {
                        photo.uri?.let { uri ->
                            try {
                                BitmapFactory.decodeFile(uri)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    val isSelected = selectedPhotoIds.contains(photo.id)
                    PhotoThumbnailV3(
                        bitmap = photoBitmap?.asImageBitmap(),
                        label = null,
                        isPrimary = false,
                        onClick = {
                            if (isSelectionMode) {
                                // Toggle selection
                                selectedPhotoIds = if (isSelected) {
                                    selectedPhotoIds - photo.id
                                } else {
                                    selectedPhotoIds + photo.id
                                }
                            } else {
                                // Open gallery
                                galleryStartIndex = index + 1
                                showPhotoGallery = true
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                // Enter selection mode and select this photo
                                isSelectionMode = true
                                selectedPhotoIds = setOf(photo.id)
                            }
                        },
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                    )
                }

                // Add photo button
                item {
                    AddPhotoButtonV3(
                        onClick = { onAddPhotos(itemId) },
                        modifier = if (tourViewModel != null) {
                            Modifier.tourTarget("edit_add_photo", tourViewModel)
                        } else {
                            Modifier
                        }
                    )
                }
            }

            // Structured Fields
            Text(
                text = stringResource(R.string.item_detail_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Brand
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_brand),
                value = brandField,
                onValueChange = { brandField = it },
                onClear = { brandField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "brand"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = if (tourViewModel != null) {
                    Modifier.tourTarget("edit_brand_field", tourViewModel)
                } else {
                    Modifier
                }
            )

            Spacer(Modifier.height(12.dp))

            // Product/Type
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_product_type),
                value = productTypeField,
                onValueChange = { productTypeField = it },
                onClear = { productTypeField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "itemType"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(12.dp))

            // Model
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_model),
                value = modelField,
                onValueChange = { modelField = it },
                onClear = { modelField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "model"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(12.dp))

            // Color
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_color),
                value = colorField,
                onValueChange = { colorField = it },
                onClear = { colorField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "color"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(12.dp))

            // Size
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_size),
                value = sizeField,
                onValueChange = { sizeField = it },
                onClear = { sizeField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "size"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(12.dp))

            // Material
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_material),
                value = materialField,
                onValueChange = { materialField = it },
                onClear = { materialField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "material"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(12.dp))

            // Condition
            LabeledConditionDropdown(
                label = stringResource(R.string.edit_item_field_condition),
                selectedCondition = conditionField,
                onConditionSelected = { conditionField = it },
            )

            Spacer(Modifier.height(12.dp))

            // Price (Phase 2 - Pricing)
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_price),
                value = priceField,
                onValueChange = { priceField = it },
                onClear = { priceField = "" },
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
            )

            // AI Pricing Insights (Phase 2 - shown when available)
            if (pricingInsights?.status?.uppercase() == "OK") {
                Spacer(Modifier.height(8.dp))
                PriceInsightsCompactCard(
                    insights = pricingInsights!!,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Notes (multiline)
            Text(
                text = stringResource(R.string.edit_item_field_notes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = notesField,
                onValueChange = { notesField = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text(stringResource(R.string.edit_item_notes_placeholder)) },
                trailingIcon = {
                    if (notesField.isNotEmpty()) {
                        IconButton(onClick = { notesField = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Photo Gallery Dialog
    if (showPhotoGallery) {
        // Build list of gallery photo refs
        val galleryPhotos = remember(currentItem) {
            buildList {
                // Add primary thumbnail first
                (currentItem.thumbnailRef ?: currentItem.thumbnail)?.let { imageRef ->
                    add(GalleryPhotoRef.FromImageRef(imageRef))
                }
                // Add additional photos
                currentItem.additionalPhotos.forEach { photo ->
                    photo.uri?.let { path ->
                        add(GalleryPhotoRef.FromFilePath(path))
                    }
                }
            }
        }

        if (galleryPhotos.isNotEmpty()) {
            PhotoGalleryDialog(
                photos = galleryPhotos,
                initialIndex = galleryStartIndex,
                onDismiss = { showPhotoGallery = false },
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.edit_item_delete_photos_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.edit_item_delete_photos_message,
                        selectedPhotoIds.size
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingPhotos = true
                        showDeleteConfirmation = false
                        itemsViewModel.deletePhotosFromItem(
                            context = context,
                            itemId = itemId,
                            photoIds = selectedPhotoIds,
                            onComplete = {
                                isDeletingPhotos = false
                                isSelectionMode = false
                                selectedPhotoIds = setOf()
                            }
                        )
                    },
                    enabled = !isDeletingPhotos,
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeletingPhotos,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
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
                // Update notes field with export content
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
                notesField = builder.toString().trim()
            },
        )
    }

    // FTUE Tour Overlay
    if (isTourActive && currentTourStep?.screen == com.scanium.app.ftue.TourScreen.EDIT_ITEM) {
        currentTourStep?.let { step ->
            tourViewModel?.let { vm ->
                val bounds = targetBounds[step.targetKey]

                com.scanium.app.ftue.SpotlightTourOverlay(
                    step = step,
                    targetBounds = bounds,
                    onNext = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                    onSkip = { vm.skipTour() },
                )
            }
        }
    }
}

/**
 * Labeled text field with inline clear button.
 */
@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    keyboardOptions: KeyboardOptions? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = visualTransformation,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = keyboardOptions ?: KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
            ),
        )
    }
}

/**
 * Labeled dropdown selector for item condition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledConditionDropdown(
    label: String,
    selectedCondition: ItemCondition?,
    onConditionSelected: (ItemCondition?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedCondition?.let { ItemLocalizer.getConditionName(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    if (selectedCondition != null) {
                        IconButton(onClick = { onConditionSelected(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                placeholder = { Text(stringResource(R.string.common_select)) },
                singleLine = true,
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumbnailV3(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    label: String?,
    isPrimary: Boolean,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .then(
                        if (onClick != null || onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = { onClick?.invoke() },
                                onLongClick = { onLongClick?.invoke() }
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (isPrimary) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp),
                            )
                        } else if (isSelected) {
                            Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.tertiary,
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
            // Selection indicator overlay
            if (isSelected && isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        tint = MaterialTheme.colorScheme.onTertiary
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
private fun AddPhotoButtonV3(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addPhotoText = stringResource(R.string.edit_item_add_photo)
    val addText = stringResource(R.string.common_add)
    Card(
        modifier = modifier
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
 * Save field values to item attributes with USER source.
 * This ensures the AI export uses the latest edited values.
 *
 * LOCALIZATION: Converts localized display values back to canonical (English)
 * form before saving. This ensures internal storage remains language-neutral.
 */
private fun saveFieldsToAttributes(
    context: android.content.Context,
    itemsViewModel: ItemsViewModel,
    itemId: String,
    brandField: String,
    productTypeField: String,
    modelField: String,
    colorField: String,
    sizeField: String,
    materialField: String,
    conditionField: ItemCondition?,
    notesField: String,
) {
    // Update each attribute if value is non-blank
    if (brandField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "brand",
            ItemAttribute(value = brandField, confidence = 1.0f, source = "USER"),
        )
    }

    if (productTypeField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "itemType",
            ItemAttribute(value = productTypeField, confidence = 1.0f, source = "USER"),
        )
    }

    if (modelField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "model",
            ItemAttribute(value = modelField, confidence = 1.0f, source = "USER"),
        )
    }

    // LOCALIZATION: Canonicalize color back to English before saving
    if (colorField.isNotBlank()) {
        val canonicalColor = ItemAttributeLocalizer.canonicalizeColor(context, colorField)
        itemsViewModel.updateItemAttribute(
            itemId,
            "color",
            ItemAttribute(value = canonicalColor, confidence = 1.0f, source = "USER"),
        )
    }

    if (sizeField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "size",
            ItemAttribute(value = sizeField, confidence = 1.0f, source = "USER"),
        )
    }

    // LOCALIZATION: Canonicalize material back to English before saving
    if (materialField.isNotBlank()) {
        val canonicalMaterial = ItemAttributeLocalizer.canonicalizeMaterial(context, materialField)
        itemsViewModel.updateItemAttribute(
            itemId,
            "material",
            ItemAttribute(value = canonicalMaterial, confidence = 1.0f, source = "USER"),
        )
    }

    // Save condition enum value in canonical form (uppercase enum name)
    if (conditionField != null) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "condition",
            ItemAttribute(value = conditionField.name, confidence = 1.0f, source = "USER"),
        )
    }

    // Update summary text (notes field maps to attributesSummaryText)
    // Mark as user-edited to preserve manual changes
    itemsViewModel.updateSummaryText(
        itemId = itemId,
        summaryText = notesField,
        userEdited = notesField.isNotBlank(),
    )
}

/**
 * Compact pricing insights card for Edit Item screen (Phase 2).
 * Shows AI-generated market price range with collapsible comparable listings.
 */
@Composable
private fun PriceInsightsCompactCard(
    insights: com.scanium.shared.core.models.assistant.PricingInsights,
    modifier: Modifier = Modifier,
) {
    val range = insights.range ?: return
    val marketplacesById = remember(insights.marketplacesUsed) {
        insights.marketplacesUsed.associateBy { it.id }
    }

    var showComparables by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with price range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI Market Price",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${range.currency} ${range.low.toInt()}-${range.high.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Based on ${insights.results.size} listings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Show comparables button
            if (insights.results.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showComparables = !showComparables },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showComparables) {
                            "Hide matches (${insights.results.size})"
                        } else {
                            "Show matches (${insights.results.size})"
                        }
                    )
                }

                // Comparables list (collapsible)
                if (showComparables) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        insights.results.forEach { comparable ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = comparable.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = marketplacesById[comparable.sourceMarketplaceId]?.name
                                            ?: comparable.sourceMarketplaceId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${comparable.price.currency} ${comparable.price.amount.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
