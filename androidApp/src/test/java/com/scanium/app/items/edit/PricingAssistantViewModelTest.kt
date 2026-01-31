package com.scanium.app.items.edit

import com.scanium.app.ScannedItem
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.pricing.PricingV4Repository
import com.scanium.app.pricing.VariantSchema
import com.scanium.app.pricing.VariantSchemaRepository
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemCondition
import com.scanium.shared.core.models.ml.ItemCategory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PricingAssistantViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `builds steps with variants completeness and identifier`() =
        runTest {
            val item =
                ScannedItem(
                    id = "item-1",
                    category = ItemCategory.ELECTRONICS,
                    priceRange = 0.0 to 0.0,
                    barcodeValue = "1234567890123",
                    condition = ItemCondition.GOOD,
                    attributes =
                        mapOf(
                            "brand" to ItemAttribute.fromValue("Apple"),
                            "itemType" to ItemAttribute.fromValue("electronics_laptop"),
                            "model" to ItemAttribute.fromValue("MacBook Pro"),
                        ),
                )

            val itemsViewModel = mockk<ItemsViewModel>(relaxed = true)
            every { itemsViewModel.getItem("item-1") } returns item

            val schemaRepository = mockk<VariantSchemaRepository>()
            coEvery { schemaRepository.fetchSchema("electronics_laptop") } returns
                Result.success(
                    VariantSchema(
                        fields =
                            listOf(
                                com.scanium.app.pricing.VariantField(
                                    key = "storage",
                                    label = "Storage",
                                    type = "select",
                                    options = listOf("256GB"),
                                ),
                            ),
                        completenessOptions = listOf("Charger"),
                    ),
                )

            val pricingRepo = mockk<PricingV4Repository>(relaxed = true)

            val viewModel =
                PricingAssistantViewModel(
                    itemId = "item-1",
                    itemsViewModel = itemsViewModel,
                    pricingV4Repository = pricingRepo,
                    variantSchemaRepository = schemaRepository,
                )

            testDispatcher.scheduler.advanceUntilIdle()

            val steps = viewModel.state.value.steps
            assertEquals(
                listOf(
                    PricingAssistantStep.INTRO,
                    PricingAssistantStep.VARIANTS,
                    PricingAssistantStep.COMPLETENESS,
                    PricingAssistantStep.IDENTIFIER,
                    PricingAssistantStep.CONFIRM,
                ),
                steps,
            )
        }

    @Test
    fun `submitPricingRequest uses variant attributes and updates success state`() =
        runTest {
            val item =
                ScannedItem(
                    id = "item-1",
                    category = ItemCategory.ELECTRONICS,
                    priceRange = 0.0 to 0.0,
                    barcodeValue = "EAN123",
                    condition = ItemCondition.GOOD,
                    attributes =
                        mapOf(
                            "brand" to ItemAttribute.fromValue("Sony"),
                            "itemType" to ItemAttribute.fromValue("electronics_camera"),
                            "model" to ItemAttribute.fromValue("A7"),
                        ),
                )

            val itemsViewModel = mockk<ItemsViewModel>(relaxed = true)
            every { itemsViewModel.getItem("item-1") } returns item

            val schemaRepository = mockk<VariantSchemaRepository>()
            coEvery { schemaRepository.fetchSchema("electronics_camera") } returns
                Result.success(
                    VariantSchema(
                        fields =
                            listOf(
                                com.scanium.app.pricing.VariantField(
                                    key = "lensMount",
                                    label = "Lens mount",
                                    type = "select",
                                    options = listOf("Sony E"),
                                ),
                            ),
                        completenessOptions = listOf("Battery"),
                    ),
                )

            val pricingRepo = mockk<PricingV4Repository>()
            val requestSlot = slot<com.scanium.app.pricing.PricingV4Request>()
            coEvery { pricingRepo.estimatePrice(capture(requestSlot)) } returns
                Result.success(
                    PricingInsights(
                        status = "OK",
                        countryCode = "NL",
                        range = PriceRange(low = 100.0, median = 120.0, high = 140.0, currency = "EUR"),
                    ),
                )

            val viewModel =
                PricingAssistantViewModel(
                    itemId = "item-1",
                    itemsViewModel = itemsViewModel,
                    pricingV4Repository = pricingRepo,
                    variantSchemaRepository = schemaRepository,
                )

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateVariantValue("lensMount", "Sony E")
            viewModel.toggleCompleteness("Battery")
            viewModel.updateIdentifier("EAN123")

            viewModel.submitPricingRequest("NL")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Sony E", requestSlot.captured.variantAttributes["lensMount"])
            assertTrue(requestSlot.captured.completeness.contains("Battery"))
            assertEquals("EAN123", requestSlot.captured.identifier)

            val state = viewModel.state.value
            assertTrue(state.pricingUiState is com.scanium.app.pricing.PricingUiState.Success)
        }
}
