package com.scanium.app.selling.assistant

import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.EvidenceBullet
import com.scanium.app.model.ItemContextSnapshot

class LocalAssistantHelper(
    private val templateSelector: (String?) -> LocalTemplatePack = LocalTemplatePacks::selectPack,
) {
    fun buildResponse(
        items: List<ItemContextSnapshot>,
        userMessage: String,
        failure: AssistantBackendFailure? = null,
    ): AssistantResponse {
        val primary =
            items.firstOrNull()
                ?: return AssistantResponse(
                    content = "Add an item so I can provide offline listing guidance.",
                    confidenceTier = ConfidenceTier.LOW,
                )

        val normalizedQuestion = userMessage.lowercase()
        val pack = templateSelector(primary.category)

        val response =
            when {
                normalizedQuestion.contains("category") || normalizedQuestion.contains("what is it") -> {
                    buildCategoryResponse(primary)
                }

                normalizedQuestion.contains("price") || normalizedQuestion.contains("worth") || normalizedQuestion.contains("estimate") -> {
                    buildPriceResponse(primary)
                }

                normalizedQuestion.contains("brand") || normalizedQuestion.contains("model") || normalizedQuestion.contains("color") -> {
                    buildAttributeResponse(primary, normalizedQuestion)
                }

                normalizedQuestion.contains("checklist") || normalizedQuestion.contains("missing") -> {
                    buildChecklistResponse(primary, pack)
                }

                normalizedQuestion.contains("template") || normalizedQuestion.contains("description") -> {
                    buildTemplateResponse(primary, pack)
                }

                else -> {
                    buildOverviewResponse(primary, pack)
                }
            }

        val suggestedNextPhoto = buildNextPhotoSuggestion(primary, pack, normalizedQuestion)

        return response
            .copy(
                suggestedNextPhoto = suggestedNextPhoto,
                actions = response.actions + buildNextPhotoAction(suggestedNextPhoto),
            ).withFailureMetadata(failure)
    }

    private fun buildCategoryResponse(snapshot: ItemContextSnapshot): AssistantResponse {
        val category = snapshot.category
        return if (category.isNullOrBlank()) {
            AssistantResponse(
                content = "I don't have a category yet. Add more details or enable image analysis when you're online.",
                confidenceTier = ConfidenceTier.LOW,
            )
        } else {
            val tier = confidenceToTier(snapshot.confidence)
            AssistantResponse(
                content = "The draft category is \"$category\".",
                confidenceTier = tier,
                evidence =
                    listOf(
                        EvidenceBullet(type = "draft", text = "Category from item context."),
                    ),
            )
        }
    }

    private fun buildPriceResponse(snapshot: ItemContextSnapshot): AssistantResponse {
        val estimate = snapshot.priceEstimate
        return if (estimate != null && estimate > 0.0) {
            val low = (estimate * 0.85).toInt()
            val high = (estimate * 1.15).toInt().coerceAtLeast(low + 1)
            AssistantResponse(
                content =
                    "Offline estimate: roughly €$low–€$high based on the current draft. " +
                        "Confirm condition and comps when online for a stronger price.",
                confidenceTier = ConfidenceTier.LOW,
                evidence = listOf(EvidenceBullet(type = "draft", text = "Local price estimate from draft.")),
            )
        } else {
            AssistantResponse(
                content =
                    "I don't have a price estimate locally yet. Add condition, brand, and more photos, " +
                        "or retry online to refine.",
                confidenceTier = ConfidenceTier.LOW,
            )
        }
    }

    private fun buildAttributeResponse(
        snapshot: ItemContextSnapshot,
        normalizedQuestion: String,
    ): AssistantResponse {
        val requestedKeys = mutableListOf<String>()
        if (normalizedQuestion.contains("brand")) requestedKeys.add("brand")
        if (normalizedQuestion.contains("model")) requestedKeys.add("model")
        if (normalizedQuestion.contains("color")) requestedKeys.add("color")

        val found =
            requestedKeys.mapNotNull { key ->
                snapshot.attributes.firstOrNull { it.key.equals(key, ignoreCase = true) }
            }

        return if (found.isNotEmpty()) {
            val summary = found.joinToString { "${it.key.replaceFirstChar { char -> char.uppercase() }}: ${it.value}" }
            val bestTier =
                found
                    .map { confidenceToTier(it.confidence) }
                    .maxByOrNull { confidenceScore(it) } ?: ConfidenceTier.MED
            AssistantResponse(
                content = summary,
                confidenceTier = bestTier,
                evidence = listOf(EvidenceBullet(type = "draft", text = "Attributes from item context.")),
            )
        } else {
            AssistantResponse(
                content =
                    "I can't confirm that offline yet. Try enabling image analysis or take a close-up " +
                        "of the logo/label when you're online.",
                confidenceTier = ConfidenceTier.LOW,
            )
        }
    }

    private fun buildChecklistResponse(
        snapshot: ItemContextSnapshot,
        pack: LocalTemplatePack,
    ): AssistantResponse {
        val checklistText = pack.missingInfoChecklist.joinToString(separator = "\n") { "• $it" }
        val actions =
            listOf(
                AssistantAction(
                    type = AssistantActionType.COPY_TEXT,
                    payload = mapOf("label" to "Checklist", "text" to checklistText),
                    label = "Copy checklist",
                ),
            )
        return AssistantResponse(
            content = "Missing info checklist for ${pack.displayName}:\n$checklistText",
            confidenceTier = ConfidenceTier.LOW,
            actions = actions,
            evidence = listOf(EvidenceBullet(type = "draft", text = "Template pack: ${pack.displayName}.")),
        )
    }

    private fun buildTemplateResponse(
        snapshot: ItemContextSnapshot,
        pack: LocalTemplatePack,
    ): AssistantResponse {
        val template = buildDescriptionTemplate(pack)
        val actions =
            listOf(
                AssistantAction(
                    type = AssistantActionType.APPLY_DRAFT_UPDATE,
                    payload = mapOf("itemId" to snapshot.itemId, "description" to template),
                    label = "Apply description",
                ),
                AssistantAction(
                    type = AssistantActionType.COPY_TEXT,
                    payload = mapOf("label" to "Description", "text" to template),
                    label = "Copy description",
                ),
            )
        return AssistantResponse(
            content = "Here's a local description template you can use:\n$template",
            confidenceTier = ConfidenceTier.LOW,
            actions = actions,
            evidence = listOf(EvidenceBullet(type = "draft", text = "Template pack: ${pack.displayName}.")),
        )
    }

    private fun buildOverviewResponse(
        snapshot: ItemContextSnapshot,
        pack: LocalTemplatePack,
    ): AssistantResponse {
        val summary = snapshot.title ?: snapshot.category ?: "this item"
        val templateHint = buildDescriptionTemplate(pack)
        return AssistantResponse(
            content =
                "Offline helper summary: I can help refine $summary. " +
                    "Here’s a quick template to start:\n$templateHint",
            confidenceTier = ConfidenceTier.LOW,
            actions =
                listOf(
                    AssistantAction(
                        type = AssistantActionType.COPY_TEXT,
                        payload = mapOf("label" to "Description", "text" to templateHint),
                        label = "Copy template",
                    ),
                ),
            evidence = listOf(EvidenceBullet(type = "draft", text = "Template pack: ${pack.displayName}.")),
        )
    }

    private fun buildNextPhotoSuggestion(
        snapshot: ItemContextSnapshot,
        pack: LocalTemplatePack,
        normalizedQuestion: String,
    ): String? {
        val missingAttributes = snapshot.attributes.isEmpty()
        val fewPhotos = snapshot.photosCount < 2
        if (!missingAttributes && !fewPhotos) return null
        if (normalizedQuestion.contains("price")) return "Capture close-ups of any wear or damage to refine pricing."
        return pack.nextPhotoSuggestions.firstOrNull()
            ?: "Take a clear close-up of labels or defects."
    }

    private fun buildNextPhotoAction(suggestion: String?): List<AssistantAction> =
        suggestion?.let {
            listOf(
                AssistantAction(
                    type = AssistantActionType.SUGGEST_NEXT_PHOTO,
                    payload = mapOf("suggestion" to it),
                    label = "Take photo",
                ),
            )
        } ?: emptyList()

    private fun buildDescriptionTemplate(pack: LocalTemplatePack): String =
        pack.descriptionSections.joinToString(separator = "\n") { section ->
            val requiredFlag = if (section.required) " (required)" else ""
            "${section.label}$requiredFlag: ${section.prompt}"
        }

    private fun confidenceToTier(confidence: Float?): ConfidenceTier =
        when {
            confidence == null -> ConfidenceTier.MED
            confidence >= 0.8f -> ConfidenceTier.HIGH
            confidence >= 0.5f -> ConfidenceTier.MED
            else -> ConfidenceTier.LOW
        }

    private fun confidenceScore(tier: ConfidenceTier): Int =
        when (tier) {
            ConfidenceTier.HIGH -> 3
            ConfidenceTier.MED -> 2
            ConfidenceTier.LOW -> 1
        }

    private fun AssistantResponse.withFailureMetadata(failure: AssistantBackendFailure?): AssistantResponse {
        val metadata = failure?.toMetadata() ?: emptyMap()
        if (metadata.isEmpty()) return this
        return copy(citationsMetadata = (citationsMetadata ?: emptyMap()) + metadata)
    }
}
