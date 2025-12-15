package com.scanium.app.domain.config

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Unit tests for DomainPack data class and JSON serialization.
 */
class DomainPackTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `DomainPack serialization and deserialization works correctly`() {
        val jsonString = """
            {
              "id": "test_pack",
              "name": "Test Pack",
              "version": "1.0.0",
              "description": "Test description",
              "categories": [
                {
                  "id": "test_category",
                  "displayName": "Test Category",
                  "parentId": null,
                  "itemCategoryName": "UNKNOWN",
                  "prompts": ["test prompt"],
                  "priority": 10,
                  "enabled": true
                }
              ],
              "attributes": [
                {
                  "name": "test_attribute",
                  "type": "STRING",
                  "extractionMethod": "OCR",
                  "appliesToCategoryIds": ["test_category"],
                  "required": false
                }
              ]
            }
        """.trimIndent()

        val pack = json.decodeFromString<DomainPack>(jsonString)

        assertThat(pack.id).isEqualTo("test_pack")
        assertThat(pack.name).isEqualTo("Test Pack")
        assertThat(pack.version).isEqualTo("1.0.0")
        assertThat(pack.description).isEqualTo("Test description")
        assertThat(pack.categories).hasSize(1)
        assertThat(pack.attributes).hasSize(1)
    }

    @Test
    fun `getEnabledCategories returns only enabled categories`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(
                createCategory(id = "cat1", enabled = true),
                createCategory(id = "cat2", enabled = false),
                createCategory(id = "cat3", enabled = true)
            ),
            attributes = emptyList()
        )

        val enabled = pack.getEnabledCategories()

        assertThat(enabled).hasSize(2)
        assertThat(enabled.map { it.id }).containsExactly("cat1", "cat3")
    }

    @Test
    fun `getCategoryById finds category by ID`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(
                createCategory(id = "cat1"),
                createCategory(id = "cat2")
            ),
            attributes = emptyList()
        )

        val category = pack.getCategoryById("cat2")

        assertThat(category).isNotNull()
        assertThat(category?.id).isEqualTo("cat2")
    }

    @Test
    fun `getCategoryById returns null for non-existent ID`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(createCategory(id = "cat1")),
            attributes = emptyList()
        )

        val category = pack.getCategoryById("non_existent")

        assertThat(category).isNull()
    }

    @Test
    fun `getCategoriesForItemCategory filters by itemCategoryName`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(
                createCategory(id = "cat1", itemCategoryName = "ELECTRONICS", enabled = true),
                createCategory(id = "cat2", itemCategoryName = "HOME_GOOD", enabled = true),
                createCategory(id = "cat3", itemCategoryName = "ELECTRONICS", enabled = true),
                createCategory(id = "cat4", itemCategoryName = "ELECTRONICS", enabled = false)
            ),
            attributes = emptyList()
        )

        val electronics = pack.getCategoriesForItemCategory("ELECTRONICS")

        assertThat(electronics).hasSize(2)
        assertThat(electronics.map { it.id }).containsExactly("cat1", "cat3")
    }

    @Test
    fun `getAttributesForCategory returns applicable attributes`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(createCategory(id = "cat1")),
            attributes = listOf(
                createAttribute(name = "attr1", appliesToCategoryIds = listOf("cat1", "cat2")),
                createAttribute(name = "attr2", appliesToCategoryIds = listOf("cat2")),
                createAttribute(name = "attr3", appliesToCategoryIds = listOf("cat1"))
            )
        )

        val attributes = pack.getAttributesForCategory("cat1")

        assertThat(attributes).hasSize(2)
        assertThat(attributes.map { it.name }).containsExactly("attr1", "attr3")
    }

    @Test
    fun `getCategoriesByPriority sorts by priority descending`() {
        val pack = DomainPack(
            id = "test",
            name = "Test",
            version = "1.0.0",
            categories = listOf(
                createCategory(id = "cat1", priority = 5, enabled = true),
                createCategory(id = "cat2", priority = 15, enabled = true),
                createCategory(id = "cat3", priority = 10, enabled = true),
                createCategory(id = "cat4", priority = null, enabled = true),
                createCategory(id = "cat5", priority = 20, enabled = false) // disabled, should be excluded
            ),
            attributes = emptyList()
        )

        val sorted = pack.getCategoriesByPriority()

        assertThat(sorted).hasSize(4)
        assertThat(sorted.map { it.id }).containsExactly("cat2", "cat3", "cat1", "cat4").inOrder()
    }

    // Helper functions

    private fun createCategory(
        id: String,
        displayName: String = "Test Category",
        parentId: String? = null,
        itemCategoryName: String = "UNKNOWN",
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

    private fun createAttribute(
        name: String,
        type: AttributeType = AttributeType.STRING,
        extractionMethod: ExtractionMethod = ExtractionMethod.OCR,
        appliesToCategoryIds: List<String> = emptyList(),
        required: Boolean = false
    ) = DomainAttribute(
        name = name,
        type = type,
        extractionMethod = extractionMethod,
        appliesToCategoryIds = appliesToCategoryIds,
        required = required
    )
}
