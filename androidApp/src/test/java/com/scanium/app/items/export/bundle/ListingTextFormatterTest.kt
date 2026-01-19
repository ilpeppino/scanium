package com.scanium.app.items.export.bundle

import com.scanium.app.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ListingTextFormatter.
 *
 * Tests:
 * - Single item formatting
 * - Multiple item formatting
 * - Compact list formatting
 * - NEEDS_AI flag display
 * - Photo count display
 */
class ListingTextFormatterTest {
    @Test
    fun `formatSingle includes title and description`() {
        val bundle =
            createTestBundle(
                title = "Vintage Leather Jacket",
                description = "Beautiful brown leather jacket in excellent condition",
            )

        val text = ListingTextFormatter.formatSingle(bundle)

        assertTrue(text.contains("Vintage Leather Jacket"))
        assertTrue(text.contains("Beautiful brown leather jacket in excellent condition"))
    }

    @Test
    fun `formatSingle includes bullets when present`() {
        val bundle =
            createTestBundle(
                title = "Nike Air Max",
                description = "Classic sneakers",
                bullets = listOf("Size 10", "Original box", "Never worn"),
            )

        val text = ListingTextFormatter.formatSingle(bundle)

        assertTrue(text.contains("Highlights:"))
        assertTrue(text.contains("• Size 10"))
        assertTrue(text.contains("• Original box"))
        assertTrue(text.contains("• Never worn"))
    }

    @Test
    fun `formatSingle shows NEEDS_AI warning for items without export fields`() {
        val bundle =
            createTestBundle(
                title = "Unknown Item",
                flags = setOf(ExportBundleFlag.NEEDS_AI),
            )

        val text = ListingTextFormatter.formatSingle(bundle)

        assertTrue(text.contains("[NEEDS_AI]"))
        assertTrue(text.contains("Run Export Assistant"))
    }

    @Test
    fun `formatSingle shows photo count`() {
        val bundle =
            createTestBundle(
                photoUris = listOf("/path/1.jpg", "/path/2.jpg", "/path/3.jpg"),
            )

        val text = ListingTextFormatter.formatSingle(bundle)

        assertTrue(text.contains("3 photo(s) available"))
    }

    @Test
    fun `formatMultiple includes header with item count`() {
        val bundles =
            listOf(
                createTestBundle(title = "Item 1"),
                createTestBundle(title = "Item 2"),
                createTestBundle(title = "Item 3"),
            )

        val text = ListingTextFormatter.formatMultiple(bundles)

        assertTrue(text.contains("SCANIUM EXPORT - 3 item(s)"))
    }

    @Test
    fun `formatMultiple includes numbered items`() {
        val bundles =
            listOf(
                createTestBundle(title = "First Item"),
                createTestBundle(title = "Second Item"),
            )

        val text = ListingTextFormatter.formatMultiple(bundles)

        assertTrue(text.contains("[1/2] First Item"))
        assertTrue(text.contains("[2/2] Second Item"))
    }

    @Test
    fun `formatMultiple includes footer`() {
        val bundles = listOf(createTestBundle())

        val text = ListingTextFormatter.formatMultiple(bundles)

        assertTrue(text.contains("Exported with Scanium"))
    }

    @Test
    fun `formatCompactList shows item list with one-line descriptions`() {
        val bundles =
            listOf(
                createTestBundle(
                    title = "Nike Shoes",
                    description = "Classic sneakers in great condition with original box included",
                ),
                createTestBundle(
                    title = "Adidas Jacket",
                    description = "Warm winter jacket",
                ),
            )

        val text = ListingTextFormatter.formatCompactList(bundles)

        assertTrue(text.contains("Scanium Items (2)"))
        assertTrue(text.contains("1. Nike Shoes"))
        assertTrue(text.contains("2. Adidas Jacket"))
        // Description preview is truncated to 80 chars
        assertTrue(text.contains("Classic sneakers in great condition"))
    }

    @Test
    fun `formatCompactList shows NEEDS_AI indicator`() {
        val bundles =
            listOf(
                createTestBundle(title = "Ready Item", flags = setOf(ExportBundleFlag.READY)),
                createTestBundle(title = "Needs AI Item", flags = setOf(ExportBundleFlag.NEEDS_AI)),
            )

        val text = ListingTextFormatter.formatCompactList(bundles)

        // First item should NOT have warning
        assertFalse(text.lines().find { it.contains("Ready Item") }?.contains("⚠️") ?: false)
        // Second item SHOULD have warning
        assertTrue(text.lines().find { it.contains("Needs AI Item") }?.contains("⚠️") ?: false)
    }

    @Test
    fun `formatSingle output is deterministic`() {
        val bundle =
            createTestBundle(
                title = "Test Item",
                description = "Test description",
                bullets = listOf("Bullet 1", "Bullet 2"),
            )

        val text1 = ListingTextFormatter.formatSingle(bundle)
        val text2 = ListingTextFormatter.formatSingle(bundle)

        assertEquals(text1, text2)
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
        createdAt: Long = System.currentTimeMillis(),
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
