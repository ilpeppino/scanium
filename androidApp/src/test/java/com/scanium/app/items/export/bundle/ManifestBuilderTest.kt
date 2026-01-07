package com.scanium.app.items.export.bundle

import com.scanium.shared.core.models.ml.ItemCategory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ManifestBuilder.
 *
 * Tests:
 * - Manifest structure and required fields
 * - Statistics calculation
 * - Item status determination
 * - Path references for ZIP structure
 */
@RunWith(RobolectricTestRunner::class)
class ManifestBuilderTest {

    @Test
    fun `build includes manifest version and format`() {
        val result = createTestResult(1)

        val manifest = JSONObject(ManifestBuilder.build(result))

        assertEquals("1.0", manifest.getString("manifestVersion"))
        assertEquals("zip", manifest.getString("exportFormat"))
        assertTrue(manifest.has("exportedAt"))
    }

    @Test
    fun `build includes app metadata`() {
        val result = createTestResult(1)

        val manifest = JSONObject(ManifestBuilder.build(result))

        val app = manifest.getJSONObject("app")
        assertEquals("Scanium", app.getString("name"))
        assertEquals("Android", app.getString("platform"))
        assertTrue(app.has("sdkVersion"))
    }

    @Test
    fun `build includes accurate statistics`() {
        val bundles = listOf(
            createTestBundle("item-1", setOf(ExportBundleFlag.READY)),
            createTestBundle("item-2", setOf(ExportBundleFlag.READY)),
            createTestBundle("item-3", setOf(ExportBundleFlag.NEEDS_AI)),
            createTestBundle("item-4", setOf(ExportBundleFlag.READY, ExportBundleFlag.NO_PHOTOS)),
        )
        val result = ExportBundleResult(
            bundles = bundles,
            totalItems = 4,
            readyCount = 3,
            needsAiCount = 1,
            noPhotosCount = 1,
        )

        val manifest = JSONObject(ManifestBuilder.build(result))

        val stats = manifest.getJSONObject("statistics")
        assertEquals(4, stats.getInt("totalItems"))
        assertEquals(3, stats.getInt("readyCount"))
        assertEquals(1, stats.getInt("needsAiCount"))
        assertEquals(1, stats.getInt("noPhotosCount"))
        assertFalse(stats.getBoolean("allReady"))
    }

    @Test
    fun `build marks allReady true when no items need AI`() {
        val bundles = listOf(
            createTestBundle("item-1", setOf(ExportBundleFlag.READY)),
            createTestBundle("item-2", setOf(ExportBundleFlag.READY)),
        )
        val result = ExportBundleResult(
            bundles = bundles,
            totalItems = 2,
            readyCount = 2,
            needsAiCount = 0,
            noPhotosCount = 0,
        )

        val manifest = JSONObject(ManifestBuilder.build(result))

        val stats = manifest.getJSONObject("statistics")
        assertTrue(stats.getBoolean("allReady"))
    }

    @Test
    fun `build includes items summary with paths`() {
        val bundle = createTestBundle(
            "test-item-123",
            setOf(ExportBundleFlag.READY),
            photoCount = 3,
        )
        val result = ExportBundleResult(
            bundles = listOf(bundle),
            totalItems = 1,
            readyCount = 1,
            needsAiCount = 0,
            noPhotosCount = 0,
        )

        val manifest = JSONObject(ManifestBuilder.build(result))

        val items = manifest.getJSONArray("items")
        assertEquals(1, items.length())

        val item = items.getJSONObject(0)
        assertEquals("test-item-123", item.getString("id"))
        assertEquals("Test Title", item.getString("title"))
        assertEquals("FASHION", item.getString("category"))
        assertEquals(3, item.getInt("photoCount"))
        assertEquals("READY", item.getString("status"))

        val paths = item.getJSONObject("paths")
        assertEquals("items/test-item-123", paths.getString("folder"))
        assertEquals("items/test-item-123/listing.txt", paths.getString("listingTxt"))
        assertEquals("items/test-item-123/listing.json", paths.getString("listingJson"))
        assertEquals("items/test-item-123/photos/", paths.getString("photos"))
    }

    @Test
    fun `build determines correct status for each item state`() {
        val bundles = listOf(
            createTestBundle("ready", setOf(ExportBundleFlag.READY)),
            createTestBundle("ready-no-photos", setOf(ExportBundleFlag.READY, ExportBundleFlag.NO_PHOTOS)),
            createTestBundle("needs-ai", setOf(ExportBundleFlag.NEEDS_AI)),
            createTestBundle("needs-ai-no-photos", setOf(ExportBundleFlag.NEEDS_AI, ExportBundleFlag.NO_PHOTOS)),
        )
        val result = ExportBundleResult(
            bundles = bundles,
            totalItems = 4,
            readyCount = 2,
            needsAiCount = 2,
            noPhotosCount = 2,
        )

        val manifest = JSONObject(ManifestBuilder.build(result))

        val items = manifest.getJSONArray("items")
        assertEquals("READY", items.getJSONObject(0).getString("status"))
        assertEquals("READY_NO_PHOTOS", items.getJSONObject(1).getString("status"))
        assertEquals("NEEDS_AI", items.getJSONObject(2).getString("status"))
        assertEquals("NEEDS_AI_NO_PHOTOS", items.getJSONObject(3).getString("status"))
    }

    @Test
    fun `build includes flags array for each item`() {
        val bundle = createTestBundle(
            "test-item",
            setOf(ExportBundleFlag.NEEDS_AI, ExportBundleFlag.NO_PHOTOS),
        )
        val result = createTestResult(bundles = listOf(bundle))

        val manifest = JSONObject(ManifestBuilder.build(result))

        val items = manifest.getJSONArray("items")
        val flags = items.getJSONObject(0).getJSONArray("flags")

        val flagList = (0 until flags.length()).map { flags.getString(it) }
        assertTrue(flagList.contains("NEEDS_AI"))
        assertTrue(flagList.contains("NO_PHOTOS"))
    }

    @Test
    fun `buildMinimal produces compact manifest`() {
        val bundles = listOf(
            createTestBundle("item-1"),
            createTestBundle("item-2"),
            createTestBundle("item-3"),
        )
        val result = ExportBundleResult(
            bundles = bundles,
            totalItems = 3,
            readyCount = 3,
            needsAiCount = 0,
            noPhotosCount = 0,
        )

        val manifest = JSONObject(ManifestBuilder.buildMinimal(result))

        assertEquals("1.0", manifest.getString("manifestVersion"))
        assertEquals("text", manifest.getString("exportFormat"))
        assertEquals(3, manifest.getInt("totalItems"))

        val itemIds = manifest.getJSONArray("itemIds")
        assertEquals(3, itemIds.length())
        assertEquals("item-1", itemIds.getString(0))
        assertEquals("item-2", itemIds.getString(1))
        assertEquals("item-3", itemIds.getString(2))
    }

    @Test
    fun `build does not include photos path when item has no photos`() {
        val bundle = createTestBundle(
            "no-photos-item",
            setOf(ExportBundleFlag.READY, ExportBundleFlag.NO_PHOTOS),
            photoCount = 0,
        )
        val result = createTestResult(bundles = listOf(bundle))

        val manifest = JSONObject(ManifestBuilder.build(result))

        val items = manifest.getJSONArray("items")
        val paths = items.getJSONObject(0).getJSONObject("paths")

        assertFalse(paths.has("photos"))
    }

    private fun createTestResult(
        itemCount: Int = 1,
        bundles: List<ExportItemBundle>? = null,
    ): ExportBundleResult {
        val actualBundles = bundles ?: (1..itemCount).map {
            createTestBundle("item-$it", setOf(ExportBundleFlag.READY))
        }
        return ExportBundleResult(
            bundles = actualBundles,
            totalItems = actualBundles.size,
            readyCount = actualBundles.count { it.isReady },
            needsAiCount = actualBundles.count { it.needsAi },
            noPhotosCount = actualBundles.count { it.hasNoPhotos },
        )
    }

    private fun createTestBundle(
        itemId: String,
        flags: Set<ExportBundleFlag> = setOf(ExportBundleFlag.READY),
        photoCount: Int = 3,
    ): ExportItemBundle {
        val photoUris = if (photoCount > 0) {
            (1..photoCount).map { "/path/photo$it.jpg" }
        } else {
            emptyList()
        }

        return ExportItemBundle(
            itemId = itemId,
            title = "Test Title",
            description = "Test Description",
            bullets = emptyList(),
            category = ItemCategory.FASHION,
            attributes = emptyMap(),
            photoUris = photoUris,
            primaryPhotoUri = null,
            createdAt = System.currentTimeMillis(),
            flags = flags,
            confidenceTier = null,
            exportModel = null,
        )
    }
}
