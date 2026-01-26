package com.scanium.app.items.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.ScannedItem
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.AttributeDisplayFormatter
import com.scanium.app.items.ItemLocalizer
import com.scanium.app.model.toImageBitmap
import com.scanium.app.pricing.PricingMissingField
import com.scanium.app.pricing.PricingUiState
import com.scanium.shared.core.models.items.ItemCondition

@Composable
fun ItemEditSections(
    currentItem: ScannedItem,
    state: ItemEditState,
    focusManager: FocusManager,
    onAddPhotos: (String) -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    showPricingV3: Boolean,
    pricingUiState: PricingUiState,
    missingPricingFields: Set<PricingMissingField>,
    pricingRegionLabel: String,
    onGetPriceEstimate: () -> Unit,
    onUsePriceEstimate: (Double) -> Unit,
    onRefreshPriceEstimate: () -> Unit,
    onRetryPriceEstimate: () -> Unit,
    onFirstFieldBoundsChanged: ((Rect?) -> Unit)? = null,
    onConditionPriceBoundsChanged: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        // Photo Gallery Section
        Text(
            text = stringResource(R.string.edit_item_photos),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Contextual Action Bar for Selection Mode
        if (state.isSelectionMode) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.edit_item_photos_selected, state.selectedPhotoIds.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (state.selectedPhotoIds.isNotEmpty()) {
                                    state.showDeleteConfirmation = true
                                }
                            },
                            enabled = state.selectedPhotoIds.isNotEmpty() && !state.isDeletingPhotos,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint =
                                    if (state.selectedPhotoIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                                    },
                            )
                        }
                        IconButton(
                            onClick = {
                                state.isSelectionMode = false
                                state.selectedPhotoIds = setOf()
                            },
                            enabled = !state.isDeletingPhotos,
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
            item {
                val thumbnailBitmap = (currentItem.thumbnailRef ?: currentItem.thumbnail).toImageBitmap()
                PhotoThumbnailV3(
                    bitmap = thumbnailBitmap,
                    label = stringResource(R.string.edit_item_photo_primary),
                    isPrimary = true,
                    onClick = {
                        if (!state.isSelectionMode) {
                            state.galleryStartIndex = 0
                            state.showPhotoGallery = true
                        }
                    },
                    isSelectionMode = false,
                    isSelected = false,
                )
            }

            itemsIndexed(currentItem.additionalPhotos) { index, photo ->
                val photoBitmap =
                    remember(photo.uri) {
                        photo.uri?.let { uri ->
                            try {
                                BitmapFactory.decodeFile(uri)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                val isSelected = state.selectedPhotoIds.contains(photo.id)
                PhotoThumbnailV3(
                    bitmap = photoBitmap?.asImageBitmap(),
                    label = null,
                    isPrimary = false,
                    onClick = {
                        if (state.isSelectionMode) {
                            state.selectedPhotoIds =
                                if (isSelected) {
                                    state.selectedPhotoIds - photo.id
                                } else {
                                    state.selectedPhotoIds + photo.id
                                }
                        } else {
                            state.galleryStartIndex = index + 1
                            state.showPhotoGallery = true
                        }
                    },
                    onLongClick = {
                        if (!state.isSelectionMode) {
                            state.isSelectionMode = true
                            state.selectedPhotoIds = setOf(photo.id)
                        }
                    },
                    isSelected = isSelected,
                    isSelectionMode = state.isSelectionMode,
                )
            }

            item {
                AddPhotoButtonV3(
                    onClick = { onAddPhotos(state.itemId) },
                    modifier =
                        if (tourViewModel != null) {
                            Modifier.tourTarget("edit_add_photo", tourViewModel)
                        } else {
                            Modifier
                        },
                )
            }
        }

        // Structured Fields
        Text(
            text = stringResource(R.string.item_detail_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_brand),
            value = state.brandField,
            onValueChange = { state.brandField = it },
            onClear = { state.brandField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "brand"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onBoundsChanged = onFirstFieldBoundsChanged,
            modifier =
                if (tourViewModel != null) {
                    Modifier.tourTarget("edit_brand_field", tourViewModel)
                } else {
                    Modifier
                },
        )

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_price),
            value = state.priceField,
            onValueChange = { state.priceField = it },
            onClear = { state.priceField = "" },
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            onBoundsChanged = onConditionPriceBoundsChanged,
        )

        if (showPricingV3) {
            Spacer(Modifier.height(8.dp))
            PriceEstimateCard(
                uiState = pricingUiState,
                missingFields = missingPricingFields,
                regionLabel = pricingRegionLabel,
                onGetEstimate = onGetPriceEstimate,
                onUsePrice = onUsePriceEstimate,
                onRefresh = onRefreshPriceEstimate,
                onRetry = onRetryPriceEstimate,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_product_type),
            value = state.productTypeField,
            onValueChange = { state.productTypeField = it },
            onClear = { state.productTypeField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "itemType"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_model),
            value = state.modelField,
            onValueChange = { state.modelField = it },
            onClear = { state.modelField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "model"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_color),
            value = state.colorField,
            onValueChange = { state.colorField = it },
            onClear = { state.colorField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "color"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_size),
            value = state.sizeField,
            onValueChange = { state.sizeField = it },
            onClear = { state.sizeField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "size"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Spacer(Modifier.height(12.dp))

        LabeledTextField(
            label = stringResource(R.string.edit_item_field_material),
            value = state.materialField,
            onValueChange = { state.materialField = it },
            onClear = { state.materialField = "" },
            visualTransformation = AttributeDisplayFormatter.visualTransformation(state.context, "material"),
            imeAction = ImeAction.Next,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        Spacer(Modifier.height(12.dp))

        LabeledConditionDropdown(
            label = stringResource(R.string.edit_item_field_condition),
            selectedCondition = state.conditionField,
            onConditionSelected = { state.conditionField = it },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.edit_item_field_notes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.notesField,
            onValueChange = { state.notesField = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            placeholder = { Text(stringResource(R.string.edit_item_notes_placeholder)) },
            trailingIcon = {
                if (state.notesField.isNotEmpty()) {
                    IconButton(onClick = { state.notesField = "" }) {
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
    onBoundsChanged: ((Rect?) -> Unit)? = null,
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (onBoundsChanged != null) {
                            val bounds = coordinates.boundsInWindow()
                            onBoundsChanged(bounds)
                        }
                    },
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
            keyboardActions =
                KeyboardActions(
                    onNext = { onNext() },
                ),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LabeledConditionDropdown(
    label: String,
    selectedCondition: ItemCondition?,
    onConditionSelected: (ItemCondition?) -> Unit,
    onBoundsChanged: ((Rect?) -> Unit)? = null,
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .onGloballyPositioned { coordinates ->
                            if (onBoundsChanged != null) {
                                val bounds = coordinates.boundsInWindow()
                                onBoundsChanged(bounds)
                            }
                        },
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
                ItemCondition.entries.forEach { condition ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(ItemLocalizer.getConditionName(condition))
                                Text(
                                    text = ItemLocalizer.getConditionDescription(condition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
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
                modifier =
                    Modifier
                        .size(100.dp)
                        .then(
                            if (onClick != null || onLongClick != null) {
                                Modifier.combinedClickable(
                                    onClick = { onClick?.invoke() },
                                    onLongClick = { onLongClick?.invoke() },
                                )
                            } else {
                                Modifier
                            },
                        ).then(
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
                colors =
                    CardDefaults.cardColors(
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
            if (isSelected && isSelectionMode) {
                Box(
                    modifier =
                        Modifier
                            .size(100.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.tertiary,
                                    RoundedCornerShape(12.dp),
                                ).padding(4.dp),
                        tint = MaterialTheme.colorScheme.onTertiary,
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
        modifier =
            modifier
                .size(100.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
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
