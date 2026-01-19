package com.scanium.app.items

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for ItemsViewModel with the new ItemAggregator integration.
 *
 * Verifies:
 * - ItemsViewModel correctly uses ItemAggregator
 * - Similar items are merged at the ViewModel level
 * - Distinct items remain separate
 * - UI state updates correctly
 * - Removal and clearing work correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ItemsViewModelAggregationTest {
    private lateinit var viewModel: ItemsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        viewModel =
            createTestItemsViewModel(
                workerDispatcher = dispatcher,
                mainDispatcher = dispatcher,
            )
    }

    @Test
    fun `test addItem merges similar detections`() =
        runBlocking {
            val item1 =
                createTestItem(
                    id = "det_1",
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    boundingBox = normalizedRect(0.3f, 0.3f, 0.5f, 0.5f),
                )

            val item2 =
                createTestItem(
                    id = "det_2",
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    boundingBox = normalizedRect(0.31f, 0.31f, 0.51f, 0.51f),
// Slightly shifted
                )

            viewModel.addItem(item1)
            viewModel.addItem(item2)

            // Should have merged into a single aggregated item
            val items = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 1 aggregated item", 1, items.size)

            // IDs are preserved on aggregation; ensure we didn't duplicate
            assertTrue("Aggregated ID should not be empty", items.first().id.isNotBlank())
        }

    @Test
    fun `test addItem keeps distinct items separate`() =
        runBlocking {
            val item1 =
                createTestItem(
                    id = "det_1",
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    boundingBox = normalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                )

            val item2 =
                createTestItem(
                    id = "det_2",
                    category = ItemCategory.ELECTRONICS,
                    labelText = "Phone",
                    boundingBox = normalizedRect(0.7f, 0.7f, 0.9f, 0.9f),
                )

            viewModel.addItem(item1)
            viewModel.addItem(item2)

            // Should have 2 items in UI state
            val items = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 2 distinct items", 2, items.size)
        }

    @Test
    fun `test addItems batch processing`() =
        runBlocking {
            val items =
                listOf(
                    createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f)),
                    createTestItem("det_2", ItemCategory.FASHION, "Shirt", normalizedRect(0.12f, 0.12f, 0.32f, 0.32f)),
                    createTestItem("det_3", ItemCategory.ELECTRONICS, "Phone", normalizedRect(0.6f, 0.6f, 0.8f, 0.8f)),
                    createTestItem("det_4", ItemCategory.ELECTRONICS, "Phone", normalizedRect(0.61f, 0.61f, 0.81f, 0.81f)),
                )

            viewModel.addItems(items)

            // Should have 2 aggregated items (1 shirt, 1 phone)
            val resultItems = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 2 aggregated items", 2, resultItems.size)
        }

    @Test
    fun `test clearAllItems`() =
        runBlocking {
            val items =
                listOf(
                    createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f)),
                    createTestItem("det_2", ItemCategory.ELECTRONICS, "Phone", normalizedRect(0.6f, 0.6f, 0.8f, 0.8f)),
                )

            viewModel.addItems(items)

            // Should have 2 items
            assertEquals("Should have 2 items", 2, viewModel.awaitItems(dispatcher).size)

            viewModel.clearAllItems()

            // Should have 0 items
            assertEquals("Should have 0 items after clear", 0, viewModel.awaitItems(dispatcher).size)
            assertEquals("Item count should be 0", 0, viewModel.getItemCount())
        }

    @Test
    fun `test removeItem`() =
        runBlocking {
            val item1 = createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f))

            viewModel.addItem(item1)

            // Should have 1 item
            val itemsBefore = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 1 item", 1, itemsBefore.size)

            val aggregatedId = itemsBefore.first().id

            viewModel.removeItem(aggregatedId)

            // Should have 0 items
            assertEquals("Should have 0 items after removal", 0, viewModel.awaitItems(dispatcher).size)
        }

    @Test
    fun `test getItemCount`() =
        runBlocking {
            assertEquals("Initial count should be 0", 0, viewModel.getItemCount())

            val items =
                listOf(
                    createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f)),
                    createTestItem("det_2", ItemCategory.ELECTRONICS, "Phone", normalizedRect(0.6f, 0.6f, 0.8f, 0.8f)),
                )

            viewModel.addItems(items)
            viewModel.awaitItems(dispatcher)

            assertEquals("Count should be 2", 2, viewModel.getItemCount())
        }

    @Test
    fun `test aggregation stats`() =
        runBlocking {
            val items =
                listOf(
                    createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f)),
                    createTestItem("det_2", ItemCategory.FASHION, "Shirt", normalizedRect(0.12f, 0.12f, 0.32f, 0.32f)),
                    createTestItem("det_3", ItemCategory.ELECTRONICS, "Phone", normalizedRect(0.6f, 0.6f, 0.8f, 0.8f)),
                )

            viewModel.addItems(items)
            viewModel.awaitItems(dispatcher)

            val stats = viewModel.getAggregationStats()

            assertEquals("Should have 2 total items", 2, stats.totalItems)
            assertEquals("Should have 1 merge", 1, stats.totalMerges)
        }

    @Test
    fun `test removeStaleItems`() =
        runBlocking {
            val item = createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.1f, 0.1f, 0.3f, 0.3f))

            viewModel.addItem(item)

            // Should have 1 item
            assertEquals("Should have 1 item", 1, viewModel.awaitItems(dispatcher).size)

            // Wait briefly
            Thread.sleep(100)

            // Remove stale items (with 50ms threshold)
            viewModel.removeStaleItems(maxAgeMs = 50)

            // Should have 0 items
            assertEquals("Should have 0 items after stale removal", 0, viewModel.awaitItems(dispatcher).size)
        }

    @Test
    fun `test UI state updates after aggregation`() =
        runBlocking {
            val item1 = createTestItem("det_1", ItemCategory.FASHION, "Shirt", normalizedRect(0.3f, 0.3f, 0.5f, 0.5f))

            viewModel.addItem(item1)

            // Check initial state
            var items = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 1 item", 1, items.size)
            assertEquals("Category should be FASHION", ItemCategory.FASHION, items.first().category)

            // Add similar item
            val item2 = createTestItem("det_2", ItemCategory.FASHION, "Shirt", normalizedRect(0.31f, 0.31f, 0.51f, 0.51f))
            viewModel.addItem(item2)

            // Should still have 1 item (merged)
            items = viewModel.awaitItems(dispatcher)
            assertEquals("Should still have 1 item after merge", 1, items.size)
        }

    @Test
    fun `test multiple sequential additions`() =
        runBlocking {
            // Simulate scanning session with multiple detections
            for (i in 0 until 5) {
                val item =
                    createTestItem(
                        id = "det_$i",
                        category = ItemCategory.FASHION,
                        labelText = "Shirt",
                        boundingBox = normalizedRect(0.3f + i * 0.01f, 0.3f, 0.5f + i * 0.01f, 0.5f),
// Slight movement
                    )
                viewModel.addItem(item)
            }

            // Should all merge into 1 item
            val items = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 1 aggregated item", 1, items.size)
        }

    @Test
    fun `test empty batch addition`() =
        runBlocking {
            viewModel.addItems(emptyList())

            // Should have 0 items
            assertEquals("Should have 0 items", 0, viewModel.awaitItems(dispatcher).size)
        }

    @Test
    fun `test aggregated items maintain correct confidence`() =
        runBlocking {
            val item1 =
                createTestItem(
                    id = "det_1",
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    boundingBox = normalizedRect(0.3f, 0.3f, 0.5f, 0.5f),
                    confidence = 0.7f,
                )

            val item2 =
                createTestItem(
                    id = "det_2",
                    category = ItemCategory.FASHION,
                    labelText = "Shirt",
                    boundingBox = normalizedRect(0.31f, 0.31f, 0.51f, 0.51f),
                    confidence = 0.9f,
// Higher confidence
                )

            viewModel.addItem(item1)
            viewModel.addItem(item2)

            val items = viewModel.awaitItems(dispatcher)
            assertEquals("Should have 1 item", 1, items.size)

            // Should use max confidence
            assertEquals("Should have max confidence", 0.9f, items.first().confidence, 0.001f)
        }

    @Test
    fun `test aggregated items maintain correct bounding box`() =
        runBlocking {
            val box1 = normalizedRect(0.3f, 0.3f, 0.5f, 0.5f)
            val box2 = normalizedRect(0.31f, 0.31f, 0.51f, 0.51f)

            val item1 = createTestItem("det_1", ItemCategory.FASHION, "Shirt", box1)
            val item2 = createTestItem("det_2", ItemCategory.FASHION, "Shirt", box2)

            viewModel.addItem(item1)
            viewModel.addItem(item2)

            val items = viewModel.awaitItems(dispatcher)

            // Should use latest bounding box
            val resultBox = items.first().boundingBox
            assertNotNull("Bounding box should exist", resultBox)
            assertEquals("Should use latest box", box2, resultBox)
        }

    // Helper function to create test items
    private fun createTestItem(
        id: String,
        category: ItemCategory,
        labelText: String,
        boundingBox: NormalizedRect,
        confidence: Float = 0.8f,
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = Pair(10.0, 50.0),
            confidence = confidence,
            boundingBox = boundingBox,
            labelText = labelText,
        )
    }

    private fun normalizedRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): NormalizedRect = NormalizedRect(left, top, right, bottom).clampToUnit()
}
