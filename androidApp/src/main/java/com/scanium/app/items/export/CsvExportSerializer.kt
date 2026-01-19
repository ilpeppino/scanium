package com.scanium.app.items.export

import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
class CsvExportSerializer(
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        },
) {
    fun serialize(payload: ExportPayload): String =
        buildString {
            writeTo(this, payload.items)
        }

    fun writeTo(
        output: Appendable,
        items: List<ExportItem>,
        imageFilenamesProvider: (ExportItem) -> List<String> = { item -> defaultImageFilenames(item) },
    ) {
        output.append(COLUMN_ORDER.joinToString(","))
        items.forEach { item ->
            output.append("\n")
            output.append(serializeItem(item, imageFilenamesProvider(item)))
        }
    }

    private fun serializeItem(
        item: ExportItem,
        imageFilenames: List<String>,
    ): String {
        val attributesJson =
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                item.attributes.toSortedMap(),
            )

        // Generate semicolon-separated list of all image filenames
        val allImageFilenames = imageFilenames.joinToString(";")

        val values =
            listOf(
                item.id,
                item.title,
                item.description,
                item.category,
                attributesJson,
                item.priceMin?.toString().orEmpty(),
                item.priceMax?.toString().orEmpty(),
                allImageFilenames,
            )
        return values.joinToString(",") { csvEscape(it) }
    }

    private fun defaultImageFilenames(item: ExportItem): List<String> {
        val refs = item.imageRefs.ifEmpty { listOfNotNull(item.imageRef) }
        if (refs.isEmpty()) {
            return emptyList()
        }
        return refs.mapIndexedNotNull { index, ref ->
            imageFilename(item.id, ref, index).takeIf { it.isNotBlank() }
        }
    }

    private fun imageFilename(
        itemId: String,
        imageRef: ImageRef?,
        index: Int,
    ): String =
        when (imageRef) {
            is ImageRef.CacheKey -> imageRef.key
            is ImageRef.Bytes -> "item_${itemId}_${String.format("%03d", index + 1)}.jpg"
            else -> ""
        }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        if (!needsQuotes) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    companion object {
        val COLUMN_ORDER =
            listOf(
                "item_id",
                "title",
                "description",
                "category",
                "attributes_json",
                "price_min",
                "price_max",
                "image_filenames", // Changed from image_filename to image_filenames (semicolon-separated list)
            )
    }
}
