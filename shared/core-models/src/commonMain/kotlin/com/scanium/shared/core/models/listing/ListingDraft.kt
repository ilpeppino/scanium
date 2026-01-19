package com.scanium.shared.core.models.listing

import com.scanium.shared.core.models.items.ScannedItem
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Portable listing draft model used by Phase A listing creation.
 *
 * Keeps Android-free types and deterministic defaults so the same
 * ScannedItem input always produces identical draft output.
 */
data class ListingDraft(
    val id: String,
    val itemId: String,
    val profile: ExportProfileId = ExportProfileId.GENERIC,
    val title: DraftField<String>,
    val description: DraftField<String>,
    val fields: Map<DraftFieldKey, DraftField<String>> = emptyMap(),
    val price: DraftField<Double>,
    val photos: List<DraftPhotoRef> = emptyList(),
    val status: DraftStatus = DraftStatus.DRAFT,
    val createdAt: Long,
    val updatedAt: Long,
    val completeness: DraftCompleteness =
        DraftCompleteness.compute(
            title = title,
            fields = fields,
            price = price,
            photos = photos,
        ),
) {
    fun recomputeCompleteness(): ListingDraft {
        return copy(
            completeness =
                DraftCompleteness.compute(
                    title = title,
                    fields = fields,
                    price = price,
                    photos = photos,
                ),
        )
    }
}

/**
 * Draft field metadata carrying provenance and confidence.
 */
data class DraftField<T>(
    val value: T?,
    val confidence: Float = 0f,
    val source: DraftProvenance = DraftProvenance.UNKNOWN,
)

/**
 * Provenance for draft values (model vs user edits).
 */
enum class DraftProvenance {
    DETECTED,
    USER_EDITED,
    DEFAULT,
    UNKNOWN,
}

enum class DraftFieldKey(val wireValue: String) {
    CATEGORY("category"),
    CONDITION("condition"),
    BRAND("brand"),
    MODEL("model"),
    COLOR("color"),

    /** Sellable item type noun (e.g., "Lip Balm", "T-Shirt", "Tissue Box") */
    ITEM_TYPE("itemType"),

    /** OCR detected text from the product (filtered snippets) */
    DETECTED_TEXT("detectedText"),
    ;

    companion object {
        fun fromWireValue(value: String): DraftFieldKey? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

/**
 * Stable export profile identifier for formatting output.
 */
@Serializable(with = ExportProfileIdSerializer::class)
data class ExportProfileId(val value: String) {
    companion object {
        val GENERIC = ExportProfileId("GENERIC")
    }
}

object ExportProfileIdSerializer : KSerializer<ExportProfileId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExportProfileId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ExportProfileId,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): ExportProfileId {
        return ExportProfileId(decoder.decodeString())
    }
}

enum class DraftStatus {
    DRAFT,
    SAVED,
}

data class DraftPhotoRef(
    val image: ImageRef,
    val source: DraftProvenance = DraftProvenance.UNKNOWN,
)

enum class DraftRequiredField {
    TITLE,
    CATEGORY,
    CONDITION,
    PRICE,
    PHOTO,
}

data class DraftCompleteness(
    val score: Int,
    val missing: Set<DraftRequiredField>,
) {
    companion object {
        fun compute(
            title: DraftField<String>,
            fields: Map<DraftFieldKey, DraftField<String>>,
            price: DraftField<Double>,
            photos: List<DraftPhotoRef>,
        ): DraftCompleteness {
            val missing = mutableSetOf<DraftRequiredField>()

            if (title.value.isNullOrBlank()) missing += DraftRequiredField.TITLE
            val categoryValue = fields[DraftFieldKey.CATEGORY]?.value
            if (categoryValue.isNullOrBlank()) missing += DraftRequiredField.CATEGORY
            val conditionValue = fields[DraftFieldKey.CONDITION]?.value
            if (conditionValue.isNullOrBlank()) missing += DraftRequiredField.CONDITION
            val priceValue = price.value
            if (priceValue == null || priceValue <= 0.0) missing += DraftRequiredField.PRICE
            if (photos.isEmpty()) missing += DraftRequiredField.PHOTO

            val requiredCount = DraftRequiredField.entries.size
            val completed = requiredCount - missing.size
            val score = ((completed.toDouble() / requiredCount.toDouble()) * 100).toInt()

            return DraftCompleteness(score = score, missing = missing)
        }
    }
}

/**
 * Deterministic builder that converts a ScannedItem into a ListingDraft.
 */
object ListingDraftBuilder {
    private const val TITLE_PREFIX = "Used"
    private const val MAX_TITLE_LENGTH = 80

    fun build(item: ScannedItem<*>): ListingDraft {
        val titleValue = buildTitle(item)
        val descriptionValue = buildDescription(item)
        val fields = buildFields(item)
        val photos = buildPhotos(item)
        val priceValue = (item.priceRange.first + item.priceRange.second) / 2.0

        val titleField =
            DraftField(
                value = titleValue,
                confidence = item.confidence,
                source = DraftProvenance.DETECTED,
            )
        val descriptionField =
            DraftField(
                value = descriptionValue,
                confidence = item.confidence,
                source = DraftProvenance.DEFAULT,
            )
        val priceField =
            DraftField(
                value = priceValue,
                confidence = item.confidence,
                source = DraftProvenance.DETECTED,
            )

        val timestamp = item.timestamp
        val draftId = "draft_${item.id}"

        return ListingDraft(
            id = draftId,
            itemId = item.id,
            profile = ExportProfileId.GENERIC,
            title = titleField,
            description = descriptionField,
            fields = fields,
            price = priceField,
            photos = photos,
            status = DraftStatus.DRAFT,
            createdAt = timestamp,
            updatedAt = timestamp,
        ).recomputeCompleteness()
    }

    private fun buildTitle(item: ScannedItem<*>): String {
        // Use displayLabel which has smart priority logic for brand + itemType + color
        // This ensures titles like "Used Labello Lip Balm · Blue" instead of generic "Used Cosmetics"
        val displayLabel = item.displayLabel.trim().takeUnless { it.isEmpty() }
        val label = item.labelText?.trim().takeUnless { it.isNullOrEmpty() }
        val category = item.category.displayName.trim().takeUnless { it.isEmpty() }
        val base = displayLabel ?: label ?: category ?: "Item"
        val title = "$TITLE_PREFIX ${base.replaceFirstChar { it.uppercase() }}".trim()
        return if (title.length <= MAX_TITLE_LENGTH) title else title.substring(0, MAX_TITLE_LENGTH).trimEnd()
    }

    private fun buildDescription(item: ScannedItem<*>): String {
        val label = item.labelText?.trim().takeUnless { it.isNullOrEmpty() }
        val category = item.category.displayName
        val parts = listOfNotNull(label, category.takeIf { it.isNotBlank() })
        val subject = if (parts.isEmpty()) "item" else parts.joinToString(" ")
        return "Detected $subject from Scanium scan."
    }

    private fun buildFields(item: ScannedItem<*>): Map<DraftFieldKey, DraftField<String>> {
        val fields = LinkedHashMap<DraftFieldKey, DraftField<String>>()
        fields[DraftFieldKey.CATEGORY] =
            DraftField(
                value = item.category.displayName,
                confidence = item.confidence,
                source = DraftProvenance.DETECTED,
            )
        fields[DraftFieldKey.CONDITION] =
            DraftField(
                value = "Used",
                confidence = 1f,
                source = DraftProvenance.DEFAULT,
            )

        // BRAND: prefer vision-detected brand, fallback to labelText
        val brand =
            item.visionAttributes.primaryBrand
                ?: item.labelText?.takeIf { it.isNotBlank() }
        brand?.let {
            fields[DraftFieldKey.BRAND] =
                DraftField(
                    value = it,
                    confidence = item.visionAttributes.logos.maxOfOrNull { logo -> logo.score } ?: item.confidence,
                    source = DraftProvenance.DETECTED,
                )
        }

        // COLOR: from vision attributes (join unique color names)
        val colors = item.visionAttributes.colors
        if (colors.isNotEmpty()) {
            val colorNames = colors.map { it.name }.distinct().joinToString(", ")
            fields[DraftFieldKey.COLOR] =
                DraftField(
                    value = colorNames,
                    confidence = colors.mapNotNull { it.score }.average().toFloat().takeIf { !it.isNaN() } ?: 0.8f,
                    source = DraftProvenance.DETECTED,
                )
        }

        // ITEM_TYPE: sellable item type noun (e.g., "Lip Balm", "T-Shirt")
        // Priority: visionAttributes.itemType > attributes["itemType"] > first label
        val itemType =
            item.visionAttributes.itemType?.takeIf { it.isNotBlank() }
                ?: item.attributes["itemType"]?.value?.takeIf { it.isNotBlank() }
                ?: item.visionAttributes.labels.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        itemType?.let {
            val confidence = item.visionAttributes.labels.maxOfOrNull { label -> label.score } ?: 0.7f
            fields[DraftFieldKey.ITEM_TYPE] =
                DraftField(
                    value = it,
                    confidence = confidence,
                    source = DraftProvenance.DETECTED,
                )
        }

        // DETECTED_TEXT: OCR text from product (filtered, first meaningful snippet)
        val ocrText =
            item.visionAttributes.ocrText?.takeIf { it.isNotBlank() }
                ?: item.attributes["ocrText"]?.value?.takeIf { it.isNotBlank() }
        ocrText?.let { text ->
            // Take first 200 chars, filtering out very short lines
            val filtered =
                text.lineSequence()
                    .map { it.trim() }
                    .filter { it.length >= 3 }
                    .take(5)
                    .joinToString(" | ")
                    .take(200)
            if (filtered.isNotBlank()) {
                fields[DraftFieldKey.DETECTED_TEXT] =
                    DraftField(
                        value = filtered,
                        confidence = 0.8f,
                        source = DraftProvenance.DETECTED,
                    )
            }
        }

        return fields
    }

    private fun buildPhotos(item: ScannedItem<*>): List<DraftPhotoRef> {
        val primary = item.thumbnailRef ?: item.thumbnail
        return primary?.let { imageRef ->
            listOf(DraftPhotoRef(image = imageRef, source = DraftProvenance.DETECTED))
        } ?: emptyList()
    }
}
