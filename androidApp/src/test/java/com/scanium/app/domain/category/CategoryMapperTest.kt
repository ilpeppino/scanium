package com.scanium.app.domain.category

import com.google.common.truth.Truth.assertThat
import com.scanium.app.domain.category.CategoryMapper.getValidItemCategoryNames
import com.scanium.app.domain.category.CategoryMapper.hasValidItemCategory
import com.scanium.app.domain.category.CategoryMapper.toItemCategory
import com.scanium.app.domain.category.CategoryMapper.toItemCategoryOrDefault
import com.scanium.app.domain.config.DomainCategory
import com.scanium.core.models.ml.ItemCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for CategoryMapper utility.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryMapperTest {

    @Test
    fun `toItemCategory maps valid itemCategoryName to ItemCategory`() {
        val category = createCategory(itemCategoryName = "ELECTRONICS")

        val result = category.toItemCategory()

        assertThat(result).isEqualTo(ItemCategory.ELECTRONICS)
    }

    @Test
    fun `toItemCategory returns null for invalid itemCategoryName`() {
        val category = createCategory(itemCategoryName = "INVALID_NAME")

        val result = category.toItemCategory()

        assertThat(result).isNull()
    }

    @Test
    fun `toItemCategory maps all valid ItemCategory enum values`() {
        val validNames = listOf(
            "FASHION" to ItemCategory.FASHION,
            "HOME_GOOD" to ItemCategory.HOME_GOOD,
            "FOOD" to ItemCategory.FOOD,
            "PLACE" to ItemCategory.PLACE,
            "PLANT" to ItemCategory.PLANT,
            "ELECTRONICS" to ItemCategory.ELECTRONICS,
            "DOCUMENT" to ItemCategory.DOCUMENT,
            "UNKNOWN" to ItemCategory.UNKNOWN
        )

        validNames.forEach { (name, expected) ->
            val category = createCategory(itemCategoryName = name)
            val result = category.toItemCategory()
            assertThat(result).isEqualTo(expected)
        }
    }

    @Test
    fun `toItemCategoryOrDefault returns mapped category for valid name`() {
        val category = createCategory(itemCategoryName = "FASHION")

        val result = category.toItemCategoryOrDefault()

        assertThat(result).isEqualTo(ItemCategory.FASHION)
    }

    @Test
    fun `toItemCategoryOrDefault returns UNKNOWN for invalid name`() {
        val category = createCategory(itemCategoryName = "INVALID")

        val result = category.toItemCategoryOrDefault()

        assertThat(result).isEqualTo(ItemCategory.UNKNOWN)
    }

    @Test
    fun `toItemCategoryOrDefault uses custom default`() {
        val category = createCategory(itemCategoryName = "INVALID")

        val result = category.toItemCategoryOrDefault(default = ItemCategory.HOME_GOOD)

        assertThat(result).isEqualTo(ItemCategory.HOME_GOOD)
    }

    @Test
    fun `hasValidItemCategory returns true for valid names`() {
        val category = createCategory(itemCategoryName = "PLANT")

        val result = category.hasValidItemCategory()

        assertThat(result).isTrue()
    }

    @Test
    fun `hasValidItemCategory returns false for invalid names`() {
        val category = createCategory(itemCategoryName = "NOT_A_REAL_CATEGORY")

        val result = category.hasValidItemCategory()

        assertThat(result).isFalse()
    }

    @Test
    fun `getValidItemCategoryNames returns all enum values`() {
        val validNames = getValidItemCategoryNames()

        assertThat(validNames).containsExactly(
            "FASHION", "HOME_GOOD", "FOOD", "PLACE", "PLANT",
            "ELECTRONICS", "DOCUMENT", "UNKNOWN"
        )
    }

    @Test
    fun `toItemCategory is case sensitive`() {
        val category = createCategory(itemCategoryName = "electronics") // lowercase

        val result = category.toItemCategory()

        // Should return null because enum names are uppercase
        assertThat(result).isNull()
    }

    @Test
    fun `mapping works for all categories in home resale pack`() {
        // These are the actual itemCategoryName values used in home_resale_domain_pack.json
        val usedNames = listOf(
            "HOME_GOOD",
            "ELECTRONICS",
            "FASHION",
            "UNKNOWN",
            "PLANT"
        )

        usedNames.forEach { name ->
            val category = createCategory(itemCategoryName = name)
            val result = category.toItemCategory()
            assertThat(result).isNotNull()
        }
    }

    // Helper function

    private fun createCategory(
        id: String = "test_id",
        displayName: String = "Test Category",
        parentId: String? = null,
        itemCategoryName: String,
        prompts: List<String> = listOf("test prompt"),
        priority: Int? = 10,
        enabled: Boolean = true
    ) = DomainCategory(
        id = id,
        displayName = displayName,
        parentId = parentId,
        itemCategoryName = itemCategoryName,
        prompts = prompts,
        priority = priority,
        enabled = enabled
    )
}
