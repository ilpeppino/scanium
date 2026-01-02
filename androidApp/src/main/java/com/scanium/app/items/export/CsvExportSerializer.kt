package com.scanium.app.items.export

import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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
        imageFilenameProvider: (ExportItem) -> String = { item -> imageFilename(item.imageRef) },
    ) {
        output.append(COLUMN_ORDER.joinToString(","))
        items.forEach { item ->
            output.append("\n")
            output.append(serializeItem(item, imageFilenameProvider(item)))
        }
    }

    private fun serializeItem(
        item: ExportItem,
        imageFilename: String,
    ): String {
        val attributesJson =
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                item.attributes.toSortedMap(),
            )
        val values =
            listOf(
                item.id,
                item.title,
                item.description,
                item.category,
                attributesJson,
                item.priceMin?.toString().orEmpty(),
                item.priceMax?.toString().orEmpty(),
                imageFilename,
            )
        return values.joinToString(",") { csvEscape(it) }
    }

    private fun imageFilename(imageRef: ImageRef?): String =
        when (imageRef) {
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
        val COLUMN_ORDER =
            listOf(
                "item_id",
                "title",
                "description",
                "category",
                "attributes_json",
                "price_min",
                "price_max",
                "image_filename",
            )
    }
}
