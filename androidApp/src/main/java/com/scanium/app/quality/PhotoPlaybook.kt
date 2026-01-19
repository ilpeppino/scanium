package com.scanium.app.quality

import com.scanium.shared.core.models.ml.ItemCategory

/**
 * Provides deterministic photo guidance based on item category and missing attributes.
 *
 * The PhotoPlaybook suggests what photo to take next to maximize attribute extraction,
 * following a category-specific shot sequence. Each shot type is designed to reveal
 * specific attributes that vision models can extract.
 */
object PhotoPlaybook {
    /**
     * A recommended photo shot with guidance.
     */
    data class PhotoShot(
        val shotType: ShotType,
        val instruction: String,
        val targetAttributes: List<String>,
        val priority: Int,
        val icon: String,
    )

    /**
     * Types of photos that reveal different attributes.
     */
    enum class ShotType(
        val displayName: String,
    ) {
        FULL_ITEM("Full Item"),
        BRAND_LABEL("Brand/Label"),
        SIZE_TAG("Size Tag"),
        DETAIL_CLOSE_UP("Detail Close-up"),
        CONDITION_SHOT("Condition"),
        MODEL_NUMBER("Model Number"),
        BACK_VIEW("Back View"),
        SCALE_REFERENCE("Scale Reference"),
        MATERIAL_TEXTURE("Material/Texture"),
        SPECS_LABEL("Specifications"),
    }

    /**
     * Guidance result with prioritized shot recommendations.
     */
    data class GuidanceResult(
        val recommendedShots: List<PhotoShot>,
        val primaryGuidance: String,
        val category: ItemCategory,
        val currentPhotoCount: Int,
        val maxRecommendedPhotos: Int,
    ) {
        val hasMoreRecommendations: Boolean
            get() = recommendedShots.isNotEmpty()

        val nextShot: PhotoShot?
            get() = recommendedShots.firstOrNull()
    }

    /**
     * Category-specific shot sequences ordered by importance.
     */
    private val categoryPlaybooks: Map<ItemCategory, List<PhotoShot>> =
        mapOf(
            ItemCategory.FASHION to
                listOf(
                    PhotoShot(
                        shotType = ShotType.FULL_ITEM,
                        instruction = "Capture the full item on a clean background",
                        targetAttributes = listOf("itemType", "color", "style", "pattern"),
                        priority = 1,
                        icon = "shirt",
                    ),
                    PhotoShot(
                        shotType = ShotType.BRAND_LABEL,
                        instruction = "Take a close-up of the brand label or logo",
                        targetAttributes = listOf("brand"),
                        priority = 2,
                        icon = "tag",
                    ),
                    PhotoShot(
                        shotType = ShotType.SIZE_TAG,
                        instruction = "Photo the size tag (usually inside collar or waistband)",
                        targetAttributes = listOf("size", "material"),
                        priority = 3,
                        icon = "ruler",
                    ),
                    PhotoShot(
                        shotType = ShotType.CONDITION_SHOT,
                        instruction = "Show any wear, stains, or damage (or pristine condition)",
                        targetAttributes = listOf("condition"),
                        priority = 4,
                        icon = "search",
                    ),
                    PhotoShot(
                        shotType = ShotType.MATERIAL_TEXTURE,
                        instruction = "Close-up of fabric texture or material composition tag",
                        targetAttributes = listOf("material", "pattern"),
                        priority = 5,
                        icon = "layers",
                    ),
                ),
            ItemCategory.ELECTRONICS to
                listOf(
                    PhotoShot(
                        shotType = ShotType.FULL_ITEM,
                        instruction = "Show the full device from the front",
                        targetAttributes = listOf("itemType", "color", "brand"),
                        priority = 1,
                        icon = "smartphone",
                    ),
                    PhotoShot(
                        shotType = ShotType.MODEL_NUMBER,
                        instruction = "Photo the model number (often on back, bottom, or settings)",
                        targetAttributes = listOf("model", "brand"),
                        priority = 2,
                        icon = "hash",
                    ),
                    PhotoShot(
                        shotType = ShotType.CONDITION_SHOT,
                        instruction = "Show screen condition and any physical damage",
                        targetAttributes = listOf("condition"),
                        priority = 3,
                        icon = "shield",
                    ),
                    PhotoShot(
                        shotType = ShotType.BACK_VIEW,
                        instruction = "Photo the back for additional markings and ports",
                        targetAttributes = listOf("model", "connectivity"),
                        priority = 4,
                        icon = "flip",
                    ),
                    PhotoShot(
                        shotType = ShotType.SPECS_LABEL,
                        instruction = "Capture any specifications label or sticker",
                        targetAttributes = listOf("storage", "connectivity", "model"),
                        priority = 5,
                        icon = "info",
                    ),
                ),
            ItemCategory.HOME_GOOD to
                listOf(
                    PhotoShot(
                        shotType = ShotType.FULL_ITEM,
                        instruction = "Capture the full item showing its shape and style",
                        targetAttributes = listOf("itemType", "color", "style"),
                        priority = 1,
                        icon = "home",
                    ),
                    PhotoShot(
                        shotType = ShotType.SCALE_REFERENCE,
                        instruction = "Include a common object for size reference",
                        targetAttributes = listOf("dimensions"),
                        priority = 2,
                        icon = "maximize",
                    ),
                    PhotoShot(
                        shotType = ShotType.BRAND_LABEL,
                        instruction = "Photo any brand markings or manufacturer labels",
                        targetAttributes = listOf("brand"),
                        priority = 3,
                        icon = "tag",
                    ),
                    PhotoShot(
                        shotType = ShotType.CONDITION_SHOT,
                        instruction = "Show any scratches, chips, or wear",
                        targetAttributes = listOf("condition"),
                        priority = 4,
                        icon = "search",
                    ),
                    PhotoShot(
                        shotType = ShotType.MATERIAL_TEXTURE,
                        instruction = "Close-up of material or construction details",
                        targetAttributes = listOf("material"),
                        priority = 5,
                        icon = "layers",
                    ),
                ),
            ItemCategory.FOOD to
                listOf(
                    PhotoShot(
                        shotType = ShotType.FULL_ITEM,
                        instruction = "Show the full product packaging",
                        targetAttributes = listOf("itemType", "brand", "flavor"),
                        priority = 1,
                        icon = "package",
                    ),
                    PhotoShot(
                        shotType = ShotType.BRAND_LABEL,
                        instruction = "Close-up of the brand and product name",
                        targetAttributes = listOf("brand", "itemType"),
                        priority = 2,
                        icon = "tag",
                    ),
                    PhotoShot(
                        shotType = ShotType.SPECS_LABEL,
                        instruction = "Photo the expiration date and size/weight",
                        targetAttributes = listOf("expiration", "size"),
                        priority = 3,
                        icon = "calendar",
                    ),
                ),
        )

    /**
     * Default playbook for categories without specific definitions.
     */
    private val defaultPlaybook =
        listOf(
            PhotoShot(
                shotType = ShotType.FULL_ITEM,
                instruction = "Capture the full item clearly",
                targetAttributes = listOf("itemType", "color"),
                priority = 1,
                icon = "camera",
            ),
            PhotoShot(
                shotType = ShotType.BRAND_LABEL,
                instruction = "Photo any brand markings or labels",
                targetAttributes = listOf("brand"),
                priority = 2,
                icon = "tag",
            ),
            PhotoShot(
                shotType = ShotType.CONDITION_SHOT,
                instruction = "Show the item's condition",
                targetAttributes = listOf("condition"),
                priority = 3,
                icon = "search",
            ),
        )

    /**
     * Get photo guidance based on category and current state.
     *
     * @param category Item category
     * @param missingAttributes List of attributes still needed
     * @param currentPhotoCount Number of photos already taken
     * @param takenShotTypes Shot types already captured
     * @return Guidance with prioritized recommendations
     */
    fun getGuidance(
        category: ItemCategory,
        missingAttributes: List<String>,
        currentPhotoCount: Int = 0,
        takenShotTypes: Set<ShotType> = emptySet(),
    ): GuidanceResult {
        val playbook = categoryPlaybooks[category] ?: defaultPlaybook

        // Filter to shots not yet taken that target missing attributes
        val relevantShots =
            playbook
                .filter { shot -> shot.shotType !in takenShotTypes }
                .filter { shot -> shot.targetAttributes.any { it in missingAttributes } }
                .sortedBy { it.priority }

        // If no missing-attribute-specific shots, suggest general remaining shots
        val recommendedShots =
            if (relevantShots.isNotEmpty()) {
                relevantShots
            } else {
                playbook.filter { it.shotType !in takenShotTypes }.sortedBy { it.priority }
            }

        val primaryGuidance =
            recommendedShots.firstOrNull()?.instruction
                ?: "All recommended photos taken. Add more if desired."

        return GuidanceResult(
            recommendedShots = recommendedShots,
            primaryGuidance = primaryGuidance,
            category = category,
            currentPhotoCount = currentPhotoCount,
            maxRecommendedPhotos = playbook.size,
        )
    }

    /**
     * Get simple text guidance for next photo.
     */
    fun getNextPhotoHint(
        category: ItemCategory,
        missingAttributes: List<String>,
        takenShotTypes: Set<ShotType> = emptySet(),
    ): String? {
        val guidance = getGuidance(category, missingAttributes, 0, takenShotTypes)
        return guidance.nextShot?.instruction
    }

    /**
     * Get all shots for a category (for UI display of complete checklist).
     */
    fun getPlaybookForCategory(category: ItemCategory): List<PhotoShot> = categoryPlaybooks[category] ?: defaultPlaybook

    /**
     * Determine which shot type best matches a set of detected attributes.
     * Used to auto-tag photos based on what was extracted.
     */
    fun inferShotType(
        category: ItemCategory,
        extractedAttributes: Set<String>,
    ): ShotType? {
        val playbook = categoryPlaybooks[category] ?: defaultPlaybook

        // Find shot type that best matches extracted attributes
        return playbook
            .maxByOrNull { shot ->
                shot.targetAttributes.count { it in extractedAttributes }
            }?.takeIf { shot ->
                shot.targetAttributes.any { it in extractedAttributes }
            }?.shotType
    }
}
