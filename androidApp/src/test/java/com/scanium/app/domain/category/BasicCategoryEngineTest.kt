package com.scanium.app.domain.category

import com.google.common.truth.Truth.assertThat
import com.scanium.app.domain.config.AttributeType
import com.scanium.app.domain.config.DomainAttribute
import com.scanium.app.domain.config.DomainCategory
import com.scanium.app.domain.config.DomainPack
import com.scanium.app.domain.config.ExtractionMethod
import com.scanium.app.domain.repository.DomainPackRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for BasicCategoryEngine.
 */
@RunWith(RobolectricTestRunner::class)
class BasicCategoryEngineTest {
    private lateinit var repository: DomainPackRepository
    private lateinit var engine: BasicCategoryEngine
    private lateinit var testPack: DomainPack

    @Before
    fun setup() {
        // Create test domain pack
        testPack = createTestDomainPack()

        // Mock repository
        repository = mockk()
        coEvery { repository.getActiveDomainPack() } returns testPack

        // Create engine
        engine = BasicCategoryEngine(repository)
    }

    @Test
    fun `selectCategory returns null when mlKitLabel is null`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = null)

            val result = engine.selectCategory(input)

            assertThat(result).isNull()
        }

    @Test
    fun `selectCategory returns null when mlKitLabel is empty`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "")

            val result = engine.selectCategory(input)

            assertThat(result).isNull()
        }

    @Test
    fun `selectCategory matches exact display name`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "Sofa")

            val result = engine.selectCategory(input)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo("furniture_sofa")
        }

    @Test
    fun `selectCategory is case insensitive`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "LAPTOP")

            val result = engine.selectCategory(input)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo("electronics_laptop")
        }

    @Test
    fun `selectCategory matches substring`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "furniture")

            val result = engine.selectCategory(input)

            // Should match categories with "furniture" in the parent ID
            assertThat(result).isNotNull()
            // Should be one of the furniture categories
            assertThat(result?.parentId).isEqualTo("furniture")
        }

    @Test
    fun `selectCategory prefers higher priority on multiple matches`() =
        runTest {
            // Both laptop and phone match "electronics", but laptop has higher priority
            val input = CategorySelectionInput(mlKitLabel = "electronics")

            val result = engine.selectCategory(input)

            // Should select the category with highest priority
            assertThat(result).isNotNull()
            // Laptop has priority 15, higher than phone's 12 (both have parentId "electronics")
            assertThat(result?.id).isEqualTo("electronics_laptop")
        }

    @Test
    fun `selectCategory ignores disabled categories`() =
        runTest {
            // Create pack with a disabled category
            val packWithDisabled =
                testPack.copy(
                    categories =
                        testPack.categories.map {
                            if (it.id == "furniture_sofa") {
                                it.copy(enabled = false)
                            } else {
                                it
                            }
                        },
                )

            coEvery { repository.getActiveDomainPack() } returns packWithDisabled

            val input = CategorySelectionInput(mlKitLabel = "Sofa")

            val result = engine.selectCategory(input)

            // Should not match disabled sofa
            assertThat(result?.id).isNotEqualTo("furniture_sofa")
        }

    @Test
    fun `selectCategory returns null when no matches found`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "NonExistentCategory")

            val result = engine.selectCategory(input)

            assertThat(result).isNull()
        }

    @Test
    fun `getCandidateCategories returns empty list for null label`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = null)

            val result = engine.getCandidateCategories(input)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getCandidateCategories returns scored matches`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "electronics")

            val result = engine.getCandidateCategories(input)

            // Should return multiple electronics categories
            assertThat(result).isNotEmpty()

            // Verify structure
            result.forEach { (category, score) ->
                assertThat(category).isNotNull()
                assertThat(score).isAtLeast(0.0f)
                assertThat(score).isAtMost(1.0f)
            }
        }

    @Test
    fun `getCandidateCategories sorts by score descending`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "laptop")

            val result = engine.getCandidateCategories(input)

            // Scores should be in descending order
            var previousScore = Float.MAX_VALUE
            result.forEach { (_, score) ->
                assertThat(score).isAtMost(previousScore)
                previousScore = score
            }
        }

    @Test
    fun `selectCategory handles whitespace in labels`() =
        runTest {
            val input = CategorySelectionInput(mlKitLabel = "  Laptop  ")

            val result = engine.selectCategory(input)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo("electronics_laptop")
        }

    @Test
    fun `selectCategory matches real ML Kit labels`() =
        runTest {
            // Test with labels that actually match our test data structure
            val testCases =
                listOf(
                    "furniture" to "furniture",
// Matches parentId "furniture"
                    "shoes" to "clothing_shoes",
// Matches displayName "Shoes"
                    "electronics" to "electronics",
// Matches parentId "electronics"
                )

            testCases.forEach { (mlKitLabel, expectedMatch) ->
                val input = CategorySelectionInput(mlKitLabel = mlKitLabel)
                val result = engine.selectCategory(input)

                assertThat(result).isNotNull()
                // Verify it matches the expected pattern
                assertThat(result?.id).contains(expectedMatch)
            }
        }

    // Helper functions

    private fun createTestDomainPack(): DomainPack {
        val categories =
            listOf(
                createCategory(
                    id = "furniture_sofa",
                    displayName = "Sofa",
                    parentId = "furniture",
                    itemCategoryName = "HOME_GOOD",
                    priority = 10,
                ),
                createCategory(
                    id = "furniture_chair",
                    displayName = "Chair",
                    parentId = "furniture",
                    itemCategoryName = "HOME_GOOD",
                    priority = 10,
                ),
                createCategory(
                    id = "electronics_laptop",
                    displayName = "Laptop",
                    parentId = "electronics",
                    itemCategoryName = "ELECTRONICS",
                    priority = 15,
                ),
                createCategory(
                    id = "electronics_phone",
                    displayName = "Smartphone",
                    parentId = "electronics",
                    itemCategoryName = "ELECTRONICS",
                    priority = 12,
                ),
                createCategory(
                    id = "clothing_shoes",
                    displayName = "Shoes",
                    parentId = "clothing",
                    itemCategoryName = "FASHION",
                    priority = 12,
                ),
            )

        val attributes =
            listOf(
                createAttribute(
                    name = "brand",
                    appliesToCategoryIds = listOf("electronics_laptop", "electronics_phone"),
                ),
            )

        return DomainPack(
            id = "test_pack",
            name = "Test Pack",
            version = "1.0.0",
            categories = categories,
            attributes = attributes,
        )
    }

    private fun createCategory(
        id: String,
        displayName: String,
        parentId: String?,
        itemCategoryName: String,
        prompts: List<String> = listOf("test prompt"),
        priority: Int? = 10,
        enabled: Boolean = true,
    ) = DomainCategory(
        id = id,
        displayName = displayName,
        parentId = parentId,
        itemCategoryName = itemCategoryName,
        prompts = prompts,
        priority = priority,
        enabled = enabled,
    )

    private fun createAttribute(
        name: String,
        type: AttributeType = AttributeType.STRING,
        extractionMethod: ExtractionMethod = ExtractionMethod.OCR,
        appliesToCategoryIds: List<String>,
        required: Boolean = false,
    ) = DomainAttribute(
        name = name,
        type = type,
        extractionMethod = extractionMethod,
        appliesToCategoryIds = appliesToCategoryIds,
        required = required,
    )
}
