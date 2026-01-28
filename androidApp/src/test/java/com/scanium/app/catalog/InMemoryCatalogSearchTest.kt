package com.scanium.app.catalog

import com.google.common.truth.Truth.assertThat
import com.scanium.app.catalog.impl.InMemoryCatalogSearch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InMemoryCatalogSearchTest {
    private val defaultEntries =
        listOf(
            CatalogEntry(id = "nike", displayLabel = "Nike", aliases = listOf("NIKE"), popularity = 95),
            CatalogEntry(id = "adidas_nike", displayLabel = "Adidas Nike", popularity = 30),
            CatalogEntry(id = "t_shirt", displayLabel = "T-Shirt", aliases = listOf("tee"), popularity = 90),
            CatalogEntry(id = "super_shirt", displayLabel = "Super T-Shirt", popularity = 40),
            CatalogEntry(id = "shoe_low", displayLabel = "Shoe Alpha", popularity = 10),
            CatalogEntry(id = "shoe_high", displayLabel = "Shoe Beta", popularity = 80),
        )

    private fun searchWith(entries: List<CatalogEntry>): CatalogSearch =
        InMemoryCatalogSearch(
            source =
                object : CatalogSource {
                    override suspend fun loadCatalog(type: CatalogType): List<CatalogEntry> = entries

                    override suspend fun hasUpdate(type: CatalogType): Boolean = false

                    override suspend fun getVersion(type: CatalogType): Int = 1
                },
        )

    @Test
    fun `exact match ranks highest`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.BRANDS, "Nike")

            assertThat(results).isNotEmpty()
            assertThat(results.first().entry.id).isEqualTo("nike")
            assertThat(results.first().matchType).isEqualTo(CatalogSearchResult.MatchType.EXACT)
        }

    @Test
    fun `prefix match ranks above contains`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.BRANDS, "Nik")

            assertThat(results.first().entry.id).isEqualTo("nike")
            assertThat(results.first().matchType).isEqualTo(CatalogSearchResult.MatchType.PREFIX)
            assertThat(results.map { it.entry.id }).contains("adidas_nike")
        }

    @Test
    fun `word boundary match works`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.PRODUCT_TYPES, "shirt")

            assertThat(results.first().entry.id).isEqualTo("t_shirt")
            assertThat(results.first().matchType).isEqualTo(CatalogSearchResult.MatchType.WORD_BOUNDARY)
        }

    @Test
    fun `contains anywhere finds substring`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.PRODUCT_TYPES, "hir")

            assertThat(results.first().entry.id).isEqualTo("t_shirt")
            assertThat(results.first().matchType).isEqualTo(CatalogSearchResult.MatchType.CONTAINS)
        }

    @Test
    fun `alias matches work`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.PRODUCT_TYPES, "tee")

            assertThat(results.first().entry.id).isEqualTo("t_shirt")
            assertThat(results.first().matchType).isEqualTo(CatalogSearchResult.MatchType.ALIAS)
        }

    @Test
    fun `results limited to 20`() =
        runTest {
            val entries =
                (0 until 30).map { index ->
                    CatalogEntry(
                        id = "brand_$index",
                        displayLabel = "Brand $index",
                        popularity = 50,
                    )
                }
            val search = searchWith(entries)
            val results = search.search(CatalogType.BRANDS, "Brand")

            assertThat(results).hasSize(20)
        }

    @Test
    fun `empty query returns empty`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.BRANDS, " ")

            assertThat(results).isEmpty()
        }

    @Test
    fun `popularity affects ranking`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.PRODUCT_TYPES, "shoe")

            assertThat(results.first().entry.id).isEqualTo("shoe_high")
        }

    @Test
    fun `case insensitive matching`() =
        runTest {
            val search = searchWith(defaultEntries)
            val results = search.search(CatalogType.BRANDS, "NIKE")

            assertThat(results.first().entry.id).isEqualTo("nike")
        }
}
