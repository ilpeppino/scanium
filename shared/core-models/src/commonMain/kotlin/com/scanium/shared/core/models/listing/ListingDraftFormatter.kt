package com.scanium.shared.core.models.listing

import kotlin.math.round

data class ListingDraftExport(
    val clipboardText: String,
    val shareText: String
)

object ListingDraftFormatter {
    fun format(draft: ListingDraft, profile: ExportProfile = draft.profile): ListingDraftExport {
        return when (profile) {
            ExportProfile.GENERIC -> formatGeneric(draft)
        }
    }

    private fun formatGeneric(draft: ListingDraft): ListingDraftExport {
        val title = draft.title.value.orEmpty()
        val description = draft.description.value.orEmpty()
        val price = formatNumber(draft.price.value)
        val condition = formatField(draft.fields[DraftFieldKey.CONDITION])
        val category = formatField(draft.fields[DraftFieldKey.CATEGORY])
        val brand = formatField(draft.fields[DraftFieldKey.BRAND])
        val model = formatField(draft.fields[DraftFieldKey.MODEL])
        val color = formatField(draft.fields[DraftFieldKey.COLOR])
        val photoCount = draft.photos.size

        val clipboardText = buildString {
            appendLine("Title:")
            appendLine(title)
            appendLine()
            appendLine("Price: ${price.ifBlank { "N/A" }}")
            appendLine("Condition: ${condition.ifBlank { "N/A" }}")
            appendLine("Category: ${category.ifBlank { "N/A" }}")
            if (brand.isNotBlank()) appendLine("Brand: $brand")
            if (model.isNotBlank()) appendLine("Model: $model")
            if (color.isNotBlank()) appendLine("Color: $color")
            appendLine()
            appendLine("Description:")
            appendLine(description)
            appendLine()
            appendLine("Photos: $photoCount")
        }

        val shareText = buildString {
            if (title.isNotBlank()) appendLine(title)
            if (price.isNotBlank()) appendLine("Price: $price")
            if (condition.isNotBlank()) appendLine("Condition: $condition")
            if (category.isNotBlank()) appendLine("Category: $category")
            if (brand.isNotBlank()) appendLine("Brand: $brand")
            if (model.isNotBlank()) appendLine("Model: $model")
            if (color.isNotBlank()) appendLine("Color: $color")
            if (description.isNotBlank()) {
                appendLine()
                appendLine(description)
            }
        }.trim()

        return ListingDraftExport(
            clipboardText = clipboardText.trim(),
            shareText = shareText
        )
    }

    private fun formatField(field: DraftField<String>?): String {
        val value = field?.value?.trim().orEmpty()
        if (value.isBlank()) return ""
        val confidence = field?.confidence ?: 0f
        val confidenceText = if (confidence > 0f) " (${formatNumber(confidence.toDouble())})" else ""
        return "$value$confidenceText"
    }

    private fun formatNumber(value: Double?): String {
        if (value == null) return ""
        val rounded = round(value * 100.0) / 100.0
        var text = rounded.toString()
        if (text.contains('.')) {
            text = text.trimEnd('0').trimEnd('.')
        }
        return text
    }
}
