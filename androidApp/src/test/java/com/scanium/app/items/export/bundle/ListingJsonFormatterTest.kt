package com.scanium.app.items.export.bundle

import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.app.ItemCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ListingJsonFormatter.
 *
 * Tests:
 * - JSON structure for single item
 * - JSON array for multiple items
 * - Attribute metadata preservation
 * - Export status flags
 */
@RunWith(RobolectricTestRunner::class)
class ListingJsonFormatterTest {
    @Test
    fun `formatSingle produces valid JSON with required fields`() {
        val bundle =
            createTestBundle(
                itemId = "item-123",
                title = "Nike Air Max",
                description = "Classic sneakers in excellent condition",
                category = ItemCategory.FASHION,
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        assertEquals("item-123", json.getString("id"))
        assertEquals("Nike Air Max", json.getString("title"))
        assertEquals("Classic sneakers in excellent condition", json.getString("description"))
        assertEquals("FASHION", json.getString("category"))
    }

    @Test
    fun `formatSingle includes bullets array when present`() {
        val bundle =
            createTestBundle(
                bullets = listOf("Size 10", "Original box", "Never worn"),
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        assertTrue(json.has("bullets"))
        val bullets = json.getJSONArray("bullets")
        assertEquals(3, bullets.length())
        assertEquals("Size 10", bullets.getString(0))
        assertEquals("Original box", bullets.getString(1))
        assertEquals("Never worn", bullets.getString(2))
    }

    @Test
    fun `formatSingle includes attributes with metadata`() {
        val bundle =
            createTestBundle(
                attributes =
                    mapOf(
                        "brand" to ItemAttribute(value = "Nike", confidence = 0.95f, source = "vision"),
                        "color" to ItemAttribute(value = "Blue", confidence = 0.8f, source = "user"),
                    ),
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        val attrs = json.getJSONObject("attributes")
        assertTrue(attrs.has("brand"))
        assertTrue(attrs.has("color"))

        val brandAttr = attrs.getJSONObject("brand")
        assertEquals("Nike", brandAttr.getString("value"))
        assertEquals(0.95, brandAttr.getDouble("confidence"), 0.01)
        assertEquals("vision", brandAttr.getString("source"))

        val colorAttr = attrs.getJSONObject("color")
        assertEquals("Blue", colorAttr.getString("value"))
        assertEquals(0.8, colorAttr.getDouble("confidence"), 0.01)
        assertEquals("user", colorAttr.getString("source"))
    }

    @Test
    fun `formatSingle includes photos array`() {
        val bundle =
            createTestBundle(
                primaryPhotoUri = "/path/primary.jpg",
                photoUris = listOf("/path/1.jpg", "/path/2.jpg"),
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        val photos = json.getJSONArray("photos")
        // primary + 2 additional (deduplicated)
        assertEquals(3, photos.length())
        // primary first
        assertEquals("/path/primary.jpg", photos.getString(0))
        assertEquals("/path/1.jpg", photos.getString(1))
        assertEquals("/path/2.jpg", photos.getString(2))
        // photos.length()
        assertEquals(3, json.getInt("photoCount"))
    }

    @Test
    fun `formatSingle includes export status flags`() {
        val bundle =
            createTestBundle(
                flags = setOf(ExportBundleFlag.READY),
                confidenceTier = "HIGH",
                exportModel = "gpt-4",
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        val status = json.getJSONObject("exportStatus")
        assertTrue(status.getBoolean("ready"))
        assertFalse(status.getBoolean("needsAi"))
        assertTrue(status.getBoolean("hasPhotos"))
        assertEquals("HIGH", status.getString("confidenceTier"))
        assertEquals("gpt-4", status.getString("model"))
    }

    @Test
    fun `formatSingle marks NEEDS_AI correctly`() {
        val bundle =
            createTestBundle(
                flags = setOf(ExportBundleFlag.NEEDS_AI, ExportBundleFlag.NO_PHOTOS),
            )

        val json = ListingJsonFormatter.formatSingle(bundle)

        val status = json.getJSONObject("exportStatus")
        assertFalse(status.getBoolean("ready"))
        assertTrue(status.getBoolean("needsAi"))
        assertFalse(status.getBoolean("hasPhotos"))
    }

    @Test
    fun `formatMultiple creates JSON array with all items`() {
        val bundles =
            listOf(
                createTestBundle(itemId = "item-1", title = "First Item"),
                createTestBundle(itemId = "item-2", title = "Second Item"),
                createTestBundle(itemId = "item-3", title = "Third Item"),
            )

        val jsonArray = ListingJsonFormatter.formatMultiple(bundles)

        assertEquals(3, jsonArray.length())
        assertEquals("item-1", jsonArray.getJSONObject(0).getString("id"))
        assertEquals("item-2", jsonArray.getJSONObject(1).getString("id"))
        assertEquals("item-3", jsonArray.getJSONObject(2).getString("id"))
    }

    @Test
    fun `formatSimpleAttributes returns flat key-value map`() {
        val bundle =
            createTestBundle(
                attributes =
                    mapOf(
                        "brand" to ItemAttribute(value = "Nike", confidence = 0.95f),
                        "color" to ItemAttribute(value = "Blue", confidence = 0.8f),
                    ),
            )

        val simpleAttrs = ListingJsonFormatter.formatSimpleAttributes(bundle)

        assertEquals("Nike", simpleAttrs.getString("brand"))
        assertEquals("Blue", simpleAttrs.getString("color"))
        // Simple format should not have nested objects
        assertTrue(simpleAttrs.get("brand") is String)
    }

    @Test
    fun `formatSingleString produces valid JSON string`() {
        val bundle = createTestBundle(title = "Test Item")

        val jsonString = ListingJsonFormatter.formatSingleString(bundle)

        // Should be parseable
        val parsed = JSONObject(jsonString)
        assertEquals("Test Item", parsed.getString("title"))
    }

    @Test
    fun `formatSingle output is deterministic`() {
        val bundle =
            createTestBundle(
                itemId = "test-id",
                title = "Test Item",
                description = "Test description",
                attributes =
                    mapOf(
                        "brand" to ItemAttribute(value = "Nike"),
                        "color" to ItemAttribute(value = "Blue"),
                    ),
            )

        val json1 = ListingJsonFormatter.formatSingle(bundle).toString()
        val json2 = ListingJsonFormatter.formatSingle(bundle).toString()

        assertEquals(json1, json2)
    }

    private fun createTestBundle(
        itemId: String = "test-id",
        title: String = "Test Title",
        description: String = "Test Description",
        bullets: List<String> = emptyList(),
        category: ItemCategory = ItemCategory.FASHION,
        attributes: Map<String, ItemAttribute> = emptyMap(),
        photoUris: List<String> = emptyList(),
        primaryPhotoUri: String? = null,
        // Fixed timestamp for determinism
        createdAt: Long = 1704067200000L,
        flags: Set<ExportBundleFlag> = setOf(ExportBundleFlag.READY),
        confidenceTier: String? = null,
        exportModel: String? = null,
    ): ExportItemBundle {
        return ExportItemBundle(
            itemId = itemId,
            title = title,
            description = description,
            bullets = bullets,
            category = category,
            attributes = attributes,
            photoUris = photoUris,
            primaryPhotoUri = primaryPhotoUri,
            createdAt = createdAt,
            flags = flags,
            confidenceTier = confidenceTier,
            exportModel = exportModel,
        )
    }
}
