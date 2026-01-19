package com.scanium.app.selling.assistant.usecases

import android.content.Context
import com.scanium.app.items.ItemLocalizer
import com.scanium.app.ScannedItem
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ListingDraft
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.shared.core.models.assistant.AttributeSource

/**
 * Pure use cases for assistant domain logic.
 *
 * Part of ARCH-001: Extracted from AssistantViewModel for testability.
 * All functions are pure (no I/O, deterministic, no side effects) except where
 * explicitly noted for Context-dependent localization.
 */
object AssistantUseCases {
    /**
     * Compute context-aware suggested questions based on item category.
     *
     * @param snapshots The item context snapshots to analyze.
     * @return A list of up to 3 suggested questions.
     */
    fun computeSuggestedQuestions(snapshots: List<ItemContextSnapshot>): List<String> {
        val suggestions = mutableListOf<String>()
        val snapshot = snapshots.firstOrNull() ?: return defaultSuggestions()
        val category = snapshot.category?.lowercase() ?: ""

        // Check what's missing
        val hasBrand = snapshot.attributes?.any { it.key.equals("brand", ignoreCase = true) } == true
        val hasColor = snapshot.attributes?.any { it.key.equals("color", ignoreCase = true) } == true
        val title = snapshot.title
        val description = snapshot.description
        val hasTitle = !title.isNullOrBlank() && title.length > 5
        val hasDescription = !description.isNullOrBlank() && description.length > 20
        val priceEstimate = snapshot.priceEstimate
        val hasPrice = priceEstimate != null && priceEstimate > 0

        // Category-specific suggestions
        when {
            category.contains(
                "electronic",
            ) || category.contains("phone") || category.contains("computer") || category.contains("camera") -> {
                if (!hasBrand) suggestions.add("What brand and model is this?")
                suggestions.add("What's the storage capacity?")
                suggestions.add("Does it power on? Any screen issues?")
                suggestions.add("Are all accessories included?")
                suggestions.add("Any scratches or dents?")
            }

            category.contains("furniture") || category.contains("home") || category.contains("decor") || category.contains("chair") ||
                category.contains(
                    "table",
                )
            -> {
                suggestions.add("What are the dimensions (H x W x D)?")
                suggestions.add("What material is it made of?")
                suggestions.add("Any scratches, stains, or wear?")
                suggestions.add("Is assembly required?")
                if (!hasColor) suggestions.add("What color/finish is it?")
            }

            category.contains("fashion") || category.contains("clothing") || category.contains("shoes") || category.contains("apparel") -> {
                if (!hasBrand) suggestions.add("What brand is this?")
                suggestions.add("What size is this?")
                if (!hasColor) suggestions.add("What color is it?")
                suggestions.add("What's the fabric/material?")
                suggestions.add("Any signs of wear or defects?")
            }

            category.contains("toy") || category.contains("game") || category.contains("puzzle") -> {
                suggestions.add("Is it complete with all pieces?")
                suggestions.add("What age range is it for?")
                suggestions.add("Does it require batteries?")
                if (!hasBrand) suggestions.add("What brand is this?")
            }

            category.contains("book") || category.contains("media") || category.contains("dvd") || category.contains("vinyl") -> {
                suggestions.add("Who is the author/artist?")
                suggestions.add("Is this a first edition?")
                suggestions.add("Condition of binding/pages?")
                suggestions.add("Any markings or highlights?")
            }

            category.contains("sport") || category.contains("fitness") || category.contains("outdoor") || category.contains("bike") -> {
                if (!hasBrand) suggestions.add("What brand is this?")
                suggestions.add("What size is it?")
                suggestions.add("Any damage or wear?")
                suggestions.add("Does it include accessories?")
            }

            else -> {
                // General fallback suggestions
                if (!hasBrand) suggestions.add("What brand is this?")
                if (!hasColor) suggestions.add("What color is this item?")
                suggestions.add("What details should I add?")
                suggestions.add("Any defects to mention?")
            }
        }

        // Always add these if not covered
        if (!hasTitle || (snapshot.title?.length ?: 0) < 15) {
            suggestions.add("Suggest a better title")
        }
        if (!hasDescription) {
            suggestions.add("Help me write a description")
        }
        if (!hasPrice) {
            suggestions.add("What should I price this at?")
        }

        // Remove duplicates, shuffle, and take 3
        return suggestions.distinct().shuffled().take(3)
    }

    /**
     * Returns default suggested questions when no context is available.
     *
     * @return A list of default suggested questions.
     */
    fun defaultSuggestions(): List<String> =
        listOf(
            "Suggest a better title",
            "What details should I add?",
            "Estimate price range",
        )

    /**
     * Updates a draft from a payload map.
     *
     * @param draft The original draft to update.
     * @param payload The payload map containing field updates.
     * @return The updated draft with recomputed completeness.
     */
    fun updateDraftFromPayload(
        draft: ListingDraft,
        payload: Map<String, String>,
    ): ListingDraft {
        var updated = draft
        val now = System.currentTimeMillis()

        payload["title"]?.let { title ->
            updated =
                updated.copy(
                    title = DraftField(title, confidence = 1f, source = DraftProvenance.USER_EDITED),
                    updatedAt = now,
                )
        }

        payload["description"]?.let { description ->
            updated =
                updated.copy(
                    description = DraftField(description, confidence = 1f, source = DraftProvenance.USER_EDITED),
                    updatedAt = now,
                )
        }

        payload["price"]?.toDoubleOrNull()?.let { price ->
            updated =
                updated.copy(
                    price = DraftField(price, confidence = 1f, source = DraftProvenance.USER_EDITED),
                    updatedAt = now,
                )
        }

        val updatedFields = updated.fields.toMutableMap()
        payload.filterKeys { it.startsWith("field.") }.forEach { (key, value) ->
            val fieldKey = DraftFieldKey.fromWireValue(key.removePrefix("field."))
            if (fieldKey != null) {
                updatedFields[fieldKey] = DraftField(value, confidence = 1f, source = DraftProvenance.USER_EDITED)
            }
        }
        updated = updated.copy(fields = updatedFields, updatedAt = now)

        return updated.recomputeCompleteness()
    }

    /**
     * Merges snapshot attributes with item attributes.
     *
     * This function requires a Context for localization of condition names.
     *
     * @param context The Android context for localization.
     * @param snapshot The base snapshot to merge into.
     * @param item The scanned item containing additional attributes.
     * @return The snapshot with merged attributes.
     */
    fun mergeSnapshotAttributes(
        context: Context,
        snapshot: ItemContextSnapshot,
        item: ScannedItem?,
    ): ItemContextSnapshot {
        if (item == null) return snapshot

        val merged = linkedMapOf<String, ItemAttributeSnapshot>()

        fun keyFor(attributeKey: String) = attributeKey.lowercase()

        fun addIfMissing(attribute: ItemAttributeSnapshot) {
            val key = keyFor(attribute.key)
            if (key !in merged) {
                merged[key] = attribute
            }
        }

        item.attributes.forEach { (key, attr) ->
            val source = if (attr.source == "user") AttributeSource.USER else AttributeSource.DETECTED
            merged[keyFor(key)] =
                ItemAttributeSnapshot(
                    key = key,
                    value = attr.value,
                    confidence = attr.confidence,
                    source = source,
                )
        }

        // Add localized condition
        item.condition?.let { condition ->
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "condition",
                    value = ItemLocalizer.getConditionName(context, condition),
                    confidence = 1.0f,
                    source = AttributeSource.USER,
                ),
            )
        }

        snapshot.attributes.forEach { addIfMissing(it) }

        val vision = item.visionAttributes
        vision.primaryBrand?.let { brand ->
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "brand",
                    value = brand,
                    confidence = vision.logos.maxOfOrNull { it.score },
                    source = AttributeSource.DETECTED,
                ),
            )
        }

        val colors = vision.colors.sortedByDescending { it.score }
        colors.firstOrNull()?.let { color ->
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "color",
                    value = color.name,
                    confidence = color.score,
                    source = AttributeSource.DETECTED,
                ),
            )
        }

        vision.itemType?.takeIf { it.isNotBlank() }?.let { itemType ->
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "itemType",
                    value = itemType,
                    confidence = vision.labels.maxOfOrNull { it.score },
                    source = AttributeSource.DETECTED,
                ),
            )
        }

        val labelHints =
            vision.labels
                .map { it.name }
                .distinct()
                .take(3)
        if (labelHints.isNotEmpty()) {
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "labelHints",
                    value = labelHints.joinToString(", "),
                    confidence = vision.labels.maxOfOrNull { it.score },
                    source = AttributeSource.DETECTED,
                ),
            )
        }

        val ocrText =
            item.recognizedText?.takeIf { it.isNotBlank() }
                ?: vision.ocrText?.takeIf { it.isNotBlank() }
        ocrText?.let { text ->
            addIfMissing(
                ItemAttributeSnapshot(
                    key = "recognizedText",
                    value = if (text.length > 200) text.take(200) + "..." else text,
                    confidence = 0.8f,
                    source = AttributeSource.DETECTED,
                ),
            )
        }

        return snapshot.copy(attributes = merged.values.toList())
    }
}
