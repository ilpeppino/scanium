package com.scanium.app.selling.assistant.local

import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import org.junit.Test

/**
 * Unit tests for LocalSuggestionEngine.
 * Verifies deterministic output and category-specific logic.
 */
class LocalSuggestionEngineTest {
    private val engine = LocalSuggestionEngine()

    // ========================================================================
    // Determinism Tests
    // ========================================================================

    @Test
    fun `same input yields same output - deterministic`() {
        val snapshot = createSnapshot(
            category = "electronics",
            brand = "Sony",
            photosCount = 2,
        )

        val result1 = engine.generateSuggestions(snapshot)
        val result2 = engine.generateSuggestions(snapshot)

        assertThat(result1).isEqualTo(result2)
    }

    // ========================================================================
    // Missing Info Prompt Tests
    // ========================================================================

    @Test
    fun `missing brand generates brand prompt`() {
        val snapshot = createSnapshot(
            category = "electronics",
            brand = null,
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("brand")
        val brandPrompt = result.missingInfoPrompts.first { it.field == "brand" }
        assertThat(brandPrompt.benefit).contains("search")
    }

    @Test
    fun `missing color generates color prompt`() {
        val snapshot = createSnapshot(
            category = "home",
            color = null,
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("color")
    }

    @Test
    fun `missing size for fashion generates size prompt`() {
        val snapshot = createSnapshot(
            category = "fashion",
            size = null,
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("size")
        val sizePrompt = result.missingInfoPrompts.first { it.field == "size" }
        assertThat(sizePrompt.benefit).contains("apparel")
    }

    @Test
    fun `few photos generates photos prompt`() {
        val snapshot = createSnapshot(
            category = "electronics",
            photosCount = 1,
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("photos")
        val photosPrompt = result.missingInfoPrompts.first { it.field == "photos" }
        assertThat(photosPrompt.prompt).contains("1/4+")
    }

    @Test
    fun `short title generates title prompt`() {
        val snapshot = createSnapshot(
            title = "Lamp", // Only 4 chars
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("title")
    }

    @Test
    fun `empty description generates description prompt`() {
        val snapshot = createSnapshot(
            description = null,
        )

        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts.map { it.field }).contains("description")
    }

    // ========================================================================
    // Category Classification Tests
    // ========================================================================

    @Test
    fun `electronics category classified correctly`() {
        val categories = listOf("electronics", "phone", "laptop", "camera", "Computer")
        categories.forEach { cat ->
            val snapshot = createSnapshot(category = cat)
            val result = engine.generateSuggestions(snapshot)

            // Electronics should have electronics-specific checklist items
            assertThat(result.suggestedDefectsChecklist).contains("Battery health/runtime issues")
        }
    }

    @Test
    fun `fashion category classified correctly`() {
        val categories = listOf("fashion", "clothing", "shoes", "Accessories")
        categories.forEach { cat ->
            val snapshot = createSnapshot(category = cat)
            val result = engine.generateSuggestions(snapshot)

            // Fashion should have fashion-specific checklist items
            assertThat(result.suggestedDefectsChecklist).contains("Stains or discoloration")
        }
    }

    @Test
    fun `home furniture category classified correctly`() {
        val categories = listOf("furniture", "home", "decor", "chair", "Table")
        categories.forEach { cat ->
            val snapshot = createSnapshot(category = cat)
            val result = engine.generateSuggestions(snapshot)

            // Home/furniture should have furniture-specific checklist items
            assertThat(result.suggestedDefectsChecklist).contains("Wobbly or unstable")
        }
    }

    @Test
    fun `toys category classified correctly`() {
        val snapshot = createSnapshot(category = "toys and games")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDefectsChecklist).contains("Missing pieces")
    }

    @Test
    fun `kitchen category classified correctly`() {
        val snapshot = createSnapshot(category = "kitchen appliances")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDefectsChecklist).contains("Rust or corrosion")
    }

    @Test
    fun `unknown category uses general checklist`() {
        val snapshot = createSnapshot(category = "random stuff xyz")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDefectsChecklist).contains("General wear")
    }

    // ========================================================================
    // Defects Checklist Tests
    // ========================================================================

    @Test
    fun `electronics checklist contains expected items`() {
        val snapshot = createSnapshot(category = "electronics")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDefectsChecklist).containsAtLeast(
            "Scratches on screen or body",
            "Battery health/runtime issues",
            "Charging port condition",
        )
    }

    @Test
    fun `fashion checklist contains expected items`() {
        val snapshot = createSnapshot(category = "clothing")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDefectsChecklist).containsAtLeast(
            "Stains or discoloration",
            "Tears or holes",
            "Pilling or fabric wear",
        )
    }

    // ========================================================================
    // Photo Suggestions Tests
    // ========================================================================

    @Test
    fun `electronics photo suggestions include model label`() {
        val snapshot = createSnapshot(category = "electronics", photosCount = 1)
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedPhotos.any { it.contains("model", ignoreCase = true) || it.contains("label", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `fashion photo suggestions include size label`() {
        val snapshot = createSnapshot(category = "shoes", photosCount = 1)
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedPhotos.any { it.contains("label", ignoreCase = true) || it.contains("brand", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `few photos returns essential suggestions first`() {
        val snapshot = createSnapshot(category = "electronics", photosCount = 1)
        val result = engine.generateSuggestions(snapshot)

        // Should have <= 4 suggestions when few photos
        assertThat(result.suggestedPhotos.size).isAtMost(4)
    }

    // ========================================================================
    // Suggested Questions Tests
    // ========================================================================

    @Test
    fun `missing brand adds brand question`() {
        val snapshot = createSnapshot(
            category = "electronics",
            brand = null,
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedQuestions.any { it.contains("brand", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `electronics category adds electronics questions`() {
        val snapshot = createSnapshot(category = "electronics")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedQuestions.any {
            it.contains("power", ignoreCase = true) || it.contains("storage", ignoreCase = true)
        }).isTrue()
    }

    @Test
    fun `fashion category asks about size when missing`() {
        val snapshot = createSnapshot(
            category = "clothing",
            size = null,
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedQuestions.any { it.contains("size", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `questions are limited to max 5`() {
        val snapshot = createSnapshot(
            category = "electronics",
            brand = null,
            color = null,
            title = "X",
            description = null,
            priceEstimate = null,
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedQuestions.size).isAtMost(5)
    }

    // ========================================================================
    // Suggested Bullets Tests
    // ========================================================================

    @Test
    fun `known attributes appear in bullets`() {
        val snapshot = createSnapshot(
            brand = "Sony",
            color = "Black",
            condition = "Like New",
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedBullets).contains("Brand: Sony")
        assertThat(result.suggestedBullets).contains("Color: Black")
        assertThat(result.suggestedBullets).contains("Condition: Like New")
    }

    @Test
    fun `bullets include category-specific templates`() {
        val snapshot = createSnapshot(category = "electronics", condition = null)
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedBullets.any { it.contains("Functionality") }).isTrue()
        assertThat(result.suggestedBullets.any { it.contains("Included") }).isTrue()
    }

    @Test
    fun `bullets are limited and unique`() {
        val snapshot = createSnapshot(category = "electronics")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedBullets.size).isAtMost(8)
        assertThat(result.suggestedBullets).containsNoDuplicates()
    }

    // ========================================================================
    // Suggested Title Tests
    // ========================================================================

    @Test
    fun `no title suggestion when current title is good`() {
        val snapshot = createSnapshot(
            title = "Sony PlayStation 5 Console - Like New - With Controller",
            brand = "Sony",
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedTitle).isNull()
    }

    @Test
    fun `title suggestion when current title is short`() {
        val snapshot = createSnapshot(
            title = "PS5",
            brand = "Sony",
            category = "gaming console",
            color = "White",
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedTitle).isNotNull()
        assertThat(result.suggestedTitle).contains("Sony")
    }

    @Test
    fun `title suggestion includes key attributes`() {
        val snapshot = createSnapshot(
            title = "Shoes",
            brand = "Nike",
            category = "shoes",
            size = "10",
            color = "Red",
        )
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedTitle).isNotNull()
        assertThat(result.suggestedTitle).contains("Nike")
    }

    // ========================================================================
    // Description Template Tests
    // ========================================================================

    @Test
    fun `description template contains item name`() {
        val snapshot = createSnapshot(title = "Vintage Camera")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDescriptionTemplate).contains("Vintage Camera")
    }

    @Test
    fun `electronics template contains relevant sections`() {
        val snapshot = createSnapshot(category = "electronics")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDescriptionTemplate).contains("SPECIFICATIONS")
        assertThat(result.suggestedDescriptionTemplate).contains("CONDITION")
        assertThat(result.suggestedDescriptionTemplate).contains("INCLUDED")
    }

    @Test
    fun `fashion template contains relevant sections`() {
        val snapshot = createSnapshot(category = "clothing")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDescriptionTemplate).contains("DETAILS")
        assertThat(result.suggestedDescriptionTemplate).contains("FIT NOTES")
    }

    @Test
    fun `furniture template contains dimensions section`() {
        val snapshot = createSnapshot(category = "furniture")
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.suggestedDescriptionTemplate).contains("DIMENSIONS")
        assertThat(result.suggestedDescriptionTemplate).contains("PICKUP/SHIPPING")
    }

    // ========================================================================
    // Edge Cases Tests
    // ========================================================================

    @Test
    fun `null list returns null`() {
        val result = engine.generateSuggestions(emptyList())

        assertThat(result).isNull()
    }

    @Test
    fun `empty attributes handled gracefully`() {
        val snapshot = createSnapshot(attributes = emptyList())
        val result = engine.generateSuggestions(snapshot)

        assertThat(result.missingInfoPrompts).isNotEmpty()
        assertThat(result.suggestedQuestions).isNotEmpty()
    }

    @Test
    fun `null category uses general template`() {
        val snapshot = createSnapshot(category = null)
        val result = engine.generateSuggestions(snapshot)

        // Should still produce valid output
        assertThat(result.suggestedDefectsChecklist).isNotEmpty()
        assertThat(result.suggestedDescriptionTemplate).isNotEmpty()
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun createSnapshot(
        itemId: String = "test-item-1",
        title: String? = "Test Item Title That Is Long Enough",
        description: String? = "A description that describes the item well enough",
        category: String? = "general",
        confidence: Float? = 0.7f,
        brand: String? = null,
        color: String? = null,
        size: String? = null,
        condition: String? = null,
        model: String? = null,
        priceEstimate: Double? = 50.0,
        photosCount: Int = 3,
        attributes: List<ItemAttributeSnapshot>? = null,
    ): ItemContextSnapshot {
        val attrs = attributes ?: buildList {
            brand?.let { add(ItemAttributeSnapshot(key = "brand", value = it)) }
            color?.let { add(ItemAttributeSnapshot(key = "color", value = it)) }
            size?.let { add(ItemAttributeSnapshot(key = "size", value = it)) }
            condition?.let { add(ItemAttributeSnapshot(key = "condition", value = it)) }
            model?.let { add(ItemAttributeSnapshot(key = "model", value = it)) }
        }

        return ItemContextSnapshot(
            itemId = itemId,
            title = title,
            description = description,
            category = category,
            confidence = confidence,
            attributes = attrs,
            priceEstimate = priceEstimate,
            photosCount = photosCount,
        )
    }
}
