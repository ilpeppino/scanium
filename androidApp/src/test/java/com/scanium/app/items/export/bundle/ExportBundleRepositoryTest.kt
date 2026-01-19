package com.scanium.app.items.export.bundle

import android.content.Context
import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem
import com.scanium.app.items.photos.ItemPhotoManager
import com.scanium.shared.core.models.items.ItemAttribute
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Unit tests for ExportBundleRepository.
 *
 * Tests:
 * - Text source priority (export fields > summaryText > generated)
 * - Flag assignment (READY, NEEDS_AI, NO_PHOTOS, USER_EDITED)
 * - Export limit enforcement
 */
@RunWith(RobolectricTestRunner::class)
class ExportBundleRepositoryTest {
    private lateinit var context: Context
    private lateinit var itemPhotoManager: ItemPhotoManager
    private lateinit var repository: ExportBundleRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        itemPhotoManager = mockk(relaxed = true)
        repository = ExportBundleRepository(context, itemPhotoManager)
    }

    @Test
    fun `buildBundles with export fields uses exportTitle and exportDescription`() {
        // Given: item with AI-generated export fields
        val item =
            createTestItem(
                id = "test-1",
                exportTitle = "Nike Air Max 90",
                exportDescription = "Classic sneakers in excellent condition",
                exportBullets = listOf("Size 10", "White colorway", "Original box"),
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: uses export fields and is marked READY
        assertEquals(1, result.bundles.size)
        val bundle = result.bundles[0]
        assertEquals("Nike Air Max 90", bundle.title)
        assertEquals("Classic sneakers in excellent condition", bundle.description)
        assertEquals(listOf("Size 10", "White colorway", "Original box"), bundle.bullets)
        assertTrue(ExportBundleFlag.READY in bundle.flags)
        assertFalse(bundle.needsAi)
        assertTrue(bundle.isReady)
    }

    @Test
    fun `buildBundles with user-edited summaryText uses fallback and marks USER_EDITED`() {
        // Given: item with user-edited summary text (no export fields)
        val item =
            createTestItem(
                id = "test-2",
                exportTitle = null,
                exportDescription = null,
                attributesSummaryText = "My custom description for this item",
                summaryTextUserEdited = true,
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: uses summary text and is marked USER_EDITED
        val bundle = result.bundles[0]
        assertEquals(item.displayLabel, bundle.title)
        assertEquals("My custom description for this item", bundle.description)
        assertTrue(ExportBundleFlag.USER_EDITED in bundle.flags)
        assertTrue(bundle.isReady) // USER_EDITED counts as ready
        assertFalse(bundle.needsAi)
    }

    @Test
    fun `buildBundles with enrichment summaryText uses fallback and marks NEEDS_AI`() {
        // Given: item with enrichment-generated summary text (not user-edited)
        val item =
            createTestItem(
                id = "test-3",
                exportTitle = null,
                exportDescription = null,
                attributesSummaryText = "Brand: Nike\nColor: Blue",
                summaryTextUserEdited = false,
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: uses summary text but is marked NEEDS_AI
        val bundle = result.bundles[0]
        assertEquals("Brand: Nike\nColor: Blue", bundle.description)
        assertTrue(ExportBundleFlag.NEEDS_AI in bundle.flags)
        assertTrue(bundle.needsAi)
        assertFalse(bundle.isReady)
    }

    @Test
    fun `buildBundles with no text sources generates minimal text`() {
        // Given: item with no export fields and no summary text
        val item =
            createTestItem(
                id = "test-4",
                exportTitle = null,
                exportDescription = null,
                attributesSummaryText = "",
                attributes =
                    mapOf(
                        "brand" to ItemAttribute(value = "Adidas"),
                        "color" to ItemAttribute(value = "Black"),
                    ),
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: generates minimal text and marks NEEDS_AI
        val bundle = result.bundles[0]
        assertTrue(bundle.description.contains("Category:"))
        assertTrue(bundle.description.contains("Brand: Adidas"))
        assertTrue(bundle.description.contains("Color: Black"))
        assertTrue(ExportBundleFlag.NEEDS_AI in bundle.flags)
    }

    @Test
    fun `buildBundles marks items without photos as NO_PHOTOS`() {
        // Given: item without photos
        val item =
            createTestItem(
                id = "test-5",
                fullImagePath = null,
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: is marked NO_PHOTOS
        val bundle = result.bundles[0]
        assertTrue(ExportBundleFlag.NO_PHOTOS in bundle.flags)
        assertTrue(bundle.hasNoPhotos)
        assertEquals(1, result.noPhotosCount)
    }

    @Test
    fun `buildBundles counts statistics correctly`() {
        // Given: mix of items with different states
        // Note: All items will have NO_PHOTOS because the fake paths don't exist
        val items =
            listOf(
                createTestItem(id = "ready-1", exportTitle = "Title 1", exportDescription = "Desc 1"),
                createTestItem(id = "ready-2", exportTitle = "Title 2", exportDescription = "Desc 2"),
                createTestItem(id = "needs-ai-1", exportTitle = null, exportDescription = null),
                createTestItem(
                    id = "no-photos-1",
                    exportTitle = "Title 3",
                    exportDescription = "Desc 3",
                    fullImagePath = null,
                ),
            )

        // When: building bundles
        val result = repository.buildBundles(items)

        // Then: statistics are correct
        assertEquals(4, result.totalItems)
        assertEquals(3, result.readyCount) // ready-1, ready-2, no-photos-1
        assertEquals(1, result.needsAiCount) // needs-ai-1
        // All items have NO_PHOTOS because fake paths don't exist
        assertEquals(4, result.noPhotosCount)
        assertFalse(result.allReady)
        assertTrue(result.hasItemsNeedingAi)
    }

    @Test
    fun `buildBundles throws ExportLimitExceededException when item limit exceeded`() {
        // Given: 60 items with a limit of 50
        val items = (1..60).map { createTestItem(id = "item-$it") }
        val limits = ExportLimits(maxItems = 50)

        // When/Then: throws exception
        try {
            repository.buildBundles(items, limits = limits)
            fail("Expected ExportLimitExceededException")
        } catch (e: ExportLimitExceededException) {
            assertTrue(e.message!!.contains("Cannot export more than 50 items"))
        }
    }

    @Test
    fun `buildBundles filters by selectedIds when provided`() {
        // Given: 5 items with only 2 selected
        val items = (1..5).map { createTestItem(id = "item-$it") }
        val selectedIds = setOf("item-2", "item-4")

        // When: building bundles with selection
        val result = repository.buildBundles(items, itemIds = selectedIds)

        // Then: only selected items are included
        assertEquals(2, result.totalItems)
        assertEquals(setOf("item-2", "item-4"), result.bundles.map { it.itemId }.toSet())
    }

    @Test
    fun `buildBundles preserves attribute metadata`() {
        // Given: item with attributes
        val attrs =
            mapOf(
                "brand" to ItemAttribute(value = "Nike", confidence = 0.95f, source = "vision"),
                "color" to ItemAttribute(value = "Blue", confidence = 0.8f, source = "user"),
            )
        val item = createTestItem(id = "test-attrs", attributes = attrs)

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: attributes are preserved
        val bundle = result.bundles[0]
        assertEquals(2, bundle.attributes.size)
        assertEquals("Nike", bundle.attributes["brand"]?.value)
        assertEquals(0.95f, bundle.attributes["brand"]?.confidence)
        assertEquals("vision", bundle.attributes["brand"]?.source)
    }

    @Test
    fun `buildBundles with multiple photos includes all photos in photoUris`() {
        // Given: item with primary photo and additional photos
        // Create temporary test files
        val testDir = File(context.filesDir, "test_photos")
        testDir.mkdirs()

        val primaryPhoto =
            File(testDir, "primary.jpg").apply {
                writeText("primary photo content")
            }
        val additionalPhoto1 =
            File(testDir, "additional1.jpg").apply {
                writeText("additional photo 1 content")
            }
        val additionalPhoto2 =
            File(testDir, "additional2.jpg").apply {
                writeText("additional photo 2 content")
            }

        val item =
            createTestItem(
                id = "test-multi-photo",
                fullImagePath = primaryPhoto.absolutePath,
                additionalPhotos =
                    listOf(
                        com.scanium.shared.core.models.items.ItemPhoto(
                            id = "photo-1",
                            uri = additionalPhoto1.absolutePath,
                        ),
                        com.scanium.shared.core.models.items.ItemPhoto(
                            id = "photo-2",
                            uri = additionalPhoto2.absolutePath,
                        ),
                    ),
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: all photos are included in photoUris in correct order
        val bundle = result.bundles[0]
        assertEquals(3, bundle.photoUris.size)
        // Primary photo should be first
        assertEquals(primaryPhoto.absolutePath, bundle.photoUris[0])
        // Additional photos should follow
        assertTrue(bundle.photoUris.contains(additionalPhoto1.absolutePath))
        assertTrue(bundle.photoUris.contains(additionalPhoto2.absolutePath))
        // No duplicates
        assertEquals(3, bundle.photoUris.toSet().size)
        assertEquals(3, bundle.photoCount)

        // Cleanup
        testDir.deleteRecursively()
    }

    @Test
    fun `buildBundles deduplicates primary photo from photoUris`() {
        // Given: item where primary photo is also in additionalPhotos (potential duplicate)
        val testDir = File(context.filesDir, "test_photos_dedup")
        testDir.mkdirs()

        val primaryPhoto =
            File(testDir, "primary.jpg").apply {
                writeText("primary photo content")
            }
        val additionalPhoto =
            File(testDir, "additional.jpg").apply {
                writeText("additional photo content")
            }

        val item =
            createTestItem(
                id = "test-dedup",
                fullImagePath = primaryPhoto.absolutePath,
                additionalPhotos =
                    listOf(
                        // Intentionally include the primary photo again
                        com.scanium.shared.core.models.items.ItemPhoto(
                            id = "photo-primary-dup",
                            uri = primaryPhoto.absolutePath,
                        ),
                        com.scanium.shared.core.models.items.ItemPhoto(
                            id = "photo-additional",
                            uri = additionalPhoto.absolutePath,
                        ),
                    ),
            )

        // When: building bundles
        val result = repository.buildBundles(listOf(item))

        // Then: primary photo appears only once (deduplicated)
        val bundle = result.bundles[0]
        assertEquals(2, bundle.photoUris.size)
        assertEquals(primaryPhoto.absolutePath, bundle.photoUris[0])
        assertEquals(additionalPhoto.absolutePath, bundle.photoUris[1])
        // Verify no duplicates
        assertEquals(2, bundle.photoUris.toSet().size)
        assertEquals(2, bundle.photoCount)

        // Cleanup
        testDir.deleteRecursively()
    }

    private fun createTestItem(
        id: String,
        exportTitle: String? = null,
        exportDescription: String? = null,
        exportBullets: List<String> = emptyList(),
        attributesSummaryText: String = "",
        summaryTextUserEdited: Boolean = false,
        attributes: Map<String, ItemAttribute> = emptyMap(),
        fullImagePath: String? = "/fake/path/to/image.jpg",
        additionalPhotos: List<com.scanium.shared.core.models.items.ItemPhoto> = emptyList(),
    ): ScannedItem {
        return ScannedItem(
            id = id,
            category = ItemCategory.FASHION,
            // Required field
            priceRange = 0.0 to 0.0,
            timestamp = System.currentTimeMillis(),
            exportTitle = exportTitle,
            exportDescription = exportDescription,
            exportBullets = exportBullets,
            attributesSummaryText = attributesSummaryText,
            summaryTextUserEdited = summaryTextUserEdited,
            attributes = attributes,
            fullImagePath = fullImagePath,
            additionalPhotos = additionalPhotos,
        )
    }
}
