package com.scanium.app.items.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.EditHintType
import com.scanium.app.ftue.EditItemFtueOverlay
import com.scanium.app.ftue.EditItemFtueViewModel
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.pricing.PricingUiState
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemCondition

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
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSettingsGeneral: () -> Unit = {},
) {
    val context = LocalContext.current
    val allItems by itemsViewModel.items.collectAsState()
    val item by remember(allItems, itemId) {
        derivedStateOf { allItems.find { it.id == itemId } }
    }

    // Observe AI assistant enabled setting
    val settingsRepository = remember { SettingsRepository(context) }
    // ISSUE-1 FIX: Use initial=true to avoid double-click bug where first click
    // shows "AI disabled" before flow emits actual value. Defense-in-depth check
    // in ExportAssistantViewModel.generateExport() handles truly disabled case.
    val aiAssistantEnabled by settingsRepository.allowAssistantFlow.collectAsState(initial = true)
    val primaryRegionCountry by settingsRepository.primaryRegionCountryFlow.collectAsState(initial = "")

    // FTUE Tour State
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // Edit Item FTUE State
    val ftueRepository = remember { FtueRepository(context) }
    val editItemFtueViewModel = remember { EditItemFtueViewModel(ftueRepository) }
    val editFtueCompleted by ftueRepository.editFtueCompletedFlow.collectAsState(initial = false)
    val editFtueCurrentStep by editItemFtueViewModel.currentStep.collectAsState()
    val editFtueIsActive by editItemFtueViewModel.isActive.collectAsState()
    val editFtueShowDetailsHint by editItemFtueViewModel.showDetailsHint.collectAsState()
    val editFtueShowConditionPriceHint by editItemFtueViewModel.showConditionPriceHint.collectAsState()

    var firstFieldRect by remember { mutableStateOf<Rect?>(null) }
    var conditionPriceFieldRect by remember { mutableStateOf<Rect?>(null) }

    val editState =
        rememberItemEditState(
            item = item,
            itemId = itemId,
            itemsViewModel = itemsViewModel,
            exportAssistantViewModelFactory = exportAssistantViewModelFactory,
        )

    // Observe Export Assistant state and extract pricing insights (Phase 2)
    if (editState.exportAssistantViewModel != null) {
        val exportState by editState.exportAssistantViewModel.state.collectAsState()
        LaunchedEffect(exportState) {
            if (exportState is ExportAssistantState.Success) {
                val successState = exportState as ExportAssistantState.Success
                // Update pricing insights
                editState.pricingInsights = successState.pricingInsights
                successState.pricingInsights?.let { insights ->
                    editState.lastPricingInputs = editState.pricingInputs
                    editState.pricingUiState = PricingUiState.Success(insights, isStale = false)
                }

                // Auto-populate price with median (USER DECISION)
                val range = editState.pricingInsights?.range
                if (editState.pricingInsights?.status?.uppercase() == "OK" && range != null) {
                    val median = (range.low + range.high) / 2.0
                    editState.priceField = "%.2f".format(median)
                }
            }
        }
    }

    LaunchedEffect(
        editState.brandField,
        editState.productTypeField,
        editState.modelField,
        editState.conditionField,
    ) {
        val inputs = editState.pricingInputs
        val missing = inputs.missingFields()
        val currentState = editState.pricingUiState
        editState.pricingUiState =
            when (currentState) {
                is PricingUiState.Success -> currentState.copy(isStale = inputs.isStaleComparedTo(editState.lastPricingInputs))
                PricingUiState.Loading -> currentState
                else -> if (missing.isEmpty()) PricingUiState.Ready else PricingUiState.InsufficientData
            }
    }

    // Initialize Edit Item FTUE when screen is first shown
    LaunchedEffect(editFtueCompleted) {
        if (!editFtueCompleted) {
            editItemFtueViewModel.initialize(shouldStartFtue = true, isDevBuild = com.scanium.app.BuildConfig.FLAVOR == "dev")
        }
    }

    // Track field edits for FTUE progression
    LaunchedEffect(
        editState.brandField,
        editState.productTypeField,
        editState.modelField,
        editState.colorField,
        editState.sizeField,
        editState.materialField,
    ) {
        if (editFtueCurrentStep == com.scanium.app.ftue.EditItemFtueViewModel.EditItemFtueStep.DETAILS_HINT_SHOWN) {
            editItemFtueViewModel.onFieldEdited()
        }
    }

    // Track condition/price updates for FTUE progression
    LaunchedEffect(editState.conditionField, editState.priceField) {
        if (editFtueCurrentStep == com.scanium.app.ftue.EditItemFtueViewModel.EditItemFtueStep.CONDITION_PRICE_HINT_SHOWN) {
            editItemFtueViewModel.onConditionOrPriceSet()
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
                modifier =
                    Modifier
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
                    modifier =
                        Modifier
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
                            if (!aiAssistantEnabled) {
                                // Show disabled inlay instead of triggering assistant
                                editState.showAiDisabledInlay = true
                            } else {
                                // Save fields to attributes BEFORE calling AI
                                saveFieldsToAttributes(
                                    context = context,
                                    itemsViewModel = itemsViewModel,
                                    itemId = itemId,
                                    brandField = editState.brandField,
                                    productTypeField = editState.productTypeField,
                                    modelField = editState.modelField,
                                    colorField = editState.colorField,
                                    sizeField = editState.sizeField,
                                    materialField = editState.materialField,
                                    conditionField = editState.conditionField,
                                    notesField = editState.notesField,
                                )

                                if (editState.exportAssistantViewModel != null && FeatureFlags.allowAiAssistant) {
                                    editState.showExportAssistantSheet = true
                                } else {
                                    onAiGenerate(itemId)
                                }
                            }
                        },
                        enabled = true, // Always clickable to show inlay when disabled
                        colors =
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor =
                                    if (aiAssistantEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            ),
                        modifier =
                            if (tourViewModel != null) {
                                Modifier
                                    .weight(1f)
                                    .tourTarget("edit_ai_button", tourViewModel)
                                    .testTag("editItem_aiButton")
                            } else {
                                Modifier
                                    .weight(1f)
                                    .testTag("editItem_aiButton")
                            },
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
                                brandField = editState.brandField,
                                productTypeField = editState.productTypeField,
                                modelField = editState.modelField,
                                colorField = editState.colorField,
                                sizeField = editState.sizeField,
                                materialField = editState.materialField,
                                conditionField = editState.conditionField,
                                notesField = editState.notesField,
                            )
                            // Advance tour if on SAVE_CHANGES step
                            if (currentTourStep?.key == com.scanium.app.ftue.TourStepKey.SAVE_CHANGES) {
                                tourViewModel?.nextStep()
                            }
                            onBack()
                        },
                        modifier =
                            if (tourViewModel != null) {
                                Modifier
                                    .weight(1f)
                                    .tourTarget("edit_save_button", tourViewModel)
                            } else {
                                Modifier.weight(1f)
                            },
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        },
    ) { padding ->
        ItemEditSections(
            currentItem = currentItem,
            state = editState,
            focusManager = focusManager,
            onAddPhotos = onAddPhotos,
            tourViewModel = tourViewModel,
            pricingUiState = editState.pricingUiState,
            missingPricingFields = editState.pricingInputs.missingFields(),
            pricingRegionLabel =
                if (primaryRegionCountry.isBlank()) {
                    stringResource(R.string.pricing_region_generic)
                } else {
                    primaryRegionCountry
                },
            onGetPriceEstimate = {
                editState.lastPricingInputs = editState.pricingInputs
                editState.pricingUiState = PricingUiState.Loading
            },
            onUsePriceEstimate = { median ->
                editState.priceField = "%.2f".format(median)
            },
            onRefreshPriceEstimate = {
                editState.lastPricingInputs = editState.pricingInputs
                editState.pricingUiState = PricingUiState.Loading
            },
            onRetryPriceEstimate = {
                val missing = editState.pricingInputs.missingFields()
                editState.pricingUiState = if (missing.isEmpty()) PricingUiState.Ready else PricingUiState.InsufficientData
            },
            onFirstFieldBoundsChanged = { bounds -> firstFieldRect = bounds },
            onConditionPriceBoundsChanged = { bounds -> conditionPriceFieldRect = bounds },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(horizontal = 16.dp),
        )
    }

    // Photo Gallery Dialog
    if (editState.showPhotoGallery) {
        // Build list of gallery photo refs
        val galleryPhotos =
            remember(currentItem) {
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
                initialIndex = editState.galleryStartIndex,
                onDismiss = { editState.showPhotoGallery = false },
            )
        }
    }

    // Delete Confirmation Dialog
    if (editState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { editState.showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.edit_item_delete_photos_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.edit_item_delete_photos_message,
                        editState.selectedPhotoIds.size,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        editState.isDeletingPhotos = true
                        editState.showDeleteConfirmation = false
                        itemsViewModel.deletePhotosFromItem(
                            context = context,
                            itemId = itemId,
                            photoIds = editState.selectedPhotoIds,
                            onComplete = {
                                editState.isDeletingPhotos = false
                                editState.isSelectionMode = false
                                editState.selectedPhotoIds = setOf()
                            },
                        )
                    },
                    enabled = !editState.isDeletingPhotos,
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editState.showDeleteConfirmation = false },
                    enabled = !editState.isDeletingPhotos,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // Export Assistant Bottom Sheet
    if (editState.showExportAssistantSheet && editState.exportAssistantViewModel != null) {
        val settingsRepository = remember { SettingsRepository(context) }
        val ttsManager =
            remember {
                com.scanium.app.assistant.tts
                    .TtsManager(context, settingsRepository)
            }
        ExportAssistantSheet(
            viewModel = editState.exportAssistantViewModel,
            settingsRepository = settingsRepository,
            ttsManager = ttsManager,
            onDismiss = { editState.showExportAssistantSheet = false },
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
                editState.notesField = builder.toString().trim()
            },
            onNavigateToSettingsAssistant = onNavigateToSettings,
            onNavigateToSettingsGeneral = onNavigateToSettingsGeneral,
        )
    }

    // AI Disabled Inlay Dialog
    if (editState.showAiDisabledInlay) {
        AlertDialog(
            onDismissRequest = { editState.showAiDisabledInlay = false },
            title = { Text(stringResource(R.string.assistant_disabled_title)) },
            text = {
                Text(
                    stringResource(R.string.assistant_disabled_message),
                    modifier = Modifier.testTag("aiDisabled_inlay"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        editState.showAiDisabledInlay = false
                        onNavigateToSettings()
                    },
                    modifier = Modifier.testTag("aiDisabled_openSettings"),
                ) {
                    Text(stringResource(R.string.assistant_disabled_open_settings))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editState.showAiDisabledInlay = false },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
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

    // Edit Item FTUE Overlays
    if (editFtueShowDetailsHint) {
        EditItemFtueOverlay(
            isVisible = true,
            hintType = EditHintType.IMPROVE_DETAILS,
            targetFieldRect = firstFieldRect,
            onDismiss = { editItemFtueViewModel.dismiss() },
        )
    }

    if (editFtueShowConditionPriceHint) {
        EditItemFtueOverlay(
            isVisible = true,
            hintType = EditHintType.CONDITION_PRICE,
            targetFieldRect = conditionPriceFieldRect,
            onDismiss = { editItemFtueViewModel.dismiss() },
        )
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
