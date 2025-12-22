package com.scanium.shared.core.models.listing

import kotlin.math.round

data class ListingDraftExport(
    val clipboardText: String,
    val shareText: String
)

object ListingDraftFormatter {
    fun format(draft: ListingDraft, profile: ExportProfileDefinition): ListingDraftExport {
        val title = formatTitle(draft, profile.titleRules)
        val description = formatDescription(draft, profile)
        val fieldValues = buildFieldValues(draft, title, description)

        val clipboardText = buildClipboardText(fieldValues, profile)
        val shareText = buildShareText(fieldValues, profile)

        return ListingDraftExport(
            clipboardText = clipboardText,
            shareText = shareText
        )
    }

    fun format(draft: ListingDraft): ListingDraftExport {
        return format(draft, ExportProfiles.generic())
    }

    fun formattedValues(
        draft: ListingDraft,
        profile: ExportProfileDefinition
    ): Map<ExportFieldKey, String> {
        val title = formatTitle(draft, profile.titleRules)
        val description = formatDescription(draft, profile)
        return buildFieldValues(draft, title, description)
    }

    fun formatFieldLine(
        key: ExportFieldKey,
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition
    ): String {
        return buildFieldLine(key, values, profile)
    }

    fun missingRequiredFields(
        draft: ListingDraft,
        profile: ExportProfileDefinition
    ): List<ExportFieldKey> {
        return profile.requiredFields.filter { key ->
            when (key) {
                ExportFieldKey.TITLE -> draft.title.value.isNullOrBlank()
                ExportFieldKey.PRICE -> (draft.price.value ?: 0.0) <= 0.0
                ExportFieldKey.CONDITION -> draft.fields[DraftFieldKey.CONDITION]?.value.isNullOrBlank()
                ExportFieldKey.CATEGORY -> draft.fields[DraftFieldKey.CATEGORY]?.value.isNullOrBlank()
                ExportFieldKey.BRAND -> draft.fields[DraftFieldKey.BRAND]?.value.isNullOrBlank()
                ExportFieldKey.MODEL -> draft.fields[DraftFieldKey.MODEL]?.value.isNullOrBlank()
                ExportFieldKey.COLOR -> draft.fields[DraftFieldKey.COLOR]?.value.isNullOrBlank()
                ExportFieldKey.DESCRIPTION -> draft.description.value.isNullOrBlank()
                ExportFieldKey.PHOTOS -> draft.photos.isEmpty()
            }
        }
    }

    private fun buildFieldValues(
        draft: ListingDraft,
        formattedTitle: String,
        formattedDescription: String?
    ): Map<ExportFieldKey, String> {
        val price = formatNumber(draft.price.value)
        val condition = formatField(draft.fields[DraftFieldKey.CONDITION])
        val category = formatField(draft.fields[DraftFieldKey.CATEGORY])
        val brand = formatField(draft.fields[DraftFieldKey.BRAND])
        val model = formatField(draft.fields[DraftFieldKey.MODEL])
        val color = formatField(draft.fields[DraftFieldKey.COLOR])
        val photos = draft.photos.size.toString()
        val description = formattedDescription ?: draft.description.value.orEmpty()

        return linkedMapOf(
            ExportFieldKey.TITLE to formattedTitle,
            ExportFieldKey.PRICE to price,
            ExportFieldKey.CONDITION to condition,
            ExportFieldKey.CATEGORY to category,
            ExportFieldKey.BRAND to brand,
            ExportFieldKey.MODEL to model,
            ExportFieldKey.COLOR to color,
            ExportFieldKey.DESCRIPTION to description,
            ExportFieldKey.PHOTOS to photos
        )
    }

    private fun buildClipboardText(
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition
    ): String {
        val title = resolveTitle(values, profile)
        val description = values[ExportFieldKey.DESCRIPTION].orEmpty()
        val lines = buildFieldLines(values, profile, includeDescription = false, includePhotos = true)

        return buildString {
            appendLine("Title:")
            appendLine(title)
            appendLine()
            lines.forEach { appendLine(it) }
            appendLine()
            appendLine("Description:")
            appendLine(description)
        }.trim()
    }

    private fun buildShareText(
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition
    ): String {
        val title = resolveTitle(values, profile)
        val description = values[ExportFieldKey.DESCRIPTION].orEmpty()
        val lines = buildFieldLines(values, profile, includeDescription = false, includePhotos = false)

        return buildString {
            if (title.isNotBlank()) appendLine(title)
            lines.forEach { appendLine(it) }
            if (description.isNotBlank()) {
                appendLine()
                appendLine(description)
            }
        }.trim()
    }

    private fun buildFieldLines(
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition,
        includeDescription: Boolean,
        includePhotos: Boolean
    ): List<String> {
        val keys = profile.fieldOrdering
        val lines = mutableListOf<String>()
        keys.forEach { key ->
            if (key == ExportFieldKey.TITLE) return@forEach
            if (key == ExportFieldKey.DESCRIPTION && !includeDescription) return@forEach
            if (key == ExportFieldKey.PHOTOS && !includePhotos) return@forEach
            val line = buildFieldLine(key, values, profile)
            if (line.isNotBlank()) {
                lines.add(line)
            }
        }
        return lines
    }

    private fun buildFieldLine(
        key: ExportFieldKey,
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition
    ): String {
        val rawValue = values[key].orEmpty()
        val value = when {
            rawValue.isNotBlank() -> rawValue
            profile.missingFieldPolicy == MissingFieldPolicy.SHOW_UNKNOWN -> "Unknown"
            else -> ""
        }
        if (value.isBlank()) return ""
        val label = profile.optionalFieldLabels[key] ?: key.defaultLabel
        return "$label: $value"
    }

    private fun resolveTitle(
        values: Map<ExportFieldKey, String>,
        profile: ExportProfileDefinition
    ): String {
        val raw = values[ExportFieldKey.TITLE].orEmpty()
        return when {
            raw.isNotBlank() -> raw
            profile.missingFieldPolicy == MissingFieldPolicy.SHOW_UNKNOWN -> "Unknown"
            else -> ""
        }
    }

    private fun formatTitle(draft: ListingDraft, rules: ExportTitleRules): String {
        val base = draft.title.value.orEmpty().trim()
        val brand = formatFieldValue(draft.fields[DraftFieldKey.BRAND])
        val model = formatFieldValue(draft.fields[DraftFieldKey.MODEL])
        val parts = mutableListOf<String>()
        if (base.isNotBlank()) parts += base
        if (rules.includeBrandInTitle && brand.isNotBlank() && !containsIgnoreCase(base, brand)) {
            parts += brand
        }
        if (rules.includeModelInTitle && model.isNotBlank() && !containsIgnoreCase(base, model)) {
            parts += model
        }
        val combined = parts.joinToString(" ").trim()
        val capitalized = applyCapitalization(combined, rules.capitalization)
        return truncateTitle(capitalized, rules.maxLen)
    }

    private fun formatDescription(draft: ListingDraft, profile: ExportProfileDefinition): String {
        val rules = profile.descriptionRules
        val lines = mutableListOf<String>()
        val baseDescription = draft.description.value.orEmpty().trim()
        if (baseDescription.isNotBlank()) {
            lines += baseDescription
        }
        if (rules.includeConditionLine) {
            val condition = formatFieldValue(draft.fields[DraftFieldKey.CONDITION])
            val value = condition.ifBlank {
                if (profile.missingFieldPolicy == MissingFieldPolicy.SHOW_UNKNOWN) "Unknown" else ""
            }
            if (value.isNotBlank()) {
                lines += "Condition: $value"
            }
        }
        if (rules.includeMeasurements) {
            // No measurements available in draft; intentionally omitted.
        }
        if (rules.includeDisclaimerLine) {
            lines += "Details auto-generated; please verify before listing."
        }

        return when (rules.format) {
            DescriptionFormat.BULLETS -> lines.joinToString("\n") { "- $it" }
            DescriptionFormat.PARAGRAPH -> lines.joinToString("\n\n")
        }.trim()
    }

    private fun containsIgnoreCase(base: String, value: String): Boolean {
        if (base.isBlank() || value.isBlank()) return false
        return base.lowercase().contains(value.lowercase())
    }

    private fun applyCapitalization(value: String, mode: TitleCapitalization): String {
        return when (mode) {
            TitleCapitalization.NONE -> value
            TitleCapitalization.UPPERCASE -> value.uppercase()
            TitleCapitalization.SENTENCE_CASE -> value.replaceFirstChar { it.uppercase() }
            TitleCapitalization.TITLE_CASE -> value.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
        }
    }

    private fun truncateTitle(value: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        return if (value.length <= maxLen) value else value.substring(0, maxLen).trimEnd()
    }

    private fun formatField(field: DraftField<String>?): String {
        val value = field?.value?.trim().orEmpty()
        if (value.isBlank()) return ""
        val confidence = field?.confidence ?: 0f
        val confidenceText = if (confidence > 0f) " (${formatNumber(confidence.toDouble())})" else ""
        return "$value$confidenceText"
    }

    private fun formatFieldValue(field: DraftField<String>?): String {
        return field?.value?.trim().orEmpty()
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
