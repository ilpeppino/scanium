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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.scanium.app.catalog.CatalogSearch
import com.scanium.app.catalog.CatalogType
import com.scanium.app.config.FeatureFlags
import com.scanium.app.data.AndroidRemoteConfigProvider
import com.scanium.app.data.SettingsRepository
import com.scanium.app.di.CatalogSearchEntryPoint
import com.scanium.app.di.PricingV3RepositoryEntryPoint
import com.scanium.app.di.PricingV4RepositoryEntryPoint
import com.scanium.app.ftue.EditHintType
import com.scanium.app.ftue.EditItemFtueOverlay
import com.scanium.app.ftue.EditItemFtueViewModel
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.state.ItemFieldUpdate
import com.scanium.app.model.config.RemoteConfig
import com.scanium.app.pricing.PricingMissingField
import com.scanium.app.pricing.PricingUiState
import com.scanium.app.pricing.PricingV3Exception
import com.scanium.app.pricing.PricingV3Repository
import com.scanium.app.pricing.PricingV3Request
import com.scanium.app.pricing.PricingV4Exception
import com.scanium.app.pricing.PricingV4Repository
import com.scanium.app.pricing.PricingV4Request
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemCondition
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

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
    pricingAssistantViewModelFactory: PricingAssistantViewModel.Factory? = null,
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
    val configScope = rememberCoroutineScope()
    val uiScope = rememberCoroutineScope()
    val configProvider = remember { AndroidRemoteConfigProvider(context, configScope) }
    val remoteConfig by configProvider.config.collectAsState(initial = RemoteConfig())
    // ISSUE-1 FIX: Use initial=true to avoid double-click bug where first click
    // shows "AI disabled" before flow emits actual value. Defense-in-depth check
    // in ExportAssistantViewModel.generateExport() handles truly disabled case.
    val aiAssistantEnabled by settingsRepository.allowAssistantFlow.collectAsState(initial = true)
    val primaryRegionCountry by settingsRepository.primaryRegionCountryFlow.collectAsState(initial = "")
    val pricingGuidanceDismissed by settingsRepository.pricingGuidanceDismissedFlow.collectAsState(initial = false)
    val showPricingV4 = FeatureFlags.allowPricingV4 && remoteConfig.featureFlags.enablePricingV4
    val showPricingV3 = FeatureFlags.allowPricingV3 && remoteConfig.featureFlags.enablePricingV3
    val showPricing = showPricingV4 || showPricingV3
    val showPricingAssistant = FeatureFlags.allowPricingAssistant && showPricingV4

    val pricingV3Repository: PricingV3Repository =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    PricingV3RepositoryEntryPoint::class.java,
                ).pricingV3Repository()
        }

    val pricingV4Repository: PricingV4Repository =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    PricingV4RepositoryEntryPoint::class.java,
                ).pricingV4Repository()
        }

    val catalogSearch: CatalogSearch =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    CatalogSearchEntryPoint::class.java,
                ).catalogSearch()
        }

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

    var showAiAssistantChooser by remember { mutableStateOf(false) }
    var showPricingUnavailableSheet by remember { mutableStateOf(false) }
    var showPricingGuidanceDialog by remember { mutableStateOf(false) }
    var pricingGuidanceDontShowAgain by remember { mutableStateOf(false) }
    var aiChooserErrorMessage by remember { mutableStateOf<String?>(null) }
    var showPricingAssistantFieldErrors by remember { mutableStateOf(false) }

    var firstFieldRect by remember { mutableStateOf<Rect?>(null) }
    var conditionPriceFieldRect by remember { mutableStateOf<Rect?>(null) }

    val editState =
        rememberItemEditState(
            item = item,
            itemId = itemId,
            itemsViewModel = itemsViewModel,
            exportAssistantViewModelFactory = exportAssistantViewModelFactory,
            pricingAssistantViewModelFactory = pricingAssistantViewModelFactory,
        )

    val brandSuggestionsFlow =
        remember(catalogSearch, editState.brandQueryFlow) {
            catalogSearch.searchFlow(CatalogType.BRANDS, editState.brandQueryFlow)
        }
    val productTypeSuggestionsFlow =
        remember(catalogSearch, editState.productTypeQueryFlow) {
            catalogSearch.searchFlow(CatalogType.PRODUCT_TYPES, editState.productTypeQueryFlow)
        }
    val brandSuggestions by brandSuggestionsFlow.collectAsState(emptyList())
    val productTypeSuggestions by productTypeSuggestionsFlow.collectAsState(emptyList())

    LaunchedEffect(brandSuggestions) { editState.brandSuggestions = brandSuggestions }
    LaunchedEffect(productTypeSuggestions) { editState.productTypeSuggestions = productTypeSuggestions }

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

    fun submitPricingRequest() {
        val inputs = editState.pricingInputs
        if (!inputs.isComplete()) {
            editState.pricingUiState = PricingUiState.InsufficientData
            return
        }

        val condition = inputs.condition ?: return
        val countryCode = primaryRegionCountry.ifBlank { "NL" }.uppercase()

        editState.lastPricingInputs = inputs
        editState.pricingUiState = PricingUiState.Loading

        configScope.launch {
            val result =
                if (showPricingV4) {
                    pricingV4Repository.estimatePrice(
                        PricingV4Request(
                            itemId = itemId,
                            brand = inputs.brand,
                            productType = inputs.productType,
                            model = inputs.model.trim().takeIf { it.isNotBlank() },
                            condition = condition.name,
                            countryCode = countryCode,
                        ),
                    )
                } else {
                    pricingV3Repository.estimatePrice(
                        PricingV3Request(
                            itemId = itemId,
                            brand = inputs.brand,
                            productType = inputs.productType,
                            model = inputs.model.trim().takeIf { it.isNotBlank() },
                            condition = condition.name,
                            countryCode = countryCode,
                        ),
                    )
                }

            result.onSuccess { insights ->
                editState.pricingInsights = insights
                editState.pricingUiState = PricingUiState.Success(insights, isStale = false)
                insights.range?.let { range ->
                    if (insights.status.uppercase() == "OK") {
                        val median = range.median ?: (range.low + range.high) / 2.0
                        editState.priceField = "%.2f".format(median)
                    }
                }
            }.onFailure { error ->
                val (message, retryable, retryAfter) =
                    when (error) {
                        is PricingV4Exception -> Triple(error.userMessage, error.retryable, error.retryAfterSeconds)
                        is PricingV3Exception -> Triple(error.userMessage, error.retryable, error.retryAfterSeconds)
                        else ->
                            Triple(
                                context.getString(R.string.pricing_error_network),
                                true,
                                null,
                            )
                    }
                editState.pricingUiState =
                    PricingUiState.Error(
                        message = message,
                        retryable = retryable,
                        retryAfterSeconds = retryAfter,
                    )
            }
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
                                    brandId = editState.brandId,
                                    productTypeField = editState.productTypeField,
                                    productTypeId = editState.productTypeId,
                                    modelField = editState.modelField,
                                    colorField = editState.colorField,
                                    sizeField = editState.sizeField,
                                    conditionField = editState.conditionField,
                                    priceField = editState.priceField,
                                    notesField = editState.notesField,
                                )

                                showAiAssistantChooser = true
                            }
                        },
                        // Always clickable to show inlay when disabled
                        enabled = true,
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
                                brandId = editState.brandId,
                                productTypeField = editState.productTypeField,
                                productTypeId = editState.productTypeId,
                                modelField = editState.modelField,
                                colorField = editState.colorField,
                                sizeField = editState.sizeField,
                                conditionField = editState.conditionField,
                                priceField = editState.priceField,
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
            showPricingV3 = showPricing,
            pricingUiState = editState.pricingUiState,
            missingPricingFields = editState.pricingInputs.missingFields(),
            assistantMissingFields =
                if (showPricingAssistantFieldErrors) {
                    editState.pricingInputs
                        .missingFields()
                        .intersect(setOf(PricingMissingField.BRAND, PricingMissingField.PRODUCT_TYPE))
                } else {
                    emptySet()
                },
            pricingRegionLabel =
                if (primaryRegionCountry.isBlank()) {
                    stringResource(R.string.pricing_region_generic)
                } else {
                    primaryRegionCountry
                },
            onGetPriceEstimate = {
                submitPricingRequest()
            },
            onUsePriceEstimate = { median ->
                editState.priceField = "%.2f".format(median)
            },
            onRefreshPriceEstimate = {
                submitPricingRequest()
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

    if (showAiAssistantChooser) {
        AiAssistantChooserSheet(
            onDismiss = {
                showAiAssistantChooser = false
                aiChooserErrorMessage = null
            },
            onChoosePrice = {
                android.util.Log.d("EditItemScreenV3", "AI chooser: Price my item selected")
                val missing =
                    editState.pricingInputs
                        .missingFields()
                        .intersect(setOf(PricingMissingField.BRAND, PricingMissingField.PRODUCT_TYPE))
                if (missing.isNotEmpty()) {
                    aiChooserErrorMessage =
                        context.getString(R.string.pricing_assistant_missing_fields_error)
                    showPricingAssistantFieldErrors = true
                    return@AiAssistantChooserSheet
                }
                aiChooserErrorMessage = null
                showAiAssistantChooser = false
                if (showPricingAssistant && editState.pricingAssistantViewModel != null) {
                    editState.pricingAssistantViewModel.refreshFromItem()
                    if (pricingGuidanceDismissed) {
                        editState.showPricingAssistantSheet = true
                    } else {
                        pricingGuidanceDontShowAgain = false
                        showPricingGuidanceDialog = true
                    }
                } else {
                    showPricingUnavailableSheet = true
                }
            },
            onChooseListing = {
                showAiAssistantChooser = false
                aiChooserErrorMessage = null
                android.util.Log.d("EditItemScreenV3", "AI chooser: Generate listing text selected")
                if (editState.exportAssistantViewModel != null && FeatureFlags.allowAiAssistant) {
                    editState.showExportAssistantSheet = true
                } else {
                    onAiGenerate(itemId)
                }
            },
            highlightPrice = true,
            errorMessage = aiChooserErrorMessage,
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

    // Pricing Assistant Bottom Sheet
    if (editState.showPricingAssistantSheet && editState.pricingAssistantViewModel != null) {
        PricingAssistantSheet(
            viewModel = editState.pricingAssistantViewModel,
            countryCode = primaryRegionCountry,
            onDismiss = { editState.showPricingAssistantSheet = false },
            onUsePrice = { price ->
                editState.pricingAssistantViewModel.applyPrice(price)
                editState.priceField = "%.2f".format(price)
                editState.showPricingAssistantSheet = false
            },
            onOpenListingAssistant = {
                if (editState.exportAssistantViewModel != null && FeatureFlags.allowAiAssistant) {
                    editState.showPricingAssistantSheet = false
                    editState.showExportAssistantSheet = true
                }
            },
        )
    }

    if (showPricingGuidanceDialog) {
        PricingGuidanceDialog(
            dontShowAgainChecked = pricingGuidanceDontShowAgain,
            onDontShowAgainChange = { pricingGuidanceDontShowAgain = it },
            onContinue = {
                if (pricingGuidanceDontShowAgain) {
                    uiScope.launch {
                        settingsRepository.setPricingGuidanceDismissed(true)
                    }
                }
                showPricingGuidanceDialog = false
                editState.showPricingAssistantSheet = true
            },
            onDismiss = { showPricingGuidanceDialog = false },
        )
    }

    if (showPricingUnavailableSheet) {
        PricingUnavailableSheet(
            onDismiss = { showPricingUnavailableSheet = false },
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
    brandId: String?,
    productTypeField: String,
    productTypeId: String?,
    modelField: String,
    colorField: String,
    sizeField: String,
    conditionField: ItemCondition?,
    priceField: String,
    notesField: String,
) {
    val priceCents = parsePriceToCents(priceField)
    if (android.util.Log.isLoggable("EditItemScreenV3", android.util.Log.DEBUG)) {
        android.util.Log.d("EditItemScreenV3", "Saving price for $itemId: '$priceField' -> $priceCents")
    }
    when {
        priceField.isBlank() -> {
            itemsViewModel.updateItemsFields(
                mapOf(
                    itemId to ItemFieldUpdate(clearUserPriceCents = true),
                ),
            )
        }
        priceCents != null -> {
            itemsViewModel.updateItemsFields(
                mapOf(
                    itemId to ItemFieldUpdate(userPriceCents = priceCents),
                ),
            )
        }
        else -> {
            if (android.util.Log.isLoggable("EditItemScreenV3", android.util.Log.WARN)) {
                android.util.Log.w("EditItemScreenV3", "Invalid price for $itemId: '$priceField' (skipping persistence)")
            }
        }
    }

    // Update each attribute if value is non-blank
    if (brandField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "brand",
            ItemAttribute(value = brandField, confidence = 1.0f, source = "USER"),
        )
        val resolvedBrandId = brandId ?: "custom:${normalizeCatalogId(brandField)}"
        itemsViewModel.updateItemAttribute(
            itemId,
            "brandId",
            ItemAttribute(value = resolvedBrandId, confidence = 1.0f, source = "USER"),
        )
    }

    if (productTypeField.isNotBlank()) {
        itemsViewModel.updateItemAttribute(
            itemId,
            "itemType",
            ItemAttribute(value = productTypeField, confidence = 1.0f, source = "USER"),
        )
        val resolvedProductTypeId = productTypeId ?: "custom:${normalizeCatalogId(productTypeField)}"
        itemsViewModel.updateItemAttribute(
            itemId,
            "itemTypeId",
            ItemAttribute(value = resolvedProductTypeId, confidence = 1.0f, source = "USER"),
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

private fun parsePriceToCents(priceText: String): Long? {
    if (priceText.isBlank()) return null

    val normalized = priceText.replace(',', '.')

    return try {
        val euros = normalized.toDouble()
        if (euros < 0 || euros > 1_000_000) {
            null
        } else {
            (euros * 100).toLong()
        }
    } catch (e: NumberFormatException) {
        null
    }
}

private fun normalizeCatalogId(rawValue: String): String =
    rawValue
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^a-z0-9_]"), "")
