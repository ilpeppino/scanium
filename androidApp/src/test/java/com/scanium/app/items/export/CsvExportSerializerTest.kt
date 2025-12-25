package com.scanium.app.items.export

import com.google.common.truth.Truth.assertThat
import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Test

class CsvExportSerializerTest {
    @Test
    fun serialize_includesHeaderAndEscapesValues() {
        val item = ExportItem(
            id = "item-1",
            title = "Red, \"Blue\"",
            description = "Desc",
            category = "Cat",
            attributes = mapOf(
                "note" to "He said \"hi\"",
                "color" to "red"
            ),
            priceMin = 10.0,
            priceMax = 20.0,
            imageRef = null
        )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        val csv = CsvExportSerializer().serialize(payload)

        val lines = csv.split("\n")
        assertThat(lines[0]).isEqualTo(CsvExportSerializer.COLUMN_ORDER.joinToString(","))
        val expectedAttributesJson = Json {
            encodeDefaults = true
            explicitNulls = false
        }.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            item.attributes.toSortedMap()
        )
        val expectedRow = listOf(
            csvEscape(item.id),
            csvEscape(item.title),
            csvEscape(item.description),
            csvEscape(item.category),
            csvEscape(expectedAttributesJson),
            csvEscape(item.priceMin.toString()),
            csvEscape(item.priceMax.toString()),
            ""
        ).joinToString(",")
        assertThat(lines[1]).isEqualTo(expectedRow)
    }

    @Test
    fun writeTo_usesCustomImageFilenameProvider() {
        val item = ExportItem(
            id = "item-99",
            title = "Item",
            description = "Desc",
            category = "Cat",
            attributes = emptyMap(),
            imageRef = null
        )
        val output = StringBuilder()

        CsvExportSerializer().writeTo(output, listOf(item)) {
            "images/item_${it.id}.jpg"
        }

        val lines = output.toString().split("\n")
        assertThat(lines).hasSize(2)
        assertThat(lines[1]).contains(",images/item_${item.id}.jpg")
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        if (!needsQuotes) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
