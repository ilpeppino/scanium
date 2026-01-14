package com.scanium.app.items.edit

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ScannedItem
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.items.ItemCondition

@Stable
class ItemEditState(
    val context: Context,
    val itemId: String,
    val itemsViewModel: com.scanium.app.items.ItemsViewModel,
    exportAssistantViewModelFactory: ExportAssistantViewModel.Factory?,
    item: ScannedItem?,
) {
    val exportAssistantViewModel = exportAssistantViewModelFactory?.create(itemId, itemsViewModel)

    var showExportAssistantSheet by mutableStateOf(false)
    var showAiDisabledInlay by mutableStateOf(false)
    var showPhotoGallery by mutableStateOf(false)
    var galleryStartIndex by mutableStateOf(0)
    var isSelectionMode by mutableStateOf(false)
    var selectedPhotoIds by mutableStateOf(setOf<String>())
    var showDeleteConfirmation by mutableStateOf(false)
    var isDeletingPhotos by mutableStateOf(false)

    var brandField by mutableStateOf(item?.attributes?.get("brand")?.value ?: "")
    var productTypeField by mutableStateOf(item?.attributes?.get("itemType")?.value ?: "")
    var modelField by mutableStateOf(item?.attributes?.get("model")?.value ?: "")
    var colorField by mutableStateOf(
        item?.attributes?.get("color")?.value?.takeIf { it.isNotEmpty() }?.let { rawColor ->
            ItemAttributeLocalizer.localizeColor(context, rawColor)
        } ?: ""
    )
    var sizeField by mutableStateOf(item?.attributes?.get("size")?.value ?: "")
    var materialField by mutableStateOf(
        item?.attributes?.get("material")?.value?.takeIf { it.isNotEmpty() }?.let { rawMaterial ->
            ItemAttributeLocalizer.localizeMaterial(context, rawMaterial)
        } ?: ""
    )
    var conditionField by mutableStateOf(
        item?.attributes?.get("condition")?.value?.takeIf { it.isNotEmpty() }?.let { rawCondition ->
            runCatching { ItemCondition.valueOf(rawCondition.uppercase()) }.getOrNull()
        } ?: item?.condition
    )
    var notesField by mutableStateOf(item?.attributesSummaryText ?: "")
    var priceField by mutableStateOf(
        item?.userPriceCents?.let { cents -> "%.2f".format(cents / 100.0) } ?: ""
    )

    var pricingInsights by mutableStateOf<PricingInsights?>(null)
}

@Composable
fun rememberItemEditState(
    item: ScannedItem?,
    itemId: String,
    itemsViewModel: com.scanium.app.items.ItemsViewModel,
    exportAssistantViewModelFactory: ExportAssistantViewModel.Factory?,
): ItemEditState {
    val context = LocalContext.current
    return remember(item, itemId, itemsViewModel, exportAssistantViewModelFactory, context) {
        ItemEditState(
            context = context,
            itemId = itemId,
            itemsViewModel = itemsViewModel,
            exportAssistantViewModelFactory = exportAssistantViewModelFactory,
            item = item,
        )
    }
}
