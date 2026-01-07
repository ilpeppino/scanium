package com.scanium.app.items

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import com.scanium.shared.core.models.items.VisionLabel
import com.scanium.shared.core.models.items.VisionLogo
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VisionEnrichmentGoldenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun whenVisionInsightsApplied_thenItemLabelAndAttributesRender() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val imageBytes = context.assets.open("kleenex-small-box.jpg").use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        assertThat(bitmap).isNotNull()

        val cacheFile = File(context.cacheDir, "kleenex-small-box.jpg").apply { writeBytes(imageBytes) }
        val imageUri = Uri.fromFile(cacheFile)

        val visionAttributes = VisionAttributes(
            colors = listOf(
                VisionColor(name = "Blue", hex = "***REMOVED***1E40AF", score = 0.7f),
                VisionColor(name = "White", hex = "***REMOVED***FFFFFF", score = 0.3f),
            ),
            ocrText = "Kleenex tissue box",
            logos = listOf(VisionLogo(name = "Kleenex", score = 0.92f)),
            labels = listOf(
                VisionLabel(name = "tissue box", score = 0.9f),
                VisionLabel(name = "paper", score = 0.7f),
            ),
            brandCandidates = listOf("Kleenex"),
            modelCandidates = emptyList(),
            itemType = "Tissue Box",
        )

        val manager =
            ItemsStateManager(
                scope = CoroutineScope(testDispatcher),
                itemsStore = NoopScannedItemStore,
                initialWorkerDispatcher = testDispatcher,
                initialMainDispatcher = testDispatcher,
            )

        val baseItem =
            ScannedItem(
                id = "golden-1",
                thumbnail = null,
                category = ItemCategory.HOME_GOOD,
                priceRange = 2.0 to 4.0,
                confidence = 0.8f,
                timestamp = System.currentTimeMillis(),
                boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
                labelText = null,
                fullImageUri = imageUri,
            )

        val aggregatedItems = manager.addItemsSync(listOf(baseItem))
        val aggregatedId = aggregatedItems.first().aggregatedId

        manager.applyVisionInsights(
            aggregatedId = aggregatedId,
            visionAttributes = visionAttributes,
            suggestedLabel = "Kleenex Tissue Box",
            categoryHint = null,
        )

        val enrichedItem = manager.getItem(aggregatedId)
        assertThat(enrichedItem).isNotNull()

        requireNotNull(enrichedItem)

        assertThat(enrichedItem.attributes["brand"]?.value).isEqualTo("Kleenex")
        assertThat(enrichedItem.attributes["color"]?.value).isEqualTo("Blue")
        assertThat(enrichedItem.attributes["itemType"]?.value).isEqualTo("Tissue Box")

        assertThat(enrichedItem.displayLabel).contains("Kleenex")
        assertThat(enrichedItem.displayLabel).contains("Tissue Box")
        assertThat(enrichedItem.displayLabel).contains("Blue")

        composeTestRule.setContent {
            ItemDetailSheet(
                item = enrichedItem,
                onDismiss = {},
                onAttributeEdit = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Brand").assertExists()
        composeTestRule.onNodeWithText("Kleenex").assertExists()
        composeTestRule.onNodeWithText("Color").assertExists()
        composeTestRule.onNodeWithText("Blue").assertExists()
        composeTestRule.onNodeWithText("Item Type").assertExists()
        composeTestRule.onNodeWithText("Tissue Box").assertExists()
    }
}
