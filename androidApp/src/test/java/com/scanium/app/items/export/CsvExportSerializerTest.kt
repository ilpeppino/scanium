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
        val item =
            ExportItem(
                id = "item-1",
                title = "Red, \"Blue\"",
                description = "Desc",
                category = "Cat",
                attributes =
                    mapOf(
                        "note" to "He said \"hi\"",
                        "color" to "red",
                    ),
                priceMin = 10.0,
                priceMax = 20.0,
                imageRef = null,
            )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        val csv = CsvExportSerializer().serialize(payload)

        val lines = csv.split("\n")
        assertThat(lines[0]).isEqualTo(CsvExportSerializer.COLUMN_ORDER.joinToString(","))
        val expectedAttributesJson =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                item.attributes.toSortedMap(),
            )
        val expectedRow =
            listOf(
                csvEscape(item.id),
                csvEscape(item.title),
                csvEscape(item.description),
                csvEscape(item.category),
                csvEscape(expectedAttributesJson),
                csvEscape(item.priceMin.toString()),
                csvEscape(item.priceMax.toString()),
                "",
            ).joinToString(",")
        assertThat(lines[1]).isEqualTo(expectedRow)
    }

    @Test
    fun writeTo_usesCustomImageFilenameProvider() {
        val item =
            ExportItem(
                id = "item-99",
                title = "Item",
                description = "Desc",
                category = "Cat",
                attributes = emptyMap(),
                imageRef = null,
            )
        val output = StringBuilder()

        CsvExportSerializer().writeTo(output, listOf(item)) {
            listOf("images/item_${it.id}.jpg")
        }

        val lines = output.toString().split("\n")
        assertThat(lines).hasSize(2)
        assertThat(lines[1]).contains(",images/item_${item.id}.jpg")
    }

    @Test
    fun serialize_withMultipleImages_includesSemicolonSeparatedList() {
        // Given: item with multiple images
        val imageRefs = listOf(
            com.scanium.shared.core.models.model.ImageRef.CacheKey("image1.jpg", "image/jpeg", 100, 100),
            com.scanium.shared.core.models.model.ImageRef.CacheKey("image2.jpg", "image/jpeg", 100, 100),
            com.scanium.shared.core.models.model.ImageRef.CacheKey("image3.jpg", "image/jpeg", 100, 100),
        )

        val item =
            ExportItem(
                id = "item-multi",
                title = "Multi Photo Item",
                description = "Item with multiple photos",
                category = "Test",
                attributes = emptyMap(),
                priceMin = 10.0,
                priceMax = 20.0,
                imageRef = imageRefs.first(),
                imageRefs = imageRefs,
            )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        // When: serializing to CSV
        val csv = CsvExportSerializer().serialize(payload)

        // Then: all images are included as semicolon-separated list
        val lines = csv.split("\n")
        assertThat(lines).hasSize(2) // Header + 1 data row

        // Verify header changed to image_filenames (plural)
        assertThat(lines[0]).contains("image_filenames")

        // Verify data row contains all images separated by semicolons
        val dataRow = lines[1]
        assertThat(dataRow).contains("image1.jpg;image2.jpg;image3.jpg")

        // Verify no duplicates (each image appears exactly once)
        val imageColumn = dataRow.split(",").last() // Last column is images
        val imageList = imageColumn.split(";")
        assertThat(imageList).hasSize(3)
        assertThat(imageList.toSet()).hasSize(3) // No duplicates
    }

    @Test
    fun serialize_withSingleImage_fallsBackToCompatibleFormat() {
        // Given: item with single image (backward compatibility case)
        val imageRef = com.scanium.shared.core.models.model.ImageRef.CacheKey("single.jpg", "image/jpeg", 100, 100)

        val item =
            ExportItem(
                id = "item-single",
                title = "Single Photo Item",
                description = "Item with one photo",
                category = "Test",
                attributes = emptyMap(),
                imageRef = imageRef,
                imageRefs = emptyList(), // Empty imageRefs, should fallback to imageRef
            )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        // When: serializing to CSV
        val csv = CsvExportSerializer().serialize(payload)

        // Then: single image is included without semicolon
        val lines = csv.split("\n")
        val dataRow = lines[1]
        assertThat(dataRow).contains("single.jpg")
        assertThat(dataRow).doesNotContain(";") // No semicolon for single image
    }

    @Test
    fun serialize_withNoImages_outputsEmptyImageColumn() {
        // Given: item with no images
        val item =
            ExportItem(
                id = "item-no-photo",
                title = "No Photo Item",
                description = "Item without photos",
                category = "Test",
                attributes = emptyMap(),
                imageRef = null,
                imageRefs = emptyList(),
            )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        // When: serializing to CSV
        val csv = CsvExportSerializer().serialize(payload)

        // Then: image column is empty
        val lines = csv.split("\n")
        val dataRow = lines[1]
        val columns = dataRow.split(",")
        val imageColumn = columns.last()
        assertThat(imageColumn).isEmpty()
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
