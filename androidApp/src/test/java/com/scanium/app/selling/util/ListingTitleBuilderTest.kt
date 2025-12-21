package com.scanium.app.selling.util

import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import org.junit.Test

class ListingTitleBuilderTest {

    @Test
    fun `buildTitle uses labelText when available`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 30.0,
            labelText = "Decor / Wall Art"
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Decor / Wall Art")
    }

    @Test
    fun `buildTitle capitalizes first character of labelText`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 30.0,
            labelText = "mug"
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Mug")
    }

    @Test
    fun `buildTitle falls back to category displayName when labelText is null`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.ELECTRONICS,
            priceRange = 10.0 to 30.0,
            labelText = null
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Electronics")
    }

    @Test
    fun `buildTitle falls back to category displayName when labelText is blank`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 30.0,
            labelText = "   "
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Home Good")
    }

    @Test
    fun `buildTitle handles UNKNOWN category`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.UNKNOWN,
            priceRange = 10.0 to 30.0,
            labelText = null
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Unknown")
    }

    @Test
    fun `buildTitle trims whitespace from labelText`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 30.0,
            labelText = "  Glass Bottle  "
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Glass Bottle")
    }

    @Test
    fun `buildTitle truncates long titles to 80 characters`() {
        val longLabel = "A".repeat(100)
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 30.0,
            labelText = longLabel
        )

        val title = ListingTitleBuilder.buildTitle(item)

        // "Used " (5 chars) + first 75 chars of label = 80 total
        assertThat(title.length).isAtMost(80)
        assertThat(title).startsWith("Used A")
    }

    @Test
    fun `buildTitle handles specific domain pack labels`() {
        // Test realistic domain pack labels from home_resale.json
        val testCases = listOf(
            "Chair" to "Used Chair",
            "Table / Desk" to "Used Table / Desk",
            "Sofa / Couch" to "Used Sofa / Couch",
            "Lighting" to "Used Lighting",
            "Decor / Wall Art" to "Used Decor / Wall Art"
        )

        testCases.forEach { (label, expectedTitle) ->
            val item = ScannedItem(
                id = "item-test",
                thumbnail = null,
                category = ItemCategory.HOME_GOOD,
                priceRange = 10.0 to 30.0,
                labelText = label
            )

            val title = ListingTitleBuilder.buildTitle(item)

            assertThat(title).isEqualTo(expectedTitle)
        }
    }

    @Test
    fun `buildTitle handles fashion items`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.FASHION,
            priceRange = 20.0 to 50.0,
            labelText = "Leather Jacket"
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Leather Jacket")
    }

    @Test
    fun `buildTitle handles food items`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = null,
            category = ItemCategory.FOOD,
            priceRange = 1.0 to 5.0,
            labelText = "Cereal Box"
        )

        val title = ListingTitleBuilder.buildTitle(item)

        assertThat(title).isEqualTo("Used Cereal Box")
    }
}
