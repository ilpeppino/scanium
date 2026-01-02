package com.scanium.shared.core.models.listing

import com.scanium.shared.core.models.items.ScannedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostingAssistPlanBuilderTest {
    @Test
    fun planIsDeterministic() {
        val draft = ListingDraftBuilder.build(sampleItem())
        val profile = sampleProfile()

        val first = PostingAssistPlanBuilder.build(draft, profile)
        val second = PostingAssistPlanBuilder.build(draft, profile)

        assertEquals(first, second)
    }

    @Test
    fun requiredStepsReflectProfile() {
        val draft =
            ListingDraftBuilder.build(sampleItem()).copy(
                photos = emptyList(),
                price = DraftField(value = null),
            )
        val profile =
            sampleProfile().copy(
                requiredFields =
                    listOf(
                        ExportFieldKey.TITLE,
                        ExportFieldKey.PRICE,
                        ExportFieldKey.PHOTOS,
                    ),
            )

        val plan = PostingAssistPlanBuilder.build(draft, profile)

        assertTrue(plan.missingRequired.contains(PostingStepId.PRICE))
        assertTrue(plan.missingRequired.contains(PostingStepId.PHOTOS))
        val titleStep = plan.steps.first { it.id == PostingStepId.TITLE }
        assertTrue(titleStep.isRequired)
    }

    @Test
    fun optionalFieldsDoNotBreakPlan() {
        val draft =
            ListingDraftBuilder.build(sampleItem()).copy(
                fields = emptyMap(),
                photos = emptyList(),
            )
        val profile = sampleProfile().copy(requiredFields = emptyList())

        val plan = PostingAssistPlanBuilder.build(draft, profile)

        val attributes = plan.steps.first { it.id == PostingStepId.ATTRIBUTES }
        assertTrue(attributes.value.isBlank())
        assertTrue(plan.missingRequired.isEmpty())
    }

    @Test
    fun selectNextPrefersMissingRequired() {
        val draft =
            ListingDraftBuilder.build(sampleItem()).copy(
                photos = emptyList(),
                price = DraftField(value = null),
            )
        val plan = PostingAssistPlanBuilder.build(draft, sampleProfile())

        val next = PostingAssistPlanBuilder.selectNextStep(plan)

        assertEquals(PostingStepId.PRICE, next.id)
    }

    private fun sampleProfile(): ExportProfileDefinition {
        return ExportProfileDefinition(
            id = ExportProfileId("TEST"),
            displayName = "Test",
            fieldOrdering = ExportFieldKey.defaultOrdering(),
            requiredFields =
                listOf(
                    ExportFieldKey.TITLE,
                    ExportFieldKey.PRICE,
                    ExportFieldKey.CONDITION,
                    ExportFieldKey.CATEGORY,
                ),
        )
    }

    private fun sampleItem(): ScannedItem<Nothing> {
        return ScannedItem(
            id = "item-1",
            thumbnail =
                ImageRef.Bytes(
                    bytes = ByteArray(8) { it.toByte() },
                    mimeType = "image/jpeg",
                    width = 4,
                    height = 2,
                ),
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 20.0,
            confidence = 0.8f,
            timestamp = 1000L,
            labelText = "Mug",
        )
    }
}
