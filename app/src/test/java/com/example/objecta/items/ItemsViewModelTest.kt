package com.example.objecta.items

import com.example.objecta.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ItemsViewModel.
 *
 * Tests adding, removing, and managing items including document items with recognized text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsViewModelTest {

    private lateinit var viewModel: ItemsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ItemsViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial items list is empty`() = runTest {
        val items = viewModel.items.first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun `addItem adds a single item`() = runTest {
        val item = createTestItem(id = "item1", category = ItemCategory.FASHION)

        viewModel.addItem(item)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals("item1", items[0].id)
    }

    @Test
    fun `addItem prevents duplicate items with same id`() = runTest {
        val item1 = createTestItem(id = "duplicate", category = ItemCategory.FASHION)
        val item2 = createTestItem(id = "duplicate", category = ItemCategory.HOME_GOOD)

        viewModel.addItem(item1)
        viewModel.addItem(item2)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals(ItemCategory.FASHION, items[0].category) // First one should be kept
    }

    @Test
    fun `addItems adds multiple items at once`() = runTest {
        val itemsList = listOf(
            createTestItem(id = "item1", category = ItemCategory.ELECTRONICS),
            createTestItem(id = "item2", category = ItemCategory.FOOD),
            createTestItem(id = "item3", category = ItemCategory.PLANT)
        )

        viewModel.addItems(itemsList)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(3, items.size)
    }

    @Test
    fun `addItems filters out duplicates`() = runTest {
        val item1 = createTestItem(id = "item1", category = ItemCategory.FASHION)
        viewModel.addItem(item1)
        advanceUntilIdle()

        val itemsList = listOf(
            createTestItem(id = "item1", category = ItemCategory.ELECTRONICS), // Duplicate
            createTestItem(id = "item2", category = ItemCategory.FOOD),
            createTestItem(id = "item3", category = ItemCategory.PLANT)
        )

        viewModel.addItems(itemsList)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(3, items.size) // Only 2 new items should be added
    }

    @Test
    fun `removeItem removes item by id`() = runTest {
        val item1 = createTestItem(id = "item1", category = ItemCategory.FASHION)
        val item2 = createTestItem(id = "item2", category = ItemCategory.FOOD)

        viewModel.addItems(listOf(item1, item2))
        advanceUntilIdle()

        viewModel.removeItem("item1")
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals("item2", items[0].id)
    }

    @Test
    fun `clearAllItems removes all items`() = runTest {
        val itemsList = listOf(
            createTestItem(id = "item1", category = ItemCategory.ELECTRONICS),
            createTestItem(id = "item2", category = ItemCategory.FOOD),
            createTestItem(id = "item3", category = ItemCategory.PLANT)
        )

        viewModel.addItems(itemsList)
        advanceUntilIdle()

        viewModel.clearAllItems()
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun `getItemCount returns correct count`() = runTest {
        assertEquals(0, viewModel.getItemCount())

        viewModel.addItem(createTestItem(id = "item1", category = ItemCategory.FASHION))
        advanceUntilIdle()
        assertEquals(1, viewModel.getItemCount())

        viewModel.addItem(createTestItem(id = "item2", category = ItemCategory.FOOD))
        advanceUntilIdle()
        assertEquals(2, viewModel.getItemCount())

        viewModel.removeItem("item1")
        advanceUntilIdle()
        assertEquals(1, viewModel.getItemCount())
    }

    @Test
    fun `add document item with recognized text`() = runTest {
        val documentItem = createDocumentItem(
            id = "doc1",
            recognizedText = "This is a test document with some recognized text."
        )

        viewModel.addItem(documentItem)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals(ItemCategory.DOCUMENT, items[0].category)
        assertEquals("This is a test document with some recognized text.", items[0].recognizedText)
        assertNull(items[0].barcodeValue)
    }

    @Test
    fun `add barcode item with barcode value`() = runTest {
        val barcodeItem = createBarcodeItem(
            id = "barcode1",
            barcodeValue = "123456789012"
        )

        viewModel.addItem(barcodeItem)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals("123456789012", items[0].barcodeValue)
        assertNull(items[0].recognizedText)
    }

    @Test
    fun `add mixed item types to viewModel`() = runTest {
        val itemsList = listOf(
            createTestItem(id = "object1", category = ItemCategory.FASHION),
            createDocumentItem(id = "doc1", recognizedText = "Document text"),
            createBarcodeItem(id = "barcode1", barcodeValue = "12345"),
            createTestItem(id = "object2", category = ItemCategory.ELECTRONICS),
            createDocumentItem(id = "doc2", recognizedText = "Another document")
        )

        viewModel.addItems(itemsList)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(5, items.size)

        // Verify document items
        val docItems = items.filter { it.category == ItemCategory.DOCUMENT }
        assertEquals(2, docItems.size)
        assertTrue(docItems.all { it.recognizedText != null })

        // Verify barcode items
        val barcodeItems = items.filter { it.barcodeValue != null }
        assertEquals(1, barcodeItems.size)
    }

    @Test
    fun `duplicate document items with same text hash are prevented`() = runTest {
        val doc1 = createDocumentItem(
            id = "doc_hash_123",
            recognizedText = "Same text"
        )
        val doc2 = createDocumentItem(
            id = "doc_hash_123", // Same ID
            recognizedText = "Same text"
        )

        viewModel.addItem(doc1)
        viewModel.addItem(doc2)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size) // Only one should be added
    }

    @Test
    fun `document items with different text are added separately`() = runTest {
        val doc1 = createDocumentItem(
            id = "doc1",
            recognizedText = "First document"
        )
        val doc2 = createDocumentItem(
            id = "doc2",
            recognizedText = "Second document"
        )

        viewModel.addItems(listOf(doc1, doc2))
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(2, items.size)
        assertEquals("First document", items[0].recognizedText)
        assertEquals("Second document", items[1].recognizedText)
    }

    @Test
    fun `items maintain insertion order`() = runTest {
        val itemsList = listOf(
            createTestItem(id = "item1", category = ItemCategory.FASHION),
            createDocumentItem(id = "doc1", recognizedText = "Doc"),
            createBarcodeItem(id = "barcode1", barcodeValue = "123"),
            createTestItem(id = "item2", category = ItemCategory.FOOD)
        )

        viewModel.addItems(itemsList)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals("item1", items[0].id)
        assertEquals("doc1", items[1].id)
        assertEquals("barcode1", items[2].id)
        assertEquals("item2", items[3].id)
    }

    @Test
    fun `empty recognizedText is handled correctly`() = runTest {
        val docItem = createDocumentItem(
            id = "doc1",
            recognizedText = ""
        )

        viewModel.addItem(docItem)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals("", items[0].recognizedText)
    }

    @Test
    fun `long recognizedText is handled correctly`() = runTest {
        val longText = "A".repeat(5000)
        val docItem = createDocumentItem(
            id = "doc1",
            recognizedText = longText
        )

        viewModel.addItem(docItem)
        advanceUntilIdle()

        val items = viewModel.items.first()
        assertEquals(1, items.size)
        assertEquals(5000, items[0].recognizedText?.length)
    }

    // Helper functions

    private fun createTestItem(
        id: String,
        category: ItemCategory
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = Pair(10.0, 50.0),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createDocumentItem(
        id: String,
        recognizedText: String
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = ItemCategory.DOCUMENT,
            priceRange = Pair(0.0, 0.0),
            timestamp = System.currentTimeMillis(),
            recognizedText = recognizedText
        )
    }

    private fun createBarcodeItem(
        id: String,
        barcodeValue: String
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = ItemCategory.UNKNOWN,
            priceRange = Pair(5.0, 25.0),
            timestamp = System.currentTimeMillis(),
            barcodeValue = barcodeValue
        )
    }
}
