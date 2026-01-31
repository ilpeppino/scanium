package com.scanium.app.items.edit

import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.awaitItems
import com.scanium.app.items.createTestItemsViewModel
import com.scanium.app.pricing.PricingV4Api
import com.scanium.app.pricing.PricingV4Repository
import com.scanium.app.pricing.VariantSchema
import com.scanium.app.pricing.VariantSchemaRepository
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemCondition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PricingAssistantIntegrationTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pricingRequest_onlyFiresAfterSubmit_andMapsSources() =
        runTest(dispatcher) {
            val server = MockWebServer()
            try {
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "success": true,
                          "pricing": {
                            "status": "OK",
                            "countryCode": "NL",
                            "sources": [
                              {
                                "id": "ebay",
                                "name": "eBay",
                                "baseUrl": "https://www.ebay.com",
                                "listingCount": 4,
                                "searchUrl": "https://www.ebay.com/sch/i.html"
                              }
                            ],
                            "totalListingsAnalyzed": 4,
                            "timeWindowDays": 90,
                            "range": { "low": 10.0, "median": 15.0, "high": 20.0, "currency": "EUR" },
                            "sampleListings": [
                              { "title": "Lamp", "price": 12.0, "currency": "EUR", "marketplace": "eBay" }
                            ],
                            "confidence": "HIGH"
                          }
                        }
                        """.trimIndent(),
                    ),
                )
                server.start()

                val api = PricingV4Api(OkHttpClient(), ioDispatcher = dispatcher)
                val pricingRepository =
                    PricingV4Repository(
                        api = api,
                        apiKeyProvider = { "test-key" },
                        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
                    )

                val schemaRepository = mockk<VariantSchemaRepository>()
                coEvery { schemaRepository.fetchSchema(any()) } returns Result.success(VariantSchema())

                val settingsRepository =
                    mockk<SettingsRepository>(relaxed = true).also {
                        every { it.openItemListAfterScanFlow } returns flowOf(false)
                        every { it.smartMergeSuggestionsEnabledFlow } returns flowOf(false)
                        every { it.devForceHypothesisSelectionFlow } returns flowOf(false)
                    }

                val itemsViewModel =
                    createTestItemsViewModel(
                        settingsRepository = settingsRepository,
                        workerDispatcher = dispatcher,
                        mainDispatcher = dispatcher,
                    )

                itemsViewModel.addItem(
                    ScannedItem(
                        id = "item-1",
                        category = ItemCategory.HOME_GOOD,
                        priceRange = 10.0 to 20.0,
                        labelText = "Lamp",
                        condition = ItemCondition.GOOD,
                    ),
                )

                itemsViewModel.awaitItems(dispatcher)
                itemsViewModel.updateItemAttribute("item-1", "brand", ItemAttribute("Acme"))
                itemsViewModel.updateItemAttribute("item-1", "itemType", ItemAttribute("Lamp"))
                itemsViewModel.updateItemAttribute("item-1", "model", ItemAttribute("L200"))
                itemsViewModel.updateItemsFields(
                    mapOf(
                        "item-1" to com.scanium.app.items.state.ItemFieldUpdate(condition = ItemCondition.GOOD),
                    ),
                )
                itemsViewModel.awaitItems(dispatcher)

                val viewModel =
                    PricingAssistantViewModel(
                        itemId = "item-1",
                        itemsViewModel = itemsViewModel,
                        pricingV4Repository = pricingRepository,
                        variantSchemaRepository = schemaRepository,
                    )

                val snapshot = viewModel.state.value
                assertEquals("Acme", snapshot.brand)
                assertEquals("Lamp", snapshot.productType)
                assertEquals("L200", snapshot.model)
                assertEquals(ItemCondition.GOOD, snapshot.condition)
                assertTrue(snapshot.pricingInputs.isComplete())

                assertEquals(0, server.requestCount)

                viewModel.submitPricingRequest("NL")
                val request = server.takeRequest(1, TimeUnit.SECONDS)
                dispatcher.scheduler.advanceUntilIdle()

                assertTrue(request != null)
                assertEquals(1, server.requestCount)
                val state = viewModel.state.value
                val uiState = state.pricingUiState
                assertTrue("Expected Success but was $uiState", uiState is com.scanium.app.pricing.PricingUiState.Success)
                val success = uiState as com.scanium.app.pricing.PricingUiState.Success
                assertEquals("eBay", success.insights.marketplacesUsed.first().name)
                assertEquals("https://www.ebay.com", success.insights.marketplacesUsed.first().baseUrl)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun pricingErrors_exposeActionableMessage() =
        runTest(dispatcher) {
            val server = MockWebServer()
            try {
                server.enqueue(
                    MockResponse().setResponseCode(503).setBody(
                        """
                        {
                          "error": {
                            "code": "SERVER_ERROR",
                            "message": "Pricing service unavailable"
                          }
                        }
                        """.trimIndent(),
                    ),
                )
                server.start()

                val api = PricingV4Api(OkHttpClient(), ioDispatcher = dispatcher)
                val pricingRepository =
                    PricingV4Repository(
                        api = api,
                        apiKeyProvider = { "test-key" },
                        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
                    )

                val schemaRepository = mockk<VariantSchemaRepository>()
                coEvery { schemaRepository.fetchSchema(any()) } returns Result.success(VariantSchema())

                val settingsRepository =
                    mockk<SettingsRepository>(relaxed = true).also {
                        every { it.openItemListAfterScanFlow } returns flowOf(false)
                        every { it.smartMergeSuggestionsEnabledFlow } returns flowOf(false)
                        every { it.devForceHypothesisSelectionFlow } returns flowOf(false)
                    }

                val itemsViewModel =
                    createTestItemsViewModel(
                        settingsRepository = settingsRepository,
                        workerDispatcher = dispatcher,
                        mainDispatcher = dispatcher,
                    )

                itemsViewModel.addItem(
                    ScannedItem(
                        id = "item-2",
                        category = ItemCategory.HOME_GOOD,
                        priceRange = 10.0 to 20.0,
                        labelText = "Lamp",
                        condition = ItemCondition.GOOD,
                    ),
                )

                itemsViewModel.awaitItems(dispatcher)
                itemsViewModel.updateItemAttribute("item-2", "brand", ItemAttribute("Acme"))
                itemsViewModel.updateItemAttribute("item-2", "itemType", ItemAttribute("Lamp"))
                itemsViewModel.updateItemAttribute("item-2", "model", ItemAttribute("L200"))
                itemsViewModel.updateItemsFields(
                    mapOf(
                        "item-2" to com.scanium.app.items.state.ItemFieldUpdate(condition = ItemCondition.GOOD),
                    ),
                )
                itemsViewModel.awaitItems(dispatcher)

                val viewModel =
                    PricingAssistantViewModel(
                        itemId = "item-2",
                        itemsViewModel = itemsViewModel,
                        pricingV4Repository = pricingRepository,
                        variantSchemaRepository = schemaRepository,
                    )

                val snapshot = viewModel.state.value
                assertEquals("Acme", snapshot.brand)
                assertEquals("Lamp", snapshot.productType)
                assertEquals("L200", snapshot.model)
                assertEquals(ItemCondition.GOOD, snapshot.condition)
                assertTrue(snapshot.pricingInputs.isComplete())

                viewModel.submitPricingRequest("NL")
                val request = server.takeRequest(1, TimeUnit.SECONDS)
                dispatcher.scheduler.advanceUntilIdle()
                assertTrue(request != null)

                val uiState = viewModel.state.value.pricingUiState
                assertTrue("Expected Error but was $uiState", uiState is com.scanium.app.pricing.PricingUiState.Error)
                val error = uiState as com.scanium.app.pricing.PricingUiState.Error
                assertEquals("Pricing service unavailable", error.message)
            } finally {
                server.shutdown()
            }
        }
}
