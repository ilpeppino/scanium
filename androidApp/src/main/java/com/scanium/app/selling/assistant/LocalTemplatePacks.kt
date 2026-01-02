package com.scanium.app.selling.assistant

data class LocalDescriptionSection(
    val id: String,
    val label: String,
    val prompt: String,
    val required: Boolean,
)

data class LocalTemplatePack(
    val packId: String,
    val displayName: String,
    val categories: List<String>,
    val descriptionSections: List<LocalDescriptionSection>,
    val missingInfoChecklist: List<String>,
    val buyerFaqSuggestions: List<String>,
    val nextPhotoSuggestions: List<String>,
)

object LocalTemplatePacks {
    private val homePack =
        LocalTemplatePack(
            packId = "home",
            displayName = "Home & Furniture",
            categories = listOf("furniture", "home", "decor", "lighting", "kitchen", "garden", "storage"),
            descriptionSections =
                listOf(
                    LocalDescriptionSection("condition", "Condition", "Describe wear, stains, or damage.", true),
                    LocalDescriptionSection("dimensions", "Dimensions", "Provide height, width, depth.", true),
                    LocalDescriptionSection("material", "Material", "List materials and finishes.", false),
                    LocalDescriptionSection("defects", "Defects", "Note scratches, chips, or discoloration.", true),
                    LocalDescriptionSection("pickup", "Pickup/Shipping", "Clarify pickup vs shipping.", true),
                ),
            missingInfoChecklist =
                listOf(
                    "Exact dimensions (H x W x D)",
                    "Material and finish",
                    "Visible wear or damage",
                    "Assembly required?",
                    "Pickup/shipping options",
                ),
            buyerFaqSuggestions =
                listOf(
                    "Is pickup required or can you ship?",
                    "Any stains, scratches, or repairs?",
                    "How old is the piece?",
                    "Is assembly required?",
                    "Are there matching items?",
                ),
            nextPhotoSuggestions =
                listOf(
                    "Take a full-frame photo showing the entire item.",
                    "Capture close-ups of corners, legs, and any wear.",
                ),
        )

    private val electronicsPack =
        LocalTemplatePack(
            packId = "electronics",
            displayName = "Electronics",
            categories = listOf("electronics", "computer", "phone", "camera", "audio", "gaming"),
            descriptionSections =
                listOf(
                    LocalDescriptionSection("condition", "Condition", "Note cosmetic and functional condition.", true),
                    LocalDescriptionSection("functionality", "Functionality", "Confirm power-on and tests.", true),
                    LocalDescriptionSection("specs", "Key Specs", "List storage, model, size, etc.", false),
                    LocalDescriptionSection("accessories", "Included Accessories", "List charger/cables/manuals.", true),
                    LocalDescriptionSection("defects", "Defects", "Mention scratches or dead pixels.", true),
                ),
            missingInfoChecklist =
                listOf(
                    "Does it power on and work?",
                    "Exact model number",
                    "Battery health / runtime",
                    "Accessories included",
                    "Any cosmetic defects",
                ),
            buyerFaqSuggestions =
                listOf(
                    "Can it be tested before purchase?",
                    "How long have you used it?",
                    "Is the battery original?",
                    "Does it include the original box?",
                    "Any repairs or replacements?",
                ),
            nextPhotoSuggestions =
                listOf(
                    "Photograph the model label and ports.",
                    "Take a close-up of the screen or control panel.",
                ),
        )

    private val fashionPack =
        LocalTemplatePack(
            packId = "fashion",
            displayName = "Fashion & Clothing",
            categories = listOf("fashion", "clothing", "shoes", "accessories", "bags", "jewelry"),
            descriptionSections =
                listOf(
                    LocalDescriptionSection("size", "Size & Fit", "Include size tag and measurements.", true),
                    LocalDescriptionSection("condition", "Condition", "New with tags, gently used, or worn.", true),
                    LocalDescriptionSection("material", "Material", "List fabric/metal composition.", true),
                    LocalDescriptionSection("color", "Color", "Describe color accurately.", true),
                    LocalDescriptionSection("defects", "Defects", "Note stains, tears, or wear.", true),
                ),
            missingInfoChecklist =
                listOf(
                    "Size and measurements",
                    "Material composition",
                    "Brand authenticity or label",
                    "Any stains, tears, or wear",
                    "Fit notes (true to size?)",
                ),
            buyerFaqSuggestions =
                listOf(
                    "Can you share exact measurements?",
                    "Is it true to size?",
                    "Any flaws not shown in photos?",
                    "Is it smoke-free/pet-free?",
                    "Is it authentic?",
                ),
            nextPhotoSuggestions =
                listOf(
                    "Take a close-up of the brand label or tag.",
                    "Capture any wear areas like hems or soles.",
                ),
        )

    private val generalPack =
        LocalTemplatePack(
            packId = "general",
            displayName = "General",
            categories = emptyList(),
            descriptionSections =
                listOf(
                    LocalDescriptionSection("overview", "Overview", "Summarize what the item is.", true),
                    LocalDescriptionSection("condition", "Condition", "Describe the condition honestly.", true),
                    LocalDescriptionSection("features", "Key Features", "List notable features or specs.", false),
                    LocalDescriptionSection("defects", "Defects", "Mention any issues or flaws.", true),
                ),
            missingInfoChecklist =
                listOf(
                    "Brand/model (if applicable)",
                    "Condition notes",
                    "Measurements or size",
                    "Included accessories",
                    "Visible defects",
                ),
            buyerFaqSuggestions =
                listOf(
                    "Is anything missing or damaged?",
                    "What’s included?",
                    "Any repairs or modifications?",
                    "Why are you selling?",
                    "Is pickup or shipping preferred?",
                ),
            nextPhotoSuggestions =
                listOf(
                    "Take a wide photo showing the full item.",
                    "Capture close-ups of any labels or defects.",
                ),
        )

    private val packs = listOf(homePack, electronicsPack, fashionPack, generalPack)

    fun selectPack(category: String?): LocalTemplatePack {
        val normalized = category?.lowercase()?.trim().orEmpty()
        return packs.firstOrNull { pack ->
            pack.categories.any { normalized.contains(it) }
        } ?: generalPack
    }
}
