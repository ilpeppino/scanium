package com.scanium.app.selling.assistant.usecases

import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ListingDraft
import com.scanium.app.model.ItemContextSnapshot
import org.junit.Test

/**
 * Unit tests for AssistantUseCases.
 *
 * Part of ARCH-001: Tests for extracted pure domain functions.
 */
class AssistantUseCasesTest {

    // ==================== computeSuggestedQuestions tests ====================

    @Test
    fun `computeSuggestedQuestions returns default when no snapshots`() {
        val result = AssistantUseCases.computeSuggestedQuestions(emptyList())
        assertThat(result).isEqualTo(AssistantUseCases.defaultSuggestions())
    }

    @Test
    fun `computeSuggestedQuestions returns at most 3 questions`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Short", // Short title triggers suggestion
            description = "", // Missing description triggers suggestion
            category = "electronics", // Category triggers specific suggestions
            confidence = 0.9f,
            attributes = emptyList(),
        )
        val result = AssistantUseCases.computeSuggestedQuestions(listOf(snapshot))
        assertThat(result.size).isAtMost(3)
    }

    @Test
    fun `computeSuggestedQuestions includes electronics-specific questions for electronics category`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "iPhone 12 Pro",
            description = "A smartphone",
            category = "electronics",
            confidence = 0.9f,
            attributes = emptyList(), // No brand attribute
        )

        // Run multiple times to account for shuffling
        val allSuggestions = mutableSetOf<String>()
        repeat(20) {
            allSuggestions.addAll(AssistantUseCases.computeSuggestedQuestions(listOf(snapshot)))
        }

        // Should include electronics-specific suggestions
        val electronicsQuestions = listOf(
            "What brand and model is this?",
            "What's the storage capacity?",
            "Does it power on? Any screen issues?",
            "Are all accessories included?",
            "Any scratches or dents?",
        )
        assertThat(allSuggestions.intersect(electronicsQuestions.toSet())).isNotEmpty()
    }

    @Test
    fun `computeSuggestedQuestions includes furniture-specific questions`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Oak Dining Table",
            description = "Solid wood table",
            category = "furniture",
            confidence = 0.9f,
            attributes = emptyList(),
        )

        val allSuggestions = mutableSetOf<String>()
        repeat(20) {
            allSuggestions.addAll(AssistantUseCases.computeSuggestedQuestions(listOf(snapshot)))
        }

        val furnitureQuestions = listOf(
            "What are the dimensions (H x W x D)?",
            "What material is it made of?",
            "Any scratches, stains, or wear?",
            "Is assembly required?",
        )
        assertThat(allSuggestions.intersect(furnitureQuestions.toSet())).isNotEmpty()
    }

    @Test
    fun `computeSuggestedQuestions adds title suggestion for short titles`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Item", // Very short
            description = "A detailed description that is long enough",
            category = null,
            confidence = 0.9f,
            attributes = emptyList(),
        )

        val allSuggestions = mutableSetOf<String>()
        repeat(20) {
            allSuggestions.addAll(AssistantUseCases.computeSuggestedQuestions(listOf(snapshot)))
        }

        assertThat(allSuggestions).contains("Suggest a better title")
    }

    @Test
    fun `computeSuggestedQuestions adds description suggestion when missing`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "A Proper Long Enough Title",
            description = "", // Missing description
            category = null,
            confidence = 0.9f,
            attributes = emptyList(),
        )

        val allSuggestions = mutableSetOf<String>()
        repeat(20) {
            allSuggestions.addAll(AssistantUseCases.computeSuggestedQuestions(listOf(snapshot)))
        }

        assertThat(allSuggestions).contains("Help me write a description")
    }

    @Test
    fun `computeSuggestedQuestions adds price suggestion when missing`() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "A Proper Long Enough Title",
            description = "A detailed description that is long enough to not trigger",
            category = null,
            confidence = 0.9f,
            attributes = emptyList(),
            priceEstimate = null, // No price
        )

        val allSuggestions = mutableSetOf<String>()
        repeat(20) {
            allSuggestions.addAll(AssistantUseCases.computeSuggestedQuestions(listOf(snapshot)))
        }

        assertThat(allSuggestions).contains("What should I price this at?")
    }

    // ==================== defaultSuggestions tests ====================

    @Test
    fun `defaultSuggestions returns expected defaults`() {
        val defaults = AssistantUseCases.defaultSuggestions()
        assertThat(defaults).containsExactly(
            "Suggest a better title",
            "What details should I add?",
            "Estimate price range",
        )
    }

    // ==================== updateDraftFromPayload tests ====================

    @Test
    fun `updateDraftFromPayload updates title`() {
        val draft = createTestDraft()
        val payload = mapOf("title" to "New Title")

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.title.value).isEqualTo("New Title")
        assertThat(updated.title.confidence).isEqualTo(1f)
        assertThat(updated.title.source).isEqualTo(DraftProvenance.USER_EDITED)
    }

    @Test
    fun `updateDraftFromPayload updates description`() {
        val draft = createTestDraft()
        val payload = mapOf("description" to "New Description")

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.description.value).isEqualTo("New Description")
        assertThat(updated.description.confidence).isEqualTo(1f)
        assertThat(updated.description.source).isEqualTo(DraftProvenance.USER_EDITED)
    }

    @Test
    fun `updateDraftFromPayload updates price`() {
        val draft = createTestDraft()
        val payload = mapOf("price" to "99.99")

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.price.value).isEqualTo(99.99)
        assertThat(updated.price.confidence).isEqualTo(1f)
        assertThat(updated.price.source).isEqualTo(DraftProvenance.USER_EDITED)
    }

    @Test
    fun `updateDraftFromPayload ignores invalid price`() {
        val draft = createTestDraft()
        val payload = mapOf("price" to "not-a-number")

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.price.value).isEqualTo(draft.price.value) // Unchanged
    }

    @Test
    fun `updateDraftFromPayload updates multiple fields`() {
        val draft = createTestDraft()
        val payload = mapOf(
            "title" to "Updated Title",
            "description" to "Updated Description",
            "price" to "150.00",
        )

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.title.value).isEqualTo("Updated Title")
        assertThat(updated.description.value).isEqualTo("Updated Description")
        assertThat(updated.price.value).isEqualTo(150.0)
    }

    @Test
    fun `updateDraftFromPayload updates updatedAt timestamp`() {
        val draft = createTestDraft()
        val originalUpdatedAt = draft.updatedAt
        val payload = mapOf("title" to "New Title")

        // Small delay to ensure timestamp changes
        Thread.sleep(5)

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.updatedAt).isGreaterThan(originalUpdatedAt)
    }

    @Test
    fun `updateDraftFromPayload preserves unchanged fields`() {
        val draft = createTestDraft()
        val payload = mapOf("title" to "New Title")

        val updated = AssistantUseCases.updateDraftFromPayload(draft, payload)

        assertThat(updated.id).isEqualTo(draft.id)
        assertThat(updated.itemId).isEqualTo(draft.itemId)
        assertThat(updated.profile).isEqualTo(draft.profile)
        assertThat(updated.status).isEqualTo(draft.status)
        // Description is preserved since payload only had title
        assertThat(updated.description.value).isEqualTo(draft.description.value)
    }

    // ==================== Helper functions ====================

    private fun createTestDraft(
        id: String = "draft-1",
        itemId: String = "item-1",
    ): ListingDraft = ListingDraft(
        id = id,
        itemId = itemId,
        profile = ExportProfileId.GENERIC,
        title = DraftField("Original Title", confidence = 0.5f, source = DraftProvenance.DEFAULT),
        description = DraftField("Original Description", confidence = 0.5f, source = DraftProvenance.DEFAULT),
        fields = emptyMap(),
        price = DraftField(10.0, confidence = 0.5f, source = DraftProvenance.DEFAULT),
        photos = emptyList(),
        status = DraftStatus.DRAFT,
        createdAt = System.currentTimeMillis() - 1000,
        updatedAt = System.currentTimeMillis() - 1000,
    )
}
