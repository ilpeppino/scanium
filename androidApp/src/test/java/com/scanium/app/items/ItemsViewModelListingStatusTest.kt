package com.scanium.app.items

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.Bytes
import com.scanium.core.models.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsViewModelListingStatusTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ItemsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel =
            createTestItemsViewModel(
                workerDispatcher = dispatcher,
                mainDispatcher = dispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateListingStatus changes item status`() =
        runTest {
            // Add an item
            val item = createTestItem(id = "item1")
            viewModel.addItem(item)
            viewModel.awaitItems(dispatcher)

            // Update listing status
            viewModel.updateListingStatus(
                itemId = "item1",
                status = ItemListingStatus.LISTED_ACTIVE,
                listingId = "EBAY-123",
                listingUrl = "https://ebay.com/listing/123",
            )

            // Verify status was updated
            val updatedItem = viewModel.getItem("item1")
            assertThat(updatedItem).isNotNull()
            assertThat(updatedItem!!.listingStatus).isEqualTo(ItemListingStatus.LISTED_ACTIVE)
            assertThat(updatedItem.listingId).isEqualTo("EBAY-123")
            assertThat(updatedItem.listingUrl).isEqualTo("https://ebay.com/listing/123")
        }

    @Test
    fun `updateListingStatus only affects target item`() =
        runTest {
            // Add multiple items
            viewModel.addItem(createTestItem(id = "item1"))
            viewModel.addItem(createTestItem(id = "item2"))
            viewModel.awaitItems(dispatcher)

            // Update only one item
            viewModel.updateListingStatus(
                itemId = "item1",
                status = ItemListingStatus.LISTED_ACTIVE,
            )

            // Verify only item1 was updated (item2 may have merged away)
            val item1Status = viewModel.getListingStatus("item1")
            val item2Status = viewModel.getListingStatus("item2")

            assertThat(item1Status).isEqualTo(ItemListingStatus.LISTED_ACTIVE)
            assertThat(item2Status).isNotEqualTo(ItemListingStatus.LISTED_ACTIVE)
        }

    @Test
    fun `getListingStatus returns correct status`() =
        runTest {
            val item = createTestItem(id = "item1")
            viewModel.addItem(item)
            viewModel.awaitItems(dispatcher)

            // Initial status
            assertThat(viewModel.getListingStatus("item1")).isEqualTo(ItemListingStatus.NOT_LISTED)

            // After update
            viewModel.updateListingStatus("item1", ItemListingStatus.LISTING_IN_PROGRESS)
            assertThat(viewModel.getListingStatus("item1")).isEqualTo(ItemListingStatus.LISTING_IN_PROGRESS)
        }

    @Test
    fun `getItem returns null for non-existent item`() =
        runTest {
            val item = viewModel.getItem("non-existent")
            assertThat(item).isNull()
        }

    @Test
    fun `getListingStatus returns null for non-existent item`() =
        runTest {
            val status = viewModel.getListingStatus("non-existent")
            assertThat(status).isNull()
        }

    private fun createTestItem(
        id: String,
        category: ItemCategory = ItemCategory.HOME_GOOD,
        confidence: Float = 0.8f,
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail =
                Bytes(
                    bytes = ByteArray(16) { 1 },
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                ),
            category = category,
            priceRange = Pair(20.0, 50.0),
            confidence = confidence,
            boundingBox = NormalizedRect(0f, 0f, 1f, 1f),
            labelText = "Test item",
        )
    }
}
