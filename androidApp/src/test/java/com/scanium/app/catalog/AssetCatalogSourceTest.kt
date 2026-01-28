package com.scanium.app.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.catalog.impl.AssetCatalogSource
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssetCatalogSourceTest {
    private lateinit var context: Context
    private lateinit var source: AssetCatalogSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        source = AssetCatalogSource(context)
    }

    @Test
    fun `loads brands from assets`() =
        runTest {
            val entries = source.loadCatalog(CatalogType.BRANDS)

            assertThat(entries).isNotEmpty()
            assertThat(entries.map { it.id }).contains("nike")
        }

    @Test
    fun `caches after first load`() =
        runTest {
            val first = source.loadCatalog(CatalogType.PRODUCT_TYPES)
            val second = source.loadCatalog(CatalogType.PRODUCT_TYPES)

            assertThat(second).isSameInstanceAs(first)
        }
}
