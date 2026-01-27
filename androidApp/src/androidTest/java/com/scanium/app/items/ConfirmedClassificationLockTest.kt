import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.ItemCategory
import com.scanium.app.NormalizedRect
import com.scanium.app.ScannedItem
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmedClassificationLockTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmedItemIgnoresLateBackendClassification() {
        val manager =
            ItemsStateManager(
                scope = CoroutineScope(testDispatcher),
                itemsStore = NoopScannedItemStore,
                initialWorkerDispatcher = testDispatcher,
                initialMainDispatcher = testDispatcher,
            )

        val baseItem =
            ScannedItem(
                id = "confirmed-1",
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0,
                confidence = 0.7f,
                timestamp = System.currentTimeMillis(),
                boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
                labelText = "Jacket",
                classificationStatus = "CONFIRMED",
            )

        val aggregatedId = manager.addItemsSync(listOf(baseItem)).first().aggregatedId

        manager.applyEnhancedClassification(
            aggregatedId = aggregatedId,
            category = ItemCategory.ELECTRONICS,
            label = "Laptop",
            priceRange = null,
            classificationConfidence = 0.95f,
            isFromBackend = true,
        )

        val updatedItem = manager.getItem(aggregatedId)

        assertThat(updatedItem?.category).isEqualTo(ItemCategory.FASHION)
        assertThat(updatedItem?.labelText).isEqualTo("Jacket")
    }
}
