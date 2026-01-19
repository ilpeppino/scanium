package com.scanium.app.quality

import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.app.ItemCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.InputStreamReader

/**
 * Golden tests for completeness evaluation.
 *
 * These tests verify that the completeness evaluator produces expected results
 * for representative item scenarios. Each golden fixture defines:
 * - Category and attributes
 * - Expected score range
 * - Expected ready status
 * - Required missing attributes
 *
 * Run with: ./gradlew test --tests "*.CompletenessGoldenTest"
 *
 * CI-safe: These tests use static fixtures and don't require network or real vision APIs.
 */
@RunWith(Parameterized::class)
class CompletenessGoldenTest(
    private val scenarioName: String,
    private val fixture: GoldenFixture,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val fixtures =
                listOf(
                    loadFixture("golden/nike_shoe_attributes.json"),
                    loadFixture("golden/iphone_partial_attributes.json"),
                    // Add inline fixtures for edge cases
                    createInlineFixture(
                        name = "empty_attributes",
                        description = "No attributes detected",
                        category = ItemCategory.FASHION,
                        attributes = emptyMap(),
                        minScore = null,
                        maxScore = 5,
                        isReadyForListing = false,
                        requiredMissing = listOf("brand", "itemType"),
                    ),
                    createInlineFixture(
                        name = "low_confidence_brand",
                        description = "Brand detected with low confidence",
                        category = ItemCategory.FASHION,
                        attributes =
                            mapOf(
                                "brand" to ItemAttribute(value = "Unknown", confidence = 0.2f, source = "vision"),
                            ),
                        minScore = null,
                        maxScore = 10,
                        isReadyForListing = false,
                        // Should still be missing due to low confidence
                        requiredMissing = listOf("brand"),
                    ),
                    createInlineFixture(
                        name = "user_edited_brand",
                        description = "Brand entered by user (always trusted)",
                        category = ItemCategory.FASHION,
                        attributes =
                            mapOf(
                                "brand" to ItemAttribute(value = "Gucci", confidence = 0.1f, source = "user"),
                                "color" to ItemAttribute(value = "Red", confidence = 0.9f, source = "vision"),
                            ),
                        minScore = 30,
                        maxScore = null,
                        isReadyForListing = false,
                        // User brand should count
                        requiredMissing = listOf("itemType"),
                    ),
                )
            return fixtures.map { arrayOf(it.name, it) }
        }

        private fun loadFixture(resourcePath: String): GoldenFixture {
            val stream =
                CompletenessGoldenTest::class.java.classLoader
                    ?.getResourceAsStream(resourcePath)
                    ?: throw IllegalArgumentException("Resource not found: $resourcePath")

            val jsonText = InputStreamReader(stream).use { it.readText() }
            val obj = json.parseToJsonElement(jsonText).jsonObject

            val scenario = obj["scenario"]!!.jsonPrimitive.content
            val description = obj["description"]!!.jsonPrimitive.content
            val category = ItemCategory.valueOf(obj["category"]!!.jsonPrimitive.content)

            val attributesObj = obj["attributes"]!!.jsonObject
            val attributes = mutableMapOf<String, ItemAttribute>()
            attributesObj.keys.forEach { key ->
                val attrObj = attributesObj[key]!!.jsonObject
                attributes[key] =
                    ItemAttribute(
                        value = attrObj["value"]!!.jsonPrimitive.content,
                        confidence = attrObj["confidence"]!!.jsonPrimitive.float,
                        source = attrObj["source"]?.jsonPrimitive?.content ?: "",
                    )
            }

            val expected = obj["expected"]!!.jsonObject
            val minScore = expected["minScore"]?.jsonPrimitive?.int?.takeIf { it >= 0 }
            val maxScore = expected["maxScore"]?.jsonPrimitive?.int?.takeIf { it >= 0 }
            val isReadyForListing = expected["isReadyForListing"]!!.jsonPrimitive.content.toBoolean()

            val requiredMissing = mutableListOf<String>()
            expected["requiredMissing"]?.jsonArray?.forEach { elem ->
                requiredMissing.add(elem.jsonPrimitive.content)
            }

            return GoldenFixture(
                name = scenario,
                description = description,
                category = category,
                attributes = attributes,
                minScore = minScore,
                maxScore = maxScore,
                isReadyForListing = isReadyForListing,
                requiredMissing = requiredMissing,
            )
        }

        private fun createInlineFixture(
            name: String,
            description: String,
            category: ItemCategory,
            attributes: Map<String, ItemAttribute>,
            minScore: Int?,
            maxScore: Int?,
            isReadyForListing: Boolean,
            requiredMissing: List<String>,
        ): GoldenFixture {
            return GoldenFixture(
                name = name,
                description = description,
                category = category,
                attributes = attributes,
                minScore = minScore,
                maxScore = maxScore,
                isReadyForListing = isReadyForListing,
                requiredMissing = requiredMissing,
            )
        }
    }

    data class GoldenFixture(
        val name: String,
        val description: String,
        val category: ItemCategory,
        val attributes: Map<String, ItemAttribute>,
        val minScore: Int?,
        val maxScore: Int?,
        val isReadyForListing: Boolean,
        val requiredMissing: List<String>,
    )

    @Test
    fun `evaluate completeness matches golden expectations`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = fixture.category,
                attributes = fixture.attributes,
            )

        // Verify score is in expected range
        fixture.minScore?.let { min ->
            assertTrue(
                "Score ${result.score} should be >= $min for ${fixture.name}: ${fixture.description}",
                result.score >= min,
            )
        }
        fixture.maxScore?.let { max ->
            assertTrue(
                "Score ${result.score} should be <= $max for ${fixture.name}: ${fixture.description}",
                result.score <= max,
            )
        }

        // Verify ready status
        assertEquals(
            "Ready status mismatch for ${fixture.name}: ${fixture.description}",
            fixture.isReadyForListing,
            result.isReadyForListing,
        )

        // Verify required missing attributes are present
        val missingKeys = result.missingAttributes.map { it.key }
        fixture.requiredMissing.forEach { required ->
            assertTrue(
                "Expected '$required' to be missing for ${fixture.name}: ${fixture.description}",
                missingKeys.contains(required),
            )
        }
    }
}
