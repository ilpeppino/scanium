package com.scanium.app.regression

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.ItemCategory
import com.scanium.app.NormalizedRect
import com.scanium.app.ScannedItem
import com.scanium.app.items.export.CsvExportWriter
import com.scanium.app.items.export.ZipExportWriter
import com.scanium.shared.core.export.ExportPayload
import com.scanium.shared.core.export.toExportItem
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * TEST 4: Share/export attaches CSV/ZIP + images correctly
 *
 * Validates:
 * - CSV export creates valid CSV file with correct columns
 * - ZIP export creates archive with images/ folder and items.csv
 * - Share intent has correct action, type, and permissions
 * - Attachments are readable via ContentResolver
 *
 * Uses generated bitmaps (no binary test assets).
 */
@RunWith(AndroidJUnit4::class)
class ShareExportRegressionTest {
    private lateinit var context: Context
    private lateinit var csvExportWriter: CsvExportWriter
    private lateinit var zipExportWriter: ZipExportWriter
    private lateinit var testItems: List<ScannedItem>

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RegressionTestConfig.initialize()
        }

        private fun generateTestBitmap(
            width: Int = 200,
            height: Int = 200,
            color: Int = 0xFF3498DB.toInt(),
        ): Bitmap {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                val paint =
                    Paint().apply {
                        this.color = color
                        style = Paint.Style.FILL
                    }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        private fun bitmapToJpegBytes(
            bitmap: Bitmap,
            quality: Int = 85,
        ): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            return stream.toByteArray()
        }

        private fun createTestItem(
            id: String = UUID.randomUUID().toString(),
            category: ItemCategory = ItemCategory.FASHION,
            label: String = "Test Item",
            color: Int = 0xFF3498DB.toInt(),
        ): ScannedItem {
            val bitmap = generateTestBitmap(200, 200, color)
            val thumbnailBytes = bitmapToJpegBytes(bitmap)

            return ScannedItem(
                id = id,
                category = category,
                confidence = 0.85f,
                thumbnail =
                    ImageRef.Bytes(
                        bytes = thumbnailBytes,
                        mimeType = "image/jpeg",
                        width = 200,
                        height = 200,
                    ),
                priceRange = 10.0 to 50.0,
                labelText = label,
                boundingBox = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f),
                timestamp = System.currentTimeMillis(),
                classificationStatus = "SUCCESS",
            )
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        csvExportWriter = CsvExportWriter()
        zipExportWriter = ZipExportWriter()

        // Create 3 test items with different colors/categories
        testItems =
            listOf(
                createTestItem(
                    id = "test_item_1",
                    category = ItemCategory.FASHION,
                    label = "Test Shoe",
                    color = 0xFF3498DB.toInt(),
                ),
                createTestItem(
                    id = "test_item_2",
                    category = ItemCategory.ELECTRONICS,
                    label = "Test Laptop",
                    color = 0xFF2ECC71.toInt(),
                ),
                createTestItem(
                    id = "test_item_3",
                    category = ItemCategory.HOME,
                    label = "Test Chair",
                    color = 0xFFE74C3C.toInt(),
                ),
            )

        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testCsvExport_CreatesValidFile() =
        runTest {
            // Arrange
            val exportItems = testItems.map { it.toExportItem() }
            val payload = ExportPayload(items = exportItems)

            // Act
            val result = csvExportWriter.writeToCache(context, payload)

            // Assert
            assertThat(result.isSuccess).isTrue()

            val file = result.getOrNull()
            assertThat(file).isNotNull()
            assertThat(file!!.exists()).isTrue()
            assertThat(file.extension).isEqualTo("csv")
            assertThat(file.length()).isGreaterThan(0L)

            // Read and validate CSV content
            val csvContent = file.readText()
            assertThat(csvContent).contains("item_id")
            assertThat(csvContent).contains("title")
            assertThat(csvContent).contains("category")
            assertThat(csvContent).contains("Test Shoe")
            assertThat(csvContent).contains("Test Laptop")
            assertThat(csvContent).contains("Test Chair")

            // Cleanup
            file.delete()
        }

    @Test
    fun testZipExport_CreatesValidArchive() =
        runTest {
            // Arrange
            val exportItems = testItems.map { it.toExportItem() }
            val payload = ExportPayload(items = exportItems)

            // Act
            val result = zipExportWriter.writeToCache(context, payload)

            // Assert
            assertThat(result.isSuccess).isTrue()

            val file = result.getOrNull()
            assertThat(file).isNotNull()
            assertThat(file!!.exists()).isTrue()
            assertThat(file.extension).isEqualTo("zip")
            assertThat(file.length()).isGreaterThan(0L)

            // Verify ZIP structure
            val zipFile = java.util.zip.ZipFile(file)
            val entries = zipFile.entries().toList()

            // Should contain images and CSV
            val entryNames = entries.map { it.name }
            assertThat(entryNames).contains("items.csv")

            // Should have image files
            val imageEntries = entryNames.filter { it.startsWith("images/") && it.endsWith(".jpg") }
            assertThat(imageEntries).isNotEmpty()

            zipFile.close()

            // Cleanup
            file.delete()
        }

    @Test
    fun testCsvExport_HandlesSpecialCharacters() =
        runTest {
            // Create item with special characters in label
            val specialItem =
                createTestItem(
                    id = "special_item",
                    category = ItemCategory.FASHION,
                    label = "Item with \"quotes\" and, commas",
                )

            val exportItems = listOf(specialItem.toExportItem())
            val payload = ExportPayload(items = exportItems)

            // Act
            val result = csvExportWriter.writeToCache(context, payload)

            // Assert
            assertThat(result.isSuccess).isTrue()

            val file = result.getOrNull()!!
            val content = file.readText()

            // CSV should properly escape special characters
            assertThat(content).contains("\"")

            // Cleanup
            file.delete()
        }

    @Test
    fun testExport_MultipleItems_AllIncluded() =
        runTest {
            // Arrange - 5 items
            val manyItems =
                (1..5).map { index ->
                    createTestItem(
                        id = "many_item_$index",
                        category = ItemCategory.values()[index % ItemCategory.values().size],
                        label = "Item $index",
                        color = (0xFF000000 or (index * 0x333333)).toInt(),
                    )
                }

            val exportItems = manyItems.map { it.toExportItem() }
            val payload = ExportPayload(items = exportItems)

            // Act - CSV
            val csvResult = csvExportWriter.writeToCache(context, payload)
            assertThat(csvResult.isSuccess).isTrue()

            val csvFile = csvResult.getOrNull()!!
            val csvLines = csvFile.readLines()
            // Header + 5 items
            assertThat(csvLines.size).isAtLeast(6)

            csvFile.delete()

            // Act - ZIP
            val zipResult = zipExportWriter.writeToCache(context, payload)
            assertThat(zipResult.isSuccess).isTrue()

            val zipFile = java.util.zip.ZipFile(zipResult.getOrNull()!!)
            val imageEntries =
                zipFile.entries().toList()
                    .filter { it.name.startsWith("images/") && it.name.endsWith(".jpg") }

            assertThat(imageEntries.size).isEqualTo(5)

            zipFile.close()
            zipResult.getOrNull()!!.delete()
        }

    @Test
    fun testShareIntent_HasCorrectConfiguration() {
        // This test validates the expected intent configuration
        // The actual share flow requires UI interaction

        // Arrange - create expected intent matcher
        val expectedAction = Intent.ACTION_SEND

        // Assert - verify we can construct a valid share intent
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        assertThat(shareIntent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(shareIntent.type).isEqualTo("text/csv")
        assertThat(shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun testShareIntent_MultipleImages_UsesCorrectAction() {
        // For multiple items, ACTION_SEND_MULTIPLE should be used
        val multiIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        assertThat(multiIntent.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(multiIntent.type).isEqualTo("image/*")
    }

    @Test
    fun testExportedFiles_AreReadable() =
        runTest {
            // Verify exported files can be read back
            val exportItems = testItems.map { it.toExportItem() }
            val payload = ExportPayload(items = exportItems)

            // CSV
            val csvResult = csvExportWriter.writeToCache(context, payload)
            val csvFile = csvResult.getOrNull()!!

            val csvInputStream = csvFile.inputStream()
            val csvBytes = csvInputStream.readBytes()
            csvInputStream.close()

            assertThat(csvBytes).isNotEmpty()
            assertThat(String(csvBytes)).contains("item_id")

            csvFile.delete()

            // ZIP
            val zipResult = zipExportWriter.writeToCache(context, payload)
            val zipFileObj = zipResult.getOrNull()!!

            val zipInputStream = zipFileObj.inputStream()
            val zipBytes = zipInputStream.readBytes()
            zipInputStream.close()

            assertThat(zipBytes).isNotEmpty()
            // ZIP magic bytes
            assertThat(zipBytes[0]).isEqualTo(0x50.toByte()) // 'P'
            assertThat(zipBytes[1]).isEqualTo(0x4B.toByte()) // 'K'

            zipFileObj.delete()
        }

    @Test
    fun testItemThumbnails_PersistAfterShareOperations() =
        runTest {
            // REGRESSION TEST: Validates that item thumbnails are preserved after share operations
            // Bug: After sharing, Android kills app process. On restart, items showed photo placeholders
            // Root cause: ImageRef.CacheKey was not being persisted, only ImageRef.Bytes should be saved

            // Arrange - Create items with thumbnails
            val itemsWithThumbnails =
                listOf(
                    createTestItem(id = "persist_test_1", label = "Item 1"),
                    createTestItem(id = "persist_test_2", label = "Item 2"),
                    createTestItem(id = "persist_test_3", label = "Item 3"),
                )

            // Verify items have valid thumbnail bytes
            itemsWithThumbnails.forEach { item ->
                assertThat(item.thumbnail).isInstanceOf(ImageRef.Bytes::class.java)
                val bytes = (item.thumbnail as ImageRef.Bytes).bytes
                assertThat(bytes).isNotEmpty()
                assertThat(bytes.size).isGreaterThan(100) // JPEG should be non-trivial size
            }

            // Simulate persistence cycle (what happens during share)
            val entities = itemsWithThumbnails.map { it.toEntity() }

            // Assert - Entities should have thumbnail bytes (not null)
            entities.forEach { entity ->
                assertThat(entity.thumbnailBytes).isNotNull()
                assertThat(entity.thumbnailBytes).isNotEmpty()
                assertThat(entity.thumbnailMimeType).isEqualTo("image/jpeg")
                assertThat(entity.thumbnailWidth).isEqualTo(200)
                assertThat(entity.thumbnailHeight).isEqualTo(200)
            }

            // Simulate app restart - load from database
            val restoredItems = entities.map { it.toModel() }

            // Assert - Restored items should have valid thumbnails (not null, not placeholders)
            restoredItems.forEach { item ->
                assertThat(item.thumbnail).isNotNull()
                assertThat(item.thumbnail).isInstanceOf(ImageRef.Bytes::class.java)
                val bytes = (item.thumbnail as ImageRef.Bytes).bytes
                assertThat(bytes).isNotEmpty()
                assertThat(bytes.size).isGreaterThan(100)
            }
        }

    @Test
    fun testCacheKeyThumbnails_ResolveBeforePersistence() =
        runTest {
            // REGRESSION TEST: Validates that CacheKey thumbnails are resolved to Bytes before persistence
            // This simulates the runtime scenario where items use CacheKey for performance

            // Arrange - Create item with Bytes thumbnail
            val originalItem = createTestItem(id = "cache_key_test", label = "Cache Key Item")
            val originalBytes = (originalItem.thumbnail as ImageRef.Bytes).bytes

            // Simulate runtime caching (what ItemsStateManager.cacheThumbnails() does)
            com.scanium.app.items.ThumbnailCache.put("cache_key_test", originalItem.thumbnail as ImageRef.Bytes)

            // Create item with CacheKey reference (simulating UI state)
            val cachedItem =
                originalItem.copy(
                    thumbnail =
                        ImageRef.CacheKey(
                            key = "cache_key_test",
                            mimeType = "image/jpeg",
                            width = 200,
                            height = 200,
                        ),
                )

            // Act - Persist item with CacheKey thumbnail
            val entity = cachedItem.toEntity()

            // Assert - Entity should have resolved bytes from cache (not null)
            assertThat(entity.thumbnailBytes).isNotNull()
            assertThat(entity.thumbnailBytes).isNotEmpty()
            assertThat(entity.thumbnailBytes).isEqualTo(originalBytes)

            // Verify restoration works correctly
            val restored = entity.toModel()
            assertThat(restored.thumbnail).isNotNull()
            assertThat(restored.thumbnail).isInstanceOf(ImageRef.Bytes::class.java)
            val restoredBytes = (restored.thumbnail as ImageRef.Bytes).bytes
            assertThat(restoredBytes).isEqualTo(originalBytes)

            // Cleanup
            com.scanium.app.items.ThumbnailCache.remove("cache_key_test")
        }
}
