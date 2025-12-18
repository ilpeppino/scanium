package com.scanium.shared.core.models.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ItemCategory ML Kit label mapping.
 *
 * Tests verify:
 * - Correct mapping from ML Kit labels to ItemCategory
 * - Case-insensitive matching
 * - Unknown/default category handling
 * - All category display names are set
 *
 * Migrated from androidApp to KMP for cross-platform testing.
 */
class ItemCategoryTest {

    @Test
    fun whenFashionGoodLabel_thenMapsToFashionCategory() {
        // Act & Assert
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Fashion good"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Fashion"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Clothing"))
    }

    @Test
    fun whenHomeGoodLabel_thenMapsToHomeGoodCategory() {
        // Act & Assert
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("Home good"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("Home"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("Furniture"))
    }

    @Test
    fun whenFoodLabel_thenMapsToFoodCategory() {
        // Act & Assert
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("Food"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("Food product"))
    }

    @Test
    fun whenPlaceLabel_thenMapsToPlaceCategory() {
        // Act & Assert
        assertEquals(ItemCategory.PLACE, ItemCategory.fromMlKitLabel("Place"))
    }

    @Test
    fun whenPlantLabel_thenMapsToPlantCategory() {
        // Act & Assert
        assertEquals(ItemCategory.PLANT, ItemCategory.fromMlKitLabel("Plant"))
    }

    @Test
    fun whenElectronicsLabel_thenMapsToElectronicsCategory() {
        // Act & Assert
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("Electronics"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("Electronic"))
    }

    @Test
    fun whenUnknownLabel_thenMapsToUnknownCategory() {
        // Act & Assert
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel("Random Label"))
        assertEquals(ItemCategory.UNKNOWN, ItemCategory.fromMlKitLabel("SomeOtherCategory"))
    }

    @Test
    fun whenNullLabel_thenMapsToUnknownCategory() {
        // Act
        val result = ItemCategory.fromMlKitLabel(null)

        // Assert
        assertEquals(ItemCategory.UNKNOWN, result)
    }

    @Test
    fun whenEmptyLabel_thenMapsToUnknownCategory() {
        // Act
        val result = ItemCategory.fromMlKitLabel("")

        // Assert
        assertEquals(ItemCategory.UNKNOWN, result)
    }

    @Test
    fun whenCaseMixedLabel_thenMapsCorrectly() {
        // Act & Assert - Case insensitive
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("FASHION GOOD"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("fashion good"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("FaShIoN gOoD"))
    }

    @Test
    fun whenLabelWithWhitespace_thenMapsCorrectly() {
        // Act & Assert - Labels with spaces
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("Fashion good"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("Home good"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("Food product"))
    }

    @Test
    fun whenAllCategories_thenHaveDisplayNames() {
        // Act & Assert - All categories should have non-empty display names
        ItemCategory.values().forEach { category ->
            assertTrue(category.displayName.isNotEmpty(), "Category ${category.name} should have a display name")
        }
    }

    @Test
    fun whenAllCategories_thenHaveUniqueDisplayNames() {
        // Act
        val displayNames = ItemCategory.values().map { it.displayName }

        // Assert - No duplicates
        assertEquals(displayNames.toSet().size, displayNames.size, "Display names should be unique")
    }

    @Test
    fun whenCategoriesEnumerated_thenContainsExpectedCategories() {
        // Act
        val categories = ItemCategory.values().toList()

        // Assert - Verify all expected categories exist
        assertTrue(categories.contains(ItemCategory.FASHION))
        assertTrue(categories.contains(ItemCategory.HOME_GOOD))
        assertTrue(categories.contains(ItemCategory.FOOD))
        assertTrue(categories.contains(ItemCategory.PLACE))
        assertTrue(categories.contains(ItemCategory.PLANT))
        assertTrue(categories.contains(ItemCategory.ELECTRONICS))
        assertTrue(categories.contains(ItemCategory.UNKNOWN))
    }

    @Test
    fun whenDisplayNamesChecked_thenMatchExpectedValues() {
        // Assert - Verify display names match expected values
        assertEquals("Fashion", ItemCategory.FASHION.displayName)
        assertEquals("Home Good", ItemCategory.HOME_GOOD.displayName)
        assertEquals("Food Product", ItemCategory.FOOD.displayName)
        assertEquals("Place", ItemCategory.PLACE.displayName)
        assertEquals("Plant", ItemCategory.PLANT.displayName)
        assertEquals("Electronics", ItemCategory.ELECTRONICS.displayName)
        assertEquals("Unknown", ItemCategory.UNKNOWN.displayName)
    }

    @Test
    fun whenMappingVariations_thenAllMapCorrectly() {
        // Test various label variations that should map to the same category
        val fashionVariations = listOf("Fashion good", "Fashion", "Clothing", "FASHION", "fashion")
        fashionVariations.forEach { label ->
            assertEquals(
                ItemCategory.FASHION,
                ItemCategory.fromMlKitLabel(label),
                "Label '$label' should map to FASHION"
            )
        }

        val homeGoodVariations = listOf("Home good", "Home", "Furniture", "HOME", "home good")
        homeGoodVariations.forEach { label ->
            assertEquals(
                ItemCategory.HOME_GOOD,
                ItemCategory.fromMlKitLabel(label),
                "Label '$label' should map to HOME_GOOD"
            )
        }

        val electronicsVariations = listOf("Electronics", "Electronic", "ELECTRONICS", "electronics")
        electronicsVariations.forEach { label ->
            assertEquals(
                ItemCategory.ELECTRONICS,
                ItemCategory.fromMlKitLabel(label),
                "Label '$label' should map to ELECTRONICS"
            )
        }
    }

    @Test
    fun whenAdditionalMlKitLabels_thenMapToExpectedCategories() {
        // Fashion/accessories
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("apparel"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("shoe"))
        assertEquals(ItemCategory.FASHION, ItemCategory.fromMlKitLabel("bag"))

        // Home goods / furniture
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("sofa"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("chair"))
        assertEquals(ItemCategory.HOME_GOOD, ItemCategory.fromMlKitLabel("kitchen"))

        // Food and grocery
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("fruit"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("vegetable"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("drink"))
        assertEquals(ItemCategory.FOOD, ItemCategory.fromMlKitLabel("snack"))

        // Electronics / devices
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("device"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("phone"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("laptop"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("monitor"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("tv"))
        assertEquals(ItemCategory.ELECTRONICS, ItemCategory.fromMlKitLabel("gadget"))

        // Plants
        assertEquals(ItemCategory.PLANT, ItemCategory.fromMlKitLabel("flower"))
        assertEquals(ItemCategory.PLANT, ItemCategory.fromMlKitLabel(" plant "))
    }
}
