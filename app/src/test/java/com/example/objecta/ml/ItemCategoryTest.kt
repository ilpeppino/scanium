package com.example.objecta.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for ItemCategory ML Kit label mapping.
 *
 * Tests verify:
 * - Correct mapping from ML Kit labels to ItemCategory
 * - Case-insensitive matching
 * - Unknown/default category handling
 * - All category display names are set
 */
class ItemCategoryTest {

    @Test
    fun whenFashionGoodLabel_thenMapsToFashionCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Fashion good")).isEqualTo(ItemCategory.FASHION)
        assertThat(ItemCategory.fromMlKitLabel("Fashion")).isEqualTo(ItemCategory.FASHION)
        assertThat(ItemCategory.fromMlKitLabel("Clothing")).isEqualTo(ItemCategory.FASHION)
    }

    @Test
    fun whenHomeGoodLabel_thenMapsToHomeGoodCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Home good")).isEqualTo(ItemCategory.HOME_GOOD)
        assertThat(ItemCategory.fromMlKitLabel("Home")).isEqualTo(ItemCategory.HOME_GOOD)
        assertThat(ItemCategory.fromMlKitLabel("Furniture")).isEqualTo(ItemCategory.HOME_GOOD)
    }

    @Test
    fun whenFoodLabel_thenMapsToFoodCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Food")).isEqualTo(ItemCategory.FOOD)
        assertThat(ItemCategory.fromMlKitLabel("Food product")).isEqualTo(ItemCategory.FOOD)
    }

    @Test
    fun whenPlaceLabel_thenMapsToPlaceCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Place")).isEqualTo(ItemCategory.PLACE)
    }

    @Test
    fun whenPlantLabel_thenMapsToPlantCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Plant")).isEqualTo(ItemCategory.PLANT)
    }

    @Test
    fun whenElectronicsLabel_thenMapsToElectronicsCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Electronics")).isEqualTo(ItemCategory.ELECTRONICS)
        assertThat(ItemCategory.fromMlKitLabel("Electronic")).isEqualTo(ItemCategory.ELECTRONICS)
    }

    @Test
    fun whenUnknownLabel_thenMapsToUnknownCategory() {
        // Act & Assert
        assertThat(ItemCategory.fromMlKitLabel("Random Label")).isEqualTo(ItemCategory.UNKNOWN)
        assertThat(ItemCategory.fromMlKitLabel("SomeOtherCategory")).isEqualTo(ItemCategory.UNKNOWN)
    }

    @Test
    fun whenNullLabel_thenMapsToUnknownCategory() {
        // Act
        val result = ItemCategory.fromMlKitLabel(null)

        // Assert
        assertThat(result).isEqualTo(ItemCategory.UNKNOWN)
    }

    @Test
    fun whenEmptyLabel_thenMapsToUnknownCategory() {
        // Act
        val result = ItemCategory.fromMlKitLabel("")

        // Assert
        assertThat(result).isEqualTo(ItemCategory.UNKNOWN)
    }

    @Test
    fun whenCaseMixedLabel_thenMapsCorrectly() {
        // Act & Assert - Case insensitive
        assertThat(ItemCategory.fromMlKitLabel("FASHION GOOD")).isEqualTo(ItemCategory.FASHION)
        assertThat(ItemCategory.fromMlKitLabel("fashion good")).isEqualTo(ItemCategory.FASHION)
        assertThat(ItemCategory.fromMlKitLabel("FaShIoN gOoD")).isEqualTo(ItemCategory.FASHION)
    }

    @Test
    fun whenLabelWithWhitespace_thenMapsCorrectly() {
        // Act & Assert - Labels with spaces
        assertThat(ItemCategory.fromMlKitLabel("Fashion good")).isEqualTo(ItemCategory.FASHION)
        assertThat(ItemCategory.fromMlKitLabel("Home good")).isEqualTo(ItemCategory.HOME_GOOD)
        assertThat(ItemCategory.fromMlKitLabel("Food product")).isEqualTo(ItemCategory.FOOD)
    }

    @Test
    fun whenAllCategories_thenHaveDisplayNames() {
        // Act & Assert - All categories should have non-empty display names
        ItemCategory.values().forEach { category ->
            assertThat(category.displayName).isNotEmpty()
        }
    }

    @Test
    fun whenAllCategories_thenHaveUniqueDisplayNames() {
        // Act
        val displayNames = ItemCategory.values().map { it.displayName }

        // Assert - No duplicates
        assertThat(displayNames).containsNoDuplicates()
    }

    @Test
    fun whenCategoriesEnumerated_thenContainsExpectedCategories() {
        // Act
        val categories = ItemCategory.values().toList()

        // Assert - Verify all expected categories exist
        assertThat(categories).contains(ItemCategory.FASHION)
        assertThat(categories).contains(ItemCategory.HOME_GOOD)
        assertThat(categories).contains(ItemCategory.FOOD)
        assertThat(categories).contains(ItemCategory.PLACE)
        assertThat(categories).contains(ItemCategory.PLANT)
        assertThat(categories).contains(ItemCategory.ELECTRONICS)
        assertThat(categories).contains(ItemCategory.UNKNOWN)
    }

    @Test
    fun whenDisplayNamesChecked_thenMatchExpectedValues() {
        // Assert - Verify display names match expected values
        assertThat(ItemCategory.FASHION.displayName).isEqualTo("Fashion")
        assertThat(ItemCategory.HOME_GOOD.displayName).isEqualTo("Home Good")
        assertThat(ItemCategory.FOOD.displayName).isEqualTo("Food Product")
        assertThat(ItemCategory.PLACE.displayName).isEqualTo("Place")
        assertThat(ItemCategory.PLANT.displayName).isEqualTo("Plant")
        assertThat(ItemCategory.ELECTRONICS.displayName).isEqualTo("Electronics")
        assertThat(ItemCategory.UNKNOWN.displayName).isEqualTo("Unknown")
    }

    @Test
    fun whenMappingVariations_thenAllMapCorrectly() {
        // Test various label variations that should map to the same category
        val fashionVariations = listOf("Fashion good", "Fashion", "Clothing", "FASHION", "fashion")
        fashionVariations.forEach { label ->
            assertThat(ItemCategory.fromMlKitLabel(label))
                .isEqualTo(ItemCategory.FASHION)
        }

        val homeGoodVariations = listOf("Home good", "Home", "Furniture", "HOME", "home good")
        homeGoodVariations.forEach { label ->
            assertThat(ItemCategory.fromMlKitLabel(label))
                .isEqualTo(ItemCategory.HOME_GOOD)
        }

        val electronicsVariations = listOf("Electronics", "Electronic", "ELECTRONICS", "electronics")
        electronicsVariations.forEach { label ->
            assertThat(ItemCategory.fromMlKitLabel(label))
                .isEqualTo(ItemCategory.ELECTRONICS)
        }
    }
}
