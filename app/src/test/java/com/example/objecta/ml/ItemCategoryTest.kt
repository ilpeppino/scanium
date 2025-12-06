package com.example.objecta.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ItemCategory enum.
 *
 * Tests that all categories including the new DOCUMENT category are properly defined
 * and that ML Kit label mapping works correctly.
 */
class ItemCategoryTest {

    @Test
    fun `verify all categories exist`() {
        val categories = ItemCategory.values()
        assertEquals("Should have exactly 8 categories", 8, categories.size)
    }

    @Test
    fun `verify DOCUMENT category exists and has correct display name`() {
        val category = ItemCategory.DOCUMENT
        assertEquals("Document", category.displayName)
    }

    @Test
    fun `verify all category display names`() {
        assertEquals("Fashion", ItemCategory.FASHION.displayName)
        assertEquals("Home Good", ItemCategory.HOME_GOOD.displayName)
        assertEquals("Food Product", ItemCategory.FOOD.displayName)
        assertEquals("Place", ItemCategory.PLACE.displayName)
        assertEquals("Plant", ItemCategory.PLANT.displayName)
        assertEquals("Electronics", ItemCategory.ELECTRONICS.displayName)
        assertEquals("Document", ItemCategory.DOCUMENT.displayName)
        assertEquals("Unknown", ItemCategory.UNKNOWN.displayName)
    }

    @Test
    fun `verify fromMlKitLabel maps fashion correctly`() {
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Fashion"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("fashion"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Fashion good"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("clothing"))
    }

    @Test
    fun `verify fromMlKitLabel maps home good correctly`() {
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("Home good"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("home"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("furniture"))
    }

    @Test
    fun `verify fromMlKitLabel maps food correctly`() {
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("Food"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("food"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("Food product"))
    }

    @Test
    fun `verify fromMlKitLabel maps electronics correctly`() {
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("Electronics"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("electronics"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("electronic"))
    }

    @Test
    fun `verify fromMlKitLabel maps place correctly`() {
        assertEquals(ItemCategory.PLACE, ItemCategory.fromMlKitLabel("Place"))
        assertEquals(ItemCategory.PLACE, ItemCategory.fromMlKitLabel("place"))
    }

    @Test
    fun `verify fromMlKitLabel maps plant correctly`() {
        assertEquals(ItemCategory.PLANT, ItemCategory.fromMlKitLabel("Plant"))
        assertEquals(ItemCategory.PLANT, ItemCategory.fromMlKitLabel("plant"))
    }

    @Test
    fun `verify fromMlKitLabel returns UNKNOWN for null`() {
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel(null))
    }

    @Test
    fun `verify fromMlKitLabel returns UNKNOWN for unrecognized labels`() {
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel("random"))
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel("unknown"))
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel(""))
    }

    @Test
    fun `verify fromMlKitLabel is case insensitive`() {
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("FASHION"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("FaShIoN"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("ELECTRONICS"))
    }

    @Test
    fun `verify category valueOf works for all categories`() {
        assertEquals(ItemCategory.FASHION, ItemCategory.valueOf("FASHION"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.valueOf("HOME_GOOD"))
        assertEquals(ItemCategory.FOOD, ItemCategory.valueOf("FOOD"))
        assertEquals(ItemCategory.PLACE, ItemCategory.valueOf("PLACE"))
        assertEquals(ItemCategory.PLANT, ItemCategory.valueOf("PLANT"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.valueOf("ELECTRONICS"))
        assertEquals(ItemCategory.DOCUMENT, ItemCategory.valueOf("DOCUMENT"))
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.valueOf("UNKNOWN"))
    }

    @Test
    fun `verify all categories have unique display names`() {
        val categories = ItemCategory.values()
        val displayNames = categories.map { it.displayName }.toSet()
        assertEquals(
            "All categories should have unique display names",
            categories.size,
            displayNames.size
        )
    }
}
