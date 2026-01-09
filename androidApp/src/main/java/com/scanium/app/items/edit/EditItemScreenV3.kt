package com.scanium.app.items.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.app.items.AttributeDisplayFormatter
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.ItemAttribute
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
) {
    val context = LocalContext.current
    val allItems by itemsViewModel.items.collectAsState()
    val item by remember(allItems, itemId) {
        derivedStateOf { allItems.find { it.id == itemId } }
    }

    // Export Assistant state
    var showExportAssistantSheet by remember { mutableStateOf(false) }
    val exportAssistantViewModel = remember(exportAssistantViewModelFactory, itemId) {
        exportAssistantViewModelFactory?.create(itemId, itemsViewModel)
    }

    // Photo Gallery Dialog state
    var showPhotoGallery by remember { mutableStateOf(false) }
    var galleryStartIndex by remember { mutableStateOf(0) }

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
    // Condition: localize canonical value for display
    var conditionField by remember(item) {
        val rawCondition = item?.attributes?.get("condition")?.value ?: item?.condition?.name ?: ""
        mutableStateOf(if (rawCondition.isNotEmpty()) ItemAttributeLocalizer.localizeCondition(context, rawCondition) else "")
    }
    var notesField by remember(item) { mutableStateOf(item?.attributesSummaryText ?: "") }

    val currentItem = item
    if (currentItem == null) {
        // Item not found - show empty state
        Scaffold(
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
                        modifier = Modifier.weight(1f),
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
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Primary thumbnail
                item {
                    val thumbnailBitmap = (currentItem.thumbnailRef ?: currentItem.thumbnail).toImageBitmap()
                    PhotoThumbnailV3(
                        bitmap = thumbnailBitmap,
                        label = stringResource(R.string.edit_item_photo_primary),
                        isPrimary = true,
                        onClick = {
                            galleryStartIndex = 0
                            showPhotoGallery = true
                        },
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
                    PhotoThumbnailV3(
                        bitmap = photoBitmap?.asImageBitmap(),
                        label = null,
                        isPrimary = false,
                        onClick = {
                            // Index is offset by 1 because primary photo is at index 0
                            galleryStartIndex = index + 1
                            showPhotoGallery = true
                        },
                    )
                }

                // Add photo button
                item {
                    AddPhotoButtonV3(onClick = { onAddPhotos(itemId) })
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
            LabeledTextField(
                label = stringResource(R.string.edit_item_field_condition),
                value = conditionField,
                onValueChange = { conditionField = it },
                onClear = { conditionField = "" },
                visualTransformation = AttributeDisplayFormatter.visualTransformation(context, "condition"),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )

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

    // Export Assistant Bottom Sheet
    if (showExportAssistantSheet && exportAssistantViewModel != null) {
        ExportAssistantSheet(
            viewModel = exportAssistantViewModel,
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
            ),
        )
    }
}

@Composable
private fun PhotoThumbnailV3(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    label: String?,
    isPrimary: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .size(100.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
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
private fun AddPhotoButtonV3(onClick: () -> Unit) {
    val addPhotoText = stringResource(R.string.edit_item_add_photo)
    val addText = stringResource(R.string.common_add)
    Card(
        modifier = Modifier
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
    conditionField: String,
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

    // LOCALIZATION: Canonicalize condition back to English before saving
    if (conditionField.isNotBlank()) {
        val canonicalCondition = ItemAttributeLocalizer.canonicalizeCondition(context, conditionField)
        itemsViewModel.updateItemAttribute(
            itemId,
            "condition",
            ItemAttribute(value = canonicalCondition, confidence = 1.0f, source = "USER"),
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
