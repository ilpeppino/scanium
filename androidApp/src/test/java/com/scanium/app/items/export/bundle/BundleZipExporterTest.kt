package com.scanium.app.items.export.bundle

import android.content.Context
import android.graphics.Bitmap
import com.scanium.shared.core.models.ml.ItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests for BundleZipExporter.
 *
 * Tests:
 * - ZIP file structure
 * - File content validity
 * - Progress callback invocation
 * - Multiple items without OOM
 */
@RunWith(RobolectricTestRunner::class)
class BundleZipExporterTest {
    private lateinit var context: Context
    private lateinit var exporter: BundleZipExporter
    private lateinit var testPhotosDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        exporter = BundleZipExporter(context)

        // Create temp directory for test photos
        testPhotosDir =
            File(context.cacheDir, "test_photos").apply {
                mkdirs()
            }
    }

    @After
    fun tearDown() {
        // Clean up test photos
        testPhotosDir.deleteRecursively()

        // Clean up exports
        File(context.cacheDir, "bundle_exports").deleteRecursively()
    }

    @Test
    fun `createZip produces valid ZIP file with correct structure`() =
        runBlocking {
            // Given: test bundle with photos
            val photoFile = createTestPhoto("test_photo.jpg")
            val bundle =
                createTestBundle(
                    itemId = "item-123",
                    photoUris = listOf(photoFile.absolutePath),
                )
            val result =
                ExportBundleResult(
                    bundles = listOf(bundle),
                    totalItems = 1,
                    readyCount = 1,
                    needsAiCount = 0,
                    noPhotosCount = 0,
                )

            // When: creating ZIP
            val exportResult = exporter.createZip(result)

            // Then: ZIP is valid and has correct structure
            assertTrue(exportResult.isSuccess)
            val zipResult = exportResult.getOrThrow()
            assertTrue(zipResult.zipFile.exists())
            assertTrue(zipResult.zipFile.name.startsWith("scanium-export-"))
            assertTrue(zipResult.zipFile.name.endsWith(".zip"))

            // Verify ZIP contents
            ZipFile(zipResult.zipFile).use { zip ->
                val entries = zip.entries().toList().map { it.name }

                // Should have manifest
                assertTrue("export-manifest.json" in entries)

                // Should have item folder structure
                assertTrue(entries.any { it.startsWith("items/item-123/") })
                assertTrue(entries.any { it.contains("listing.txt") })
                assertTrue(entries.any { it.contains("listing.json") })
                assertTrue(entries.any { it.contains("photos/001.jpg") })
            }
        }

    @Test
    fun `createZip includes manifest with correct content`() =
        runBlocking {
            // Given: test result
            val bundle = createTestBundle(itemId = "test-item")
            val result =
                ExportBundleResult(
                    bundles = listOf(bundle),
                    totalItems = 1,
                    readyCount = 1,
                    needsAiCount = 0,
                    noPhotosCount = 0,
                )

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)

            // Then: manifest contains expected data
            ZipFile(exportResult.getOrThrow().zipFile).use { zip ->
                val manifestEntry = zip.getEntry("export-manifest.json")
                assertNotNull(manifestEntry)

                val manifestContent = zip.getInputStream(manifestEntry).bufferedReader().readText()
                assertTrue(manifestContent.contains("\"manifestVersion\""))
                assertTrue(manifestContent.contains("\"test-item\""))
                assertTrue(manifestContent.contains("\"totalItems\": 1"))
            }
        }

    @Test
    fun `createZip listing_txt contains formatted text`() =
        runBlocking {
            // Given: bundle with specific content
            val bundle =
                createTestBundle(
                    itemId = "item-456",
                    title = "Vintage Jacket",
                    description = "Beautiful leather jacket",
                )
            val result = createTestResult(listOf(bundle))

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)

            // Then: listing.txt contains expected text
            ZipFile(exportResult.getOrThrow().zipFile).use { zip ->
                val listingEntry = zip.getEntry("items/item-456/listing.txt")
                assertNotNull(listingEntry)

                val listingContent = zip.getInputStream(listingEntry).bufferedReader().readText()
                assertTrue(listingContent.contains("Vintage Jacket"))
                assertTrue(listingContent.contains("Beautiful leather jacket"))
            }
        }

    @Test
    fun `createZip listing_json contains valid JSON`() =
        runBlocking {
            // Given: bundle with attributes
            val bundle =
                createTestBundle(
                    itemId = "item-789",
                    title = "Nike Shoes",
                )
            val result = createTestResult(listOf(bundle))

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)

            // Then: listing.json is valid JSON
            ZipFile(exportResult.getOrThrow().zipFile).use { zip ->
                val jsonEntry = zip.getEntry("items/item-789/listing.json")
                assertNotNull(jsonEntry)

                val jsonContent = zip.getInputStream(jsonEntry).bufferedReader().readText()
                // Should be parseable as JSON
                org.json.JSONObject(jsonContent)
                assertTrue(jsonContent.contains("\"Nike Shoes\""))
            }
        }

    @Test
    fun `createZip handles multiple items correctly`() =
        runBlocking {
            // Given: multiple bundles
            val bundles =
                listOf(
                    createTestBundle(itemId = "item-1", title = "First Item"),
                    createTestBundle(itemId = "item-2", title = "Second Item"),
                    createTestBundle(itemId = "item-3", title = "Third Item"),
                )
            val result = createTestResult(bundles)

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)
            assertEquals(3, exportResult.getOrThrow().itemCount)

            // Then: all items are present
            ZipFile(exportResult.getOrThrow().zipFile).use { zip ->
                val entries = zip.entries().toList().map { it.name }

                assertTrue(entries.any { it.contains("items/item-1/") })
                assertTrue(entries.any { it.contains("items/item-2/") })
                assertTrue(entries.any { it.contains("items/item-3/") })
            }
        }

    @Test
    fun `createZip invokes progress callback`() =
        runBlocking {
            // Given: multiple bundles and progress tracker
            val bundles = (1..5).map { createTestBundle(itemId = "item-$it") }
            val result = createTestResult(bundles)

            var progressCalls = 0
            var stageChanges = 0
            val callback =
                object : BundleZipExporter.ProgressCallback {
                    override fun onProgress(
                        current: Int,
                        total: Int,
                        stage: BundleZipExporter.ExportStage,
                    ) {
                        progressCalls++
                    }

                    override fun onStageChange(stage: BundleZipExporter.ExportStage) {
                        stageChanges++
                    }
                }

            // When: creating ZIP with callback
            val exportResult = exporter.createZip(result, callback)
            assertTrue(exportResult.isSuccess)

            // Then: callbacks were invoked
            assertTrue(progressCalls > 0)
            assertTrue(stageChanges > 0)
        }

    @Test
    fun `createZip handles items without photos`() =
        runBlocking {
            // Given: bundle without photos
            val bundle =
                ExportItemBundle(
                    itemId = "no-photos",
                    title = "No Photos Item",
                    description = "This item has no photos",
                    bullets = emptyList(),
                    category = ItemCategory.FASHION,
                    attributes = emptyMap(),
                    photoUris = emptyList(),
                    primaryPhotoUri = null,
                    createdAt = System.currentTimeMillis(),
                    flags = setOf(ExportBundleFlag.READY, ExportBundleFlag.NO_PHOTOS),
                    confidenceTier = null,
                    exportModel = null,
                )
            val result = createTestResult(listOf(bundle))

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)

            // Then: ZIP is valid and has text files but no photos folder
            ZipFile(exportResult.getOrThrow().zipFile).use { zip ->
                val entries = zip.entries().toList().map { it.name }

                assertTrue(entries.any { it.contains("listing.txt") })
                assertTrue(entries.any { it.contains("listing.json") })
                assertFalse(entries.any { it.contains("photos/001.jpg") })
            }
        }

    @Test
    fun `createZip returns correct file size`() =
        runBlocking {
            // Given: test bundle
            val photoFile = createTestPhoto("photo.jpg")
            val bundle = createTestBundle(photoUris = listOf(photoFile.absolutePath))
            val result = createTestResult(listOf(bundle))

            // When: creating ZIP
            val exportResult = exporter.createZip(result)
            assertTrue(exportResult.isSuccess)

            // Then: reported size matches actual file size
            val zipResult = exportResult.getOrThrow()
            assertEquals(zipResult.zipFile.length(), zipResult.fileSizeBytes)
        }

    @Test
    fun `estimateExportSize returns reasonable estimate`() {
        // Given: bundle with photos
        val bundles =
            (1..10).map {
                createTestBundle(
                    itemId = "item-$it",
                    photoUris = listOf("/fake/photo1.jpg", "/fake/photo2.jpg"),
                )
            }
        val result =
            ExportBundleResult(
                bundles = bundles,
                totalItems = 10,
                readyCount = 10,
                needsAiCount = 0,
                noPhotosCount = 0,
            )

        // When: estimating size
        val estimate = exporter.estimateExportSize(result)

        // Then: estimate is reasonable (at least manifest + text files)
        assertTrue(estimate > 10 * 4096) // At least 4KB per item for text
    }

    private fun createTestPhoto(name: String): File {
        val photoFile = File(testPhotosDir, name)
        // Create a minimal valid JPEG-like file for testing
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        photoFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return photoFile
    }

    private fun createTestBundle(
        itemId: String = "test-item",
        title: String = "Test Title",
        description: String = "Test Description",
        photoUris: List<String> = emptyList(),
    ): ExportItemBundle {
        return ExportItemBundle(
            itemId = itemId,
            title = title,
            description = description,
            bullets = emptyList(),
            category = ItemCategory.FASHION,
            attributes = emptyMap(),
            photoUris = photoUris,
            primaryPhotoUri = null,
            createdAt = System.currentTimeMillis(),
            flags = setOf(ExportBundleFlag.READY),
            confidenceTier = null,
            exportModel = null,
        )
    }

    private fun createTestResult(bundles: List<ExportItemBundle>): ExportBundleResult {
        return ExportBundleResult(
            bundles = bundles,
            totalItems = bundles.size,
            readyCount = bundles.count { it.isReady },
            needsAiCount = bundles.count { it.needsAi },
            noPhotosCount = bundles.count { it.hasNoPhotos },
        )
    }
}
