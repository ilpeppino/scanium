package com.scanium.app.selling.assistant.local

import com.scanium.app.model.ItemContextSnapshot

/**
 * Structured local suggestions for offline assistant fallback.
 * All fields are deterministic and generated from item context.
 */
data class LocalSuggestions(
    /** Questions the user should consider answering */
    val suggestedQuestions: List<String>,
    /** Bullet points for the listing description */
    val suggestedBullets: List<String>,
    /** A template for the description field */
    val suggestedDescriptionTemplate: String,
    /** Category-specific defects to check for */
    val suggestedDefectsChecklist: List<String>,
    /** Prompts about missing information */
    val missingInfoPrompts: List<MissingInfoPrompt>,
    /** Best photos to take next based on category */
    val suggestedPhotos: List<String>,
    /** Suggested title if current title is weak */
    val suggestedTitle: String?,
)

/**
 * A prompt about missing information with explanation.
 */
data class MissingInfoPrompt(
    val field: String,
    val prompt: String,
    val benefit: String,
)

/**
 * Lightweight, deterministic suggestion engine for offline assistant fallback.
 *
 * This engine analyzes ItemContextSnapshot data and generates actionable suggestions
 * without any ML models or network calls. All logic is rule-based and explainable.
 *
 * Part of OFFLINE-ASSIST: Provides useful guidance when online assistant is unavailable.
 */
class LocalSuggestionEngine {

    /**
     * Generate structured suggestions from item context.
     * Output is fully deterministic: same input always yields same output.
     */
    fun generateSuggestions(snapshot: ItemContextSnapshot): LocalSuggestions {
        val category = CategoryClassifier.classify(snapshot.category)
        val attributes = snapshot.attributes.associate { it.key.lowercase() to it.value }

        return LocalSuggestions(
            suggestedQuestions = buildSuggestedQuestions(snapshot, category, attributes),
            suggestedBullets = buildSuggestedBullets(snapshot, category, attributes),
            suggestedDescriptionTemplate = buildDescriptionTemplate(snapshot, category),
            suggestedDefectsChecklist = getDefectsChecklist(category),
            missingInfoPrompts = buildMissingInfoPrompts(snapshot, category, attributes),
            suggestedPhotos = getPhotoSuggestions(category, snapshot.photosCount),
            suggestedTitle = buildSuggestedTitle(snapshot, category, attributes),
        )
    }

    /**
     * Generate suggestions for multiple items, using first as primary.
     */
    fun generateSuggestions(snapshots: List<ItemContextSnapshot>): LocalSuggestions? {
        val primary = snapshots.firstOrNull() ?: return null
        return generateSuggestions(primary)
    }

    // ========================================================================
    // Suggested Questions
    // ========================================================================

    private fun buildSuggestedQuestions(
        snapshot: ItemContextSnapshot,
        category: ItemCategory,
        attributes: Map<String, String>,
    ): List<String> {
        val questions = mutableListOf<String>()

        // Missing attribute questions
        if (!attributes.containsKey("brand")) {
            questions.add("What brand is this item?")
        }
        if (!attributes.containsKey("condition")) {
            questions.add("What is the item's condition?")
        }

        // Category-specific questions
        when (category) {
            ItemCategory.ELECTRONICS -> {
                questions.add("Does it power on and function correctly?")
                questions.add("What is the storage capacity or key specs?")
                questions.add("Is the original charger included?")
            }
            ItemCategory.FASHION -> {
                if (!attributes.containsKey("size")) {
                    questions.add("What size is this item?")
                }
                questions.add("What is the fabric material?")
                questions.add("Are there any signs of wear?")
            }
            ItemCategory.HOME_FURNITURE -> {
                questions.add("What are the dimensions (H x W x D)?")
                questions.add("Is assembly required?")
                questions.add("Are pickup or shipping available?")
            }
            ItemCategory.TOYS_GAMES -> {
                questions.add("Is this item complete with all pieces?")
                questions.add("What age range is it suitable for?")
                questions.add("Does it require batteries?")
            }
            ItemCategory.SPORTS_OUTDOOR -> {
                if (!attributes.containsKey("size")) {
                    questions.add("What size is this?")
                }
                questions.add("Any damage from use?")
            }
            ItemCategory.BOOKS_MEDIA -> {
                questions.add("Is this a first edition?")
                questions.add("What is the condition of the binding?")
            }
            ItemCategory.KITCHEN -> {
                questions.add("Does it include all accessories?")
                questions.add("Any chips, cracks, or stains?")
            }
            ItemCategory.GENERAL -> {
                questions.add("What details should be added?")
                questions.add("Any defects to mention?")
            }
        }

        // Description/title quality questions
        val title = snapshot.title
        if (title.isNullOrBlank() || title.length < 15) {
            questions.add("Can you suggest a more descriptive title?")
        }
        if (snapshot.description.isNullOrBlank()) {
            questions.add("What should the description include?")
        }
        val priceEstimate = snapshot.priceEstimate
        if (priceEstimate == null || priceEstimate <= 0) {
            questions.add("What is a fair price for this item?")
        }

        return questions.distinct().take(5)
    }

    // ========================================================================
    // Suggested Bullets
    // ========================================================================

    private fun buildSuggestedBullets(
        snapshot: ItemContextSnapshot,
        category: ItemCategory,
        attributes: Map<String, String>,
    ): List<String> {
        val bullets = mutableListOf<String>()

        // Add known attributes as bullets
        attributes["brand"]?.let { bullets.add("Brand: $it") }
        attributes["model"]?.let { bullets.add("Model: $it") }
        attributes["color"]?.let { bullets.add("Color: $it") }
        attributes["size"]?.let { bullets.add("Size: $it") }
        attributes["material"]?.let { bullets.add("Material: $it") }
        attributes["condition"]?.let { bullets.add("Condition: $it") }

        // Category-specific bullet templates
        when (category) {
            ItemCategory.ELECTRONICS -> {
                if (!attributes.containsKey("condition")) {
                    bullets.add("Condition: [describe cosmetic and functional condition]")
                }
                bullets.add("Functionality: [confirm power-on and tests]")
                bullets.add("Included: [list charger, cables, manuals]")
            }
            ItemCategory.FASHION -> {
                if (!attributes.containsKey("size")) {
                    bullets.add("Size: [include measurements]")
                }
                if (!attributes.containsKey("material")) {
                    bullets.add("Material: [fabric composition]")
                }
                bullets.add("Fit: [true to size / runs small / runs large]")
            }
            ItemCategory.HOME_FURNITURE -> {
                bullets.add("Dimensions: [H x W x D in cm/inches]")
                if (!attributes.containsKey("material")) {
                    bullets.add("Material: [wood, metal, fabric, etc.]")
                }
                bullets.add("Assembly: [required / pre-assembled]")
            }
            ItemCategory.TOYS_GAMES -> {
                bullets.add("Completeness: [all pieces included / missing pieces]")
                bullets.add("Age range: [suitable for X+ years]")
                bullets.add("Box: [original box included / no box]")
            }
            ItemCategory.KITCHEN -> {
                bullets.add("Accessories: [what's included]")
                bullets.add("Condition: [any chips, cracks, or stains]")
            }
            else -> {
                if (!attributes.containsKey("condition")) {
                    bullets.add("Condition: [describe honestly]")
                }
                bullets.add("Includes: [list what's included]")
            }
        }

        // Always add defects bullet
        bullets.add("Defects: [note any issues or 'None']")

        return bullets.distinct().take(8)
    }

    // ========================================================================
    // Description Template
    // ========================================================================

    private fun buildDescriptionTemplate(
        snapshot: ItemContextSnapshot,
        category: ItemCategory,
    ): String {
        val itemName = snapshot.title ?: snapshot.category ?: "this item"
        val sections = mutableListOf<String>()

        // Opening paragraph
        sections.add("Selling $itemName in [condition] condition.")

        // Category-specific sections
        when (category) {
            ItemCategory.ELECTRONICS -> {
                sections.add("")
                sections.add("SPECIFICATIONS:")
                sections.add("- Model: [model number]")
                sections.add("- Storage/Capacity: [specs]")
                sections.add("- Color: [color]")
                sections.add("")
                sections.add("CONDITION:")
                sections.add("- Powers on and works: [yes/no]")
                sections.add("- Screen condition: [no scratches / minor wear / etc.]")
                sections.add("- Battery health: [percentage or hours]")
                sections.add("")
                sections.add("INCLUDED:")
                sections.add("- [List all accessories]")
            }
            ItemCategory.FASHION -> {
                sections.add("")
                sections.add("DETAILS:")
                sections.add("- Size: [size with measurements]")
                sections.add("- Color: [color]")
                sections.add("- Material: [fabric composition]")
                sections.add("")
                sections.add("CONDITION:")
                sections.add("- [New with tags / Gently used / Well worn]")
                sections.add("- Flaws: [describe any stains, tears, or wear]")
                sections.add("")
                sections.add("FIT NOTES:")
                sections.add("- [True to size / Runs small / Runs large]")
            }
            ItemCategory.HOME_FURNITURE -> {
                sections.add("")
                sections.add("DIMENSIONS:")
                sections.add("- Height: [X cm / X inches]")
                sections.add("- Width: [X cm / X inches]")
                sections.add("- Depth: [X cm / X inches]")
                sections.add("")
                sections.add("DETAILS:")
                sections.add("- Material: [wood, metal, etc.]")
                sections.add("- Color/Finish: [color]")
                sections.add("- Assembly: [required / not required]")
                sections.add("")
                sections.add("CONDITION:")
                sections.add("- [Describe any scratches, stains, or wear]")
                sections.add("")
                sections.add("PICKUP/SHIPPING:")
                sections.add("- [Pickup only / Can ship / Both available]")
            }
            ItemCategory.TOYS_GAMES -> {
                sections.add("")
                sections.add("DETAILS:")
                sections.add("- Age range: [X+ years]")
                sections.add("- Complete: [Yes, all pieces / Missing pieces]")
                sections.add("- Batteries: [Included / Not included / Not required]")
                sections.add("")
                sections.add("CONDITION:")
                sections.add("- [Like new / Good / Fair]")
                sections.add("- Box: [Original box / No box]")
            }
            else -> {
                sections.add("")
                sections.add("DETAILS:")
                sections.add("- Brand: [brand if applicable]")
                sections.add("- Color: [color]")
                sections.add("- Size/Dimensions: [if applicable]")
                sections.add("")
                sections.add("CONDITION:")
                sections.add("- [Describe condition honestly]")
                sections.add("- Defects: [List any or 'None']")
                sections.add("")
                sections.add("INCLUDED:")
                sections.add("- [List what's included with the item]")
            }
        }

        // Closing
        sections.add("")
        sections.add("Feel free to message with any questions!")

        return sections.joinToString("\n")
    }

    // ========================================================================
    // Defects Checklist
    // ========================================================================

    private fun getDefectsChecklist(category: ItemCategory): List<String> {
        return when (category) {
            ItemCategory.ELECTRONICS -> listOf(
                "Scratches on screen or body",
                "Battery health/runtime issues",
                "Charging port condition",
                "Button functionality",
                "Camera lens scratches",
                "Speaker/microphone issues",
                "Screen burn-in or dead pixels",
                "Dents or cracks",
            )
            ItemCategory.FASHION -> listOf(
                "Stains or discoloration",
                "Tears or holes",
                "Missing buttons or zippers",
                "Pilling or fabric wear",
                "Stretched or misshapen areas",
                "Fading",
                "Odors",
                "Heel or sole wear (shoes)",
            )
            ItemCategory.HOME_FURNITURE -> listOf(
                "Scratches or scuffs",
                "Chips or cracks",
                "Stains or water marks",
                "Wobbly or unstable",
                "Missing hardware",
                "Fading or discoloration",
                "Odors",
                "Structural damage",
            )
            ItemCategory.KITCHEN -> listOf(
                "Chips or cracks",
                "Stains that won't clean",
                "Rust or corrosion",
                "Missing parts or accessories",
                "Scratches on non-stick surfaces",
                "Odors",
                "Lid damage",
                "Handle issues",
            )
            ItemCategory.TOYS_GAMES -> listOf(
                "Missing pieces",
                "Worn or peeling stickers",
                "Broken or cracked parts",
                "Battery compartment corrosion",
                "Faded colors",
                "Box damage",
                "Markings or writing",
                "Loose joints or parts",
            )
            ItemCategory.SPORTS_OUTDOOR -> listOf(
                "Tears or rips",
                "Worn grip or handle",
                "Rust or corrosion",
                "Fading from sun",
                "Structural cracks",
                "Missing accessories",
                "Strap or buckle damage",
                "Sole or tread wear",
            )
            ItemCategory.BOOKS_MEDIA -> listOf(
                "Cover wear or damage",
                "Spine condition",
                "Page yellowing",
                "Writing or highlighting",
                "Water damage",
                "Torn pages",
                "Missing dust jacket",
                "Disc scratches (for media)",
            )
            ItemCategory.GENERAL -> listOf(
                "Scratches or scuffs",
                "Dents or dings",
                "Discoloration or fading",
                "Odors",
                "Missing parts",
                "Functional issues",
                "Cracks or chips",
                "General wear",
            )
        }
    }

    // ========================================================================
    // Missing Info Prompts
    // ========================================================================

    private fun buildMissingInfoPrompts(
        snapshot: ItemContextSnapshot,
        category: ItemCategory,
        attributes: Map<String, String>,
    ): List<MissingInfoPrompt> {
        val prompts = mutableListOf<MissingInfoPrompt>()

        // Brand
        if (!attributes.containsKey("brand")) {
            prompts.add(
                MissingInfoPrompt(
                    field = "brand",
                    prompt = "Add brand name",
                    benefit = "Improves search ranking and buyer trust",
                ),
            )
        }

        // Color
        if (!attributes.containsKey("color")) {
            prompts.add(
                MissingInfoPrompt(
                    field = "color",
                    prompt = "Add color",
                    benefit = "Helps buyers filter search results",
                ),
            )
        }

        // Size (for apparel/shoes/sports)
        if (category in listOf(ItemCategory.FASHION, ItemCategory.SPORTS_OUTDOOR)) {
            if (!attributes.containsKey("size")) {
                prompts.add(
                    MissingInfoPrompt(
                        field = "size",
                        prompt = "Add size",
                        benefit = "Essential for apparel - buyers filter by size",
                    ),
                )
            }
        }

        // Condition
        if (!attributes.containsKey("condition")) {
            prompts.add(
                MissingInfoPrompt(
                    field = "condition",
                    prompt = "Add condition",
                    benefit = "Sets buyer expectations and justifies price",
                ),
            )
        }

        // Photos
        if (snapshot.photosCount < 2) {
            prompts.add(
                MissingInfoPrompt(
                    field = "photos",
                    prompt = "Add more photos (${snapshot.photosCount}/4+ recommended)",
                    benefit = "Multiple angles increase buyer confidence",
                ),
            )
        } else if (snapshot.photosCount < 4) {
            prompts.add(
                MissingInfoPrompt(
                    field = "photos",
                    prompt = "Consider adding more photos (${snapshot.photosCount}/4+ recommended)",
                    benefit = "Show labels, serial numbers, or close-ups of details",
                ),
            )
        }

        // Title length
        val title = snapshot.title
        if (title.isNullOrBlank()) {
            prompts.add(
                MissingInfoPrompt(
                    field = "title",
                    prompt = "Add a title",
                    benefit = "Titles are essential for search visibility",
                ),
            )
        } else if (title.length < 15) {
            prompts.add(
                MissingInfoPrompt(
                    field = "title",
                    prompt = "Improve title (currently ${title.length} chars)",
                    benefit = "Descriptive titles (40+ chars) get more views",
                ),
            )
        }

        // Description
        if (snapshot.description.isNullOrBlank()) {
            prompts.add(
                MissingInfoPrompt(
                    field = "description",
                    prompt = "Add description",
                    benefit = "Describes condition, includes, and answers buyer questions",
                ),
            )
        }

        // Price
        val priceEstimate = snapshot.priceEstimate
        if (priceEstimate == null || priceEstimate <= 0) {
            prompts.add(
                MissingInfoPrompt(
                    field = "price",
                    prompt = "Set a price",
                    benefit = "Required to list the item",
                ),
            )
        }

        // Category-specific prompts
        when (category) {
            ItemCategory.ELECTRONICS -> {
                if (!attributes.containsKey("model")) {
                    prompts.add(
                        MissingInfoPrompt(
                            field = "model",
                            prompt = "Add model number",
                            benefit = "Buyers search by specific model",
                        ),
                    )
                }
            }
            ItemCategory.HOME_FURNITURE -> {
                if (!attributes.containsKey("dimensions")) {
                    prompts.add(
                        MissingInfoPrompt(
                            field = "dimensions",
                            prompt = "Add dimensions",
                            benefit = "Buyers need to know if it fits their space",
                        ),
                    )
                }
                if (!attributes.containsKey("material")) {
                    prompts.add(
                        MissingInfoPrompt(
                            field = "material",
                            prompt = "Add material",
                            benefit = "Helps buyers assess quality and care requirements",
                        ),
                    )
                }
            }
            else -> { /* No additional prompts */ }
        }

        return prompts.take(6)
    }

    // ========================================================================
    // Photo Suggestions
    // ========================================================================

    private fun getPhotoSuggestions(category: ItemCategory, currentCount: Int): List<String> {
        val suggestions = when (category) {
            ItemCategory.ELECTRONICS -> listOf(
                "Screen powered on (to show it works)",
                "All ports and buttons",
                "Model/serial number label",
                "Back and sides",
                "Included accessories laid out",
                "Close-up of any scratches or wear",
            )
            ItemCategory.FASHION -> listOf(
                "Full item front view",
                "Back view",
                "Size/care label close-up",
                "Brand label or tag",
                "Close-up of fabric texture",
                "Any wear areas (hems, soles, elbows)",
                "Item laid flat with measurements",
            )
            ItemCategory.HOME_FURNITURE -> listOf(
                "Full item showing all sides",
                "Close-up of material/texture",
                "Any brand labels or markings",
                "Close-up of any wear or damage",
                "Hardware or assembly points",
                "Item in context (optional - shows scale)",
            )
            ItemCategory.TOYS_GAMES -> listOf(
                "Full set laid out",
                "Close-up of brand/age marking",
                "Box condition (if included)",
                "Battery compartment",
                "Any wear or damage",
                "Key features or accessories",
            )
            ItemCategory.KITCHEN -> listOf(
                "Full item from multiple angles",
                "Brand and model label",
                "Interior/cooking surface",
                "Lid and handles",
                "Any included accessories",
                "Close-up of any wear or marks",
            )
            ItemCategory.SPORTS_OUTDOOR -> listOf(
                "Full item front and back",
                "Size label or marking",
                "Brand label",
                "Any wear areas (grip, soles, straps)",
                "Included accessories",
                "Close-up of any damage",
            )
            ItemCategory.BOOKS_MEDIA -> listOf(
                "Front cover",
                "Back cover",
                "Spine",
                "Any damage or wear",
                "Copyright page (for edition)",
                "Disc condition (for media)",
            )
            ItemCategory.GENERAL -> listOf(
                "Full item from front",
                "Full item from back",
                "Any labels or markings",
                "Close-up of details",
                "Any wear or damage",
            )
        }

        // Prioritize suggestions based on current photo count
        return if (currentCount < 2) {
            // Essential photos first
            suggestions.take(4)
        } else {
            // Additional detail photos
            suggestions.drop(2).take(4)
        }
    }

    // ========================================================================
    // Suggested Title
    // ========================================================================

    private fun buildSuggestedTitle(
        snapshot: ItemContextSnapshot,
        category: ItemCategory,
        attributes: Map<String, String>,
    ): String? {
        val currentTitle = snapshot.title
        // Only suggest if current title is weak
        if (!currentTitle.isNullOrBlank() && currentTitle.length >= 25) {
            return null
        }

        val parts = mutableListOf<String>()

        // Brand first
        attributes["brand"]?.let { parts.add(it) }

        // Model or category
        attributes["model"]?.let { parts.add(it) }
            ?: snapshot.category?.let { parts.add(it) }

        // Key attribute based on category
        when (category) {
            ItemCategory.FASHION -> {
                attributes["size"]?.let { parts.add("Size $it") }
                attributes["color"]?.let { parts.add(it) }
            }
            ItemCategory.ELECTRONICS -> {
                attributes["storage"]?.let { parts.add(it) }
                attributes["color"]?.let { parts.add(it) }
            }
            ItemCategory.HOME_FURNITURE -> {
                attributes["color"]?.let { parts.add(it) }
                attributes["material"]?.let { parts.add(it) }
            }
            else -> {
                attributes["color"]?.let { parts.add(it) }
            }
        }

        // Condition
        attributes["condition"]?.let { parts.add(it) }

        if (parts.isEmpty()) {
            return null
        }

        return parts.joinToString(" - ")
    }
}

/**
 * Classifies raw category strings into normalized categories.
 */
internal object CategoryClassifier {
    fun classify(rawCategory: String?): ItemCategory {
        val normalized = rawCategory?.lowercase()?.trim() ?: return ItemCategory.GENERAL

        return when {
            normalized.containsAny("electronic", "phone", "computer", "laptop", "tablet", "camera", "audio", "gaming", "console", "tv", "television") ->
                ItemCategory.ELECTRONICS
            normalized.containsAny("fashion", "clothing", "clothes", "shoes", "apparel", "dress", "shirt", "pants", "jacket", "accessories", "bags", "jewelry", "watch") ->
                ItemCategory.FASHION
            normalized.containsAny("furniture", "home", "decor", "lighting", "chair", "table", "sofa", "bed", "desk", "storage", "shelf", "cabinet") ->
                ItemCategory.HOME_FURNITURE
            normalized.containsAny("kitchen", "cookware", "appliance", "dining", "bakeware") ->
                ItemCategory.KITCHEN
            normalized.containsAny("toy", "game", "puzzle", "lego", "doll", "action figure", "board game") ->
                ItemCategory.TOYS_GAMES
            normalized.containsAny("sport", "fitness", "outdoor", "bike", "bicycle", "camping", "hiking", "exercise", "gym") ->
                ItemCategory.SPORTS_OUTDOOR
            normalized.containsAny("book", "media", "dvd", "cd", "vinyl", "record", "magazine", "comic") ->
                ItemCategory.BOOKS_MEDIA
            else -> ItemCategory.GENERAL
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }
}

/**
 * Normalized item categories for suggestion logic.
 */
enum class ItemCategory {
    ELECTRONICS,
    FASHION,
    HOME_FURNITURE,
    KITCHEN,
    TOYS_GAMES,
    SPORTS_OUTDOOR,
    BOOKS_MEDIA,
    GENERAL,
}
