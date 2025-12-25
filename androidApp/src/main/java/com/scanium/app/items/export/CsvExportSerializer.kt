package com.scanium.app.items.export

import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import com.scanium.core.models.image.ImageRef
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class CsvExportSerializer(
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
    }
) {
    fun serialize(payload: ExportPayload): String = buildString {
        append(COLUMN_ORDER.joinToString(","))
        payload.items.forEach { item ->
            append("\n")
            append(serializeItem(item))
        }
    }

    private fun serializeItem(item: ExportItem): String {
        val attributesJson = json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            item.attributes.toSortedMap()
        )
        val values = listOf(
            item.id,
            item.title,
            item.description,
            item.category,
            attributesJson,
            item.priceMin?.toString().orEmpty(),
            item.priceMax?.toString().orEmpty(),
            imageFilename(item.imageRef)
        )
        return values.joinToString(",") { csvEscape(it) }
    }

    private fun imageFilename(imageRef: ImageRef?): String = when (imageRef) {
        is ImageRef.CacheKey -> imageRef.key
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
        val COLUMN_ORDER = listOf(
            "item_id",
            "title",
            "description",
            "category",
            "attributes_json",
            "price_min",
            "price_max",
            "image_filename"
        )
    }
}
