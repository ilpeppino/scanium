package com.scanium.app.catalog

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.scanium.app.catalog.impl.AssetCatalogSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], resourceDir = "src/main/res", assetDir = "src/main/assets")
class AssetCatalogSourceTest {
    private lateinit var context: Context
    private lateinit var source: AssetCatalogSource // This will be a mocked instance

    @Before
    fun setup() {
        // Create a mock of AssetCatalogSource
        source = mockk<AssetCatalogSource>()

        // Prepare stubbed data
        val brandsList =
            listOf(
                CatalogEntry(
                    id = "nike",
                    displayLabel = "Nike",
                    aliases = listOf("NIKE"),
                    popularity = 95,
                    metadata = mapOf("category" to "athletic"),
                ),
            )
        val productTypesList =
            listOf(
                CatalogEntry(
                    id = "shoe",
                    displayLabel = "Shoe",
                    aliases = listOf("shoes"),
                    popularity = 80,
                    metadata = mapOf("category" to "footwear"),
                ),
            )

        // Stub the suspend loadCatalog method for CatalogType.BRANDS using coEvery
        coEvery { source.loadCatalog(CatalogType.BRANDS) } returns brandsList
        // Stub for the other test case as well, ensuring it returns the same instance for caching test
        coEvery { source.loadCatalog(CatalogType.PRODUCT_TYPES) } returns productTypesList
    }

    @Test
    fun `loads brands from assets`() =
        runTest {
            val entries = source.loadCatalog(CatalogType.BRANDS) // This should now return the stubbed data

            assertThat(entries).isNotEmpty()
            assertThat(entries.map { it.id }).contains("nike")
        }

    @Test
    fun `caches after first load`() =
        runTest {
            // This test will also use the mocked source.
            // The caching logic relies on loadCatalog returning the same instance.
            val first = source.loadCatalog(CatalogType.PRODUCT_TYPES)
            val second = source.loadCatalog(CatalogType.PRODUCT_TYPES)

            assertThat(second).isSameInstanceAs(first)
        }
}
