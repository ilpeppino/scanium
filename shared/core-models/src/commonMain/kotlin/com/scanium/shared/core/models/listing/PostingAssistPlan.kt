package com.scanium.shared.core.models.listing

/**
 * Portable representation of the Posting Assist plan used by Phase D.
 */
enum class PostingStepId {
    TITLE,
    PRICE,
    CATEGORY,
    CONDITION,
    DESCRIPTION,
    ATTRIBUTES,
    PHOTOS
}

data class PostingStep(
    val id: PostingStepId,
    val label: String,
    val value: String,
    val isRequired: Boolean,
    val isComplete: Boolean
)

data class PostingAssistPlan(
    val draftId: String,
    val itemId: String,
    val profileId: ExportProfileId,
    val steps: List<PostingStep>,
    val missingRequired: List<PostingStepId>,
    val completenessScore: Int
)

object PostingAssistPlanBuilder {
    private val defaultOrder = listOf(
        PostingStepId.TITLE,
        PostingStepId.PRICE,
        PostingStepId.CATEGORY,
        PostingStepId.CONDITION,
        PostingStepId.DESCRIPTION,
        PostingStepId.ATTRIBUTES,
        PostingStepId.PHOTOS
    )

    fun build(
        draft: ListingDraft,
        profile: ExportProfileDefinition
    ): PostingAssistPlan {
        val values = ListingDraftFormatter.formattedValues(draft, profile)
        val requiredSteps = requiredSteps(profile)
        val steps = buildSteps(draft, profile, values, requiredSteps)
        val missingRequired = steps.filter { it.isRequired && !it.isComplete }.map { it.id }
        val requiredCount = steps.count { it.isRequired }.coerceAtLeast(1)
        val completeness = ((requiredCount - missingRequired.size).toDouble() / requiredCount.toDouble() * 100).toInt()

        return PostingAssistPlan(
            draftId = draft.id,
            itemId = draft.itemId,
            profileId = profile.id,
            steps = steps,
            missingRequired = missingRequired,
            completenessScore = completeness
        )
    }

    fun selectNextStep(plan: PostingAssistPlan): PostingStep {
        return plan.steps.firstOrNull { it.isRequired && !it.isComplete }
            ?: plan.steps.first()
    }

    private fun buildSteps(
        draft: ListingDraft,
        profile: ExportProfileDefinition,
        values: Map<ExportFieldKey, String>,
        requiredSteps: Set<PostingStepId>
    ): List<PostingStep> {
        val orderedIds = resolveOrder(profile)
        return orderedIds.mapNotNull { stepId ->
            when (stepId) {
                PostingStepId.TITLE -> buildTitleStep(values, requiredSteps.contains(stepId))
                PostingStepId.PRICE -> buildPriceStep(draft, values, requiredSteps.contains(stepId))
                PostingStepId.CATEGORY -> buildCategoryStep(draft, values, requiredSteps.contains(stepId))
                PostingStepId.CONDITION -> buildConditionStep(draft, values, requiredSteps.contains(stepId))
                PostingStepId.DESCRIPTION -> buildDescriptionStep(values, requiredSteps.contains(stepId))
                PostingStepId.ATTRIBUTES -> buildAttributesStep(draft, profile, values, requiredSteps.contains(stepId))
                PostingStepId.PHOTOS -> buildPhotosStep(draft, requiredSteps.contains(stepId))
            }
        }
    }

    private fun resolveOrder(profile: ExportProfileDefinition): List<PostingStepId> {
        val profileOrder = profile.fieldOrdering.mapNotNull { field ->
            when (field) {
                ExportFieldKey.TITLE -> PostingStepId.TITLE
                ExportFieldKey.PRICE -> PostingStepId.PRICE
                ExportFieldKey.CATEGORY -> PostingStepId.CATEGORY
                ExportFieldKey.CONDITION -> PostingStepId.CONDITION
                ExportFieldKey.DESCRIPTION -> PostingStepId.DESCRIPTION
                ExportFieldKey.BRAND, ExportFieldKey.MODEL, ExportFieldKey.COLOR -> PostingStepId.ATTRIBUTES
                ExportFieldKey.PHOTOS -> PostingStepId.PHOTOS
            }
        }
        val ordered = mutableListOf<PostingStepId>()
        ordered += PostingStepId.TITLE
        profileOrder.forEach { id -> if (!ordered.contains(id)) ordered += id }
        defaultOrder.forEach { id -> if (!ordered.contains(id)) ordered += id }
        return ordered
    }

    private fun requiredSteps(profile: ExportProfileDefinition): Set<PostingStepId> {
        return profile.requiredFields.mapNotNull { field ->
            when (field) {
                ExportFieldKey.TITLE -> PostingStepId.TITLE
                ExportFieldKey.PRICE -> PostingStepId.PRICE
                ExportFieldKey.CATEGORY -> PostingStepId.CATEGORY
                ExportFieldKey.CONDITION -> PostingStepId.CONDITION
                ExportFieldKey.DESCRIPTION -> PostingStepId.DESCRIPTION
                ExportFieldKey.BRAND, ExportFieldKey.MODEL, ExportFieldKey.COLOR -> PostingStepId.ATTRIBUTES
                ExportFieldKey.PHOTOS -> PostingStepId.PHOTOS
            }
        }.toSet()
    }

    private fun buildTitleStep(values: Map<ExportFieldKey, String>, isRequired: Boolean): PostingStep {
        val value = values[ExportFieldKey.TITLE].orEmpty()
        return PostingStep(
            id = PostingStepId.TITLE,
            label = ExportFieldKey.TITLE.defaultLabel,
            value = value,
            isRequired = isRequired,
            isComplete = value.isNotBlank()
        )
    }

    private fun buildPriceStep(
        draft: ListingDraft,
        values: Map<ExportFieldKey, String>,
        isRequired: Boolean
    ): PostingStep {
        val priceValue = values[ExportFieldKey.PRICE].orEmpty()
        val isComplete = (draft.price.value ?: 0.0) > 0.0
        return PostingStep(
            id = PostingStepId.PRICE,
            label = ExportFieldKey.PRICE.defaultLabel,
            value = priceValue,
            isRequired = isRequired,
            isComplete = isComplete
        )
    }

    private fun buildCategoryStep(
        draft: ListingDraft,
        values: Map<ExportFieldKey, String>,
        isRequired: Boolean
    ): PostingStep {
        val value = values[ExportFieldKey.CATEGORY].orEmpty()
        val isComplete = !draft.fields[DraftFieldKey.CATEGORY]?.value.isNullOrBlank()
        return PostingStep(
            id = PostingStepId.CATEGORY,
            label = ExportFieldKey.CATEGORY.defaultLabel,
            value = value,
            isRequired = isRequired,
            isComplete = isComplete
        )
    }

    private fun buildConditionStep(
        draft: ListingDraft,
        values: Map<ExportFieldKey, String>,
        isRequired: Boolean
    ): PostingStep {
        val value = values[ExportFieldKey.CONDITION].orEmpty()
        val isComplete = !draft.fields[DraftFieldKey.CONDITION]?.value.isNullOrBlank()
        return PostingStep(
            id = PostingStepId.CONDITION,
            label = ExportFieldKey.CONDITION.defaultLabel,
            value = value,
            isRequired = isRequired,
            isComplete = isComplete
        )
    }

    private fun buildDescriptionStep(
        values: Map<ExportFieldKey, String>,
        isRequired: Boolean
    ): PostingStep {
        val value = values[ExportFieldKey.DESCRIPTION].orEmpty()
        return PostingStep(
            id = PostingStepId.DESCRIPTION,
            label = ExportFieldKey.DESCRIPTION.defaultLabel,
            value = value,
            isRequired = isRequired,
            isComplete = value.isNotBlank()
        )
    }

    private fun buildAttributesStep(
        draft: ListingDraft,
        profile: ExportProfileDefinition,
        values: Map<ExportFieldKey, String>,
        isRequired: Boolean
    ): PostingStep {
        val attributeKeys = attributeOrdering(profile)
        val lines = attributeKeys.mapNotNull { key ->
            val line = ListingDraftFormatter.formatFieldLine(key, values, profile)
            line.takeIf { it.isNotBlank() }
        }
        val value = lines.joinToString("\n")
        val isComplete = listOf(
            draft.fields[DraftFieldKey.BRAND]?.value,
            draft.fields[DraftFieldKey.MODEL]?.value,
            draft.fields[DraftFieldKey.COLOR]?.value
        ).any { !it.isNullOrBlank() }

        return PostingStep(
            id = PostingStepId.ATTRIBUTES,
            label = "Attributes",
            value = value,
            isRequired = isRequired,
            isComplete = if (isRequired) isComplete else value.isNotBlank()
        )
    }

    private fun buildPhotosStep(draft: ListingDraft, isRequired: Boolean): PostingStep {
        val isComplete = draft.photos.isNotEmpty()
        return PostingStep(
            id = PostingStepId.PHOTOS,
            label = ExportFieldKey.PHOTOS.defaultLabel,
            value = "Use Share to attach photos",
            isRequired = isRequired,
            isComplete = isComplete
        )
    }

    private fun attributeOrdering(profile: ExportProfileDefinition): List<ExportFieldKey> {
        val preferred = profile.fieldOrdering.filter { key ->
            key == ExportFieldKey.BRAND || key == ExportFieldKey.MODEL || key == ExportFieldKey.COLOR
        }
        val base = listOf(ExportFieldKey.BRAND, ExportFieldKey.MODEL, ExportFieldKey.COLOR)
        val ordered = mutableListOf<ExportFieldKey>()
        preferred.forEach { key -> if (!ordered.contains(key)) ordered += key }
        base.forEach { key -> if (!ordered.contains(key)) ordered += key }
        return ordered
    }
}
