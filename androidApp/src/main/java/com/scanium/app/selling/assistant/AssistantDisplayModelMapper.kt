package com.scanium.app.selling.assistant

import com.scanium.app.copy.CustomerSafeCopyPolicy
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Maps assistant responses to structured [AssistantDisplayModel] for specialized rendering.
 *
 * Parsing strategy:
 * 1. Try JSON parsing first (look for `{"title": ...}` structure)
 * 2. Fall back to heuristic text extraction ("Title:", "Price:", bullet points)
 * 3. Return null if insufficient data → renders as plain text bubble
 */
object AssistantDisplayModelMapper {
    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Attempts to parse response text into a structured display model.
     * Returns null if the text doesn't contain sufficient structure.
     *
     * @param responseText The assistant response text to parse
     * @return AssistantDisplayModel if structured data found, null if plain text
     */
    fun parse(responseText: String): AssistantDisplayModel? {
        // Try JSON parsing first
        val jsonModel = parseJson(responseText)
        if (jsonModel != null && isValid(jsonModel)) {
            return jsonModel
        }

        // Fall back to heuristic extraction
        val heuristicModel = parseHeuristic(responseText)
        if (heuristicModel != null && isValid(heuristicModel)) {
            return heuristicModel
        }

        return null
    }

    /**
     * Sanitizes all string fields in the display model using CustomerSafeCopyPolicy.
     *
     * @param model The model to sanitize
     * @return Sanitized model with banned tokens removed
     */
    fun sanitize(model: AssistantDisplayModel): AssistantDisplayModel =
        model.copy(
            title = CustomerSafeCopyPolicy.sanitize(model.title),
            priceSuggestion =
                model.priceSuggestion?.let {
                    CustomerSafeCopyPolicy.sanitize(it)
                },
            condition =
                model.condition?.let {
                    CustomerSafeCopyPolicy.sanitize(it)
                },
            description =
                model.description?.let {
                    CustomerSafeCopyPolicy.sanitize(it)
                },
            highlights =
                model.highlights
                    .map { CustomerSafeCopyPolicy.sanitize(it) }
                    .filter { it.isNotBlank() },
            tags =
                model.tags
                    .map { CustomerSafeCopyPolicy.sanitize(it) }
                    .filter { it.isNotBlank() },
        )

    /**
     * Attempts to parse JSON structure from response text.
     * Looks for lenient JSON object with `title` field.
     */
    private fun parseJson(responseText: String): AssistantDisplayModel? {
        return try {
            // Try to find and extract a JSON object from the text
            var jsonString = extractJsonObject(responseText) ?: return null

            // Try org.json for lenient parsing
            val jsonObj = JSONObject(jsonString)

            val title =
                jsonObj.optString("title", "").takeIf { it.isNotBlank() }
                    ?: return null

            return AssistantDisplayModel(
                title = title,
                priceSuggestion =
                    jsonObj
                        .optString("price", "")
                        .takeIf { it.isNotBlank() },
                condition =
                    jsonObj
                        .optString("condition", "")
                        .takeIf { it.isNotBlank() },
                description =
                    jsonObj
                        .optString("description", "")
                        .takeIf { it.isNotBlank() },
                highlights =
                    jsonObj.optJSONArray("highlights")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            arr.optString(i).takeIf { it.isNotBlank() }
                        }
                    } ?: emptyList(),
                tags =
                    jsonObj.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            arr.optString(i).takeIf { it.isNotBlank() }
                        }
                    } ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts a JSON object string from text by finding balanced braces.
     * Tries to find a complete JSON object by counting braces.
     */
    private fun extractJsonObject(text: String): String? {
        val startIdx = text.indexOf('{')
        if (startIdx < 0) return null

        var braceCount = 0
        var inString = false

        for (i in startIdx until text.length) {
            val char = text[i]

            // Skip escaped characters
            if (i > 0 && text[i - 1] == '\\') {
                continue
            }

            when {
                char == '"' -> {
                    inString = !inString
                }

                char == '{' && !inString -> {
                    braceCount++
                }

                char == '}' && !inString -> {
                    braceCount--
                    if (braceCount == 0) {
                        return text.substring(startIdx, i + 1)
                    }
                }
            }
        }

        return null
    }

    /**
     * Heuristically extracts structured data from plain text response.
     *
     * Patterns recognized:
     * - Title: Line starting with "Title:" or wrapped in **bold**
     * - Price: Line starting with "Price:"
     * - Condition: Line starting with "Condition:"
     * - Description: Line starting with "Description:"
     * - Highlights: Lines starting with `-`, `•`, or `*`
     * - Tags: Line starting with "Tags:" (comma-separated)
     */
    private fun parseHeuristic(responseText: String): AssistantDisplayModel? {
        val lines = responseText.split("\n").map { it.trim() }

        // Extract title
        var title: String? = null
        var titleLineIndex = -1

        // Try "Title: ..." pattern first
        lines.forEachIndexed { index, line ->
            if (line.startsWith("Title:", ignoreCase = true)) {
                title =
                    line
                        .substringAfter(":")
                        .trim()
                        .takeIf { it.isNotBlank() }
                titleLineIndex = index
                return@forEachIndexed
            }
        }

        // Try bold pattern: **Title**
        if (title == null) {
            lines.forEachIndexed { index, line ->
                if (line.startsWith("**") && line.endsWith("**")) {
                    title =
                        line
                            .removeSurrounding("**")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    titleLineIndex = index
                    return@forEachIndexed
                }
            }
        }

        // If no title found, we can't construct a valid model
        if (title == null) {
            return null
        }

        // Extract other fields
        var priceSuggestion: String? = null
        var condition: String? = null
        var description: String? = null
        val highlights = mutableListOf<String>()
        val tags = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("Price:", ignoreCase = true) -> {
                    priceSuggestion =
                        line
                            .substringAfter(":")
                            .trim()
                            .takeIf { it.isNotBlank() }
                }

                line.startsWith("Condition:", ignoreCase = true) -> {
                    condition =
                        line
                            .substringAfter(":")
                            .trim()
                            .takeIf { it.isNotBlank() }
                }

                line.startsWith("Description:", ignoreCase = true) -> {
                    description =
                        line
                            .substringAfter(":")
                            .trim()
                            .takeIf { it.isNotBlank() }
                }

                line.startsWith("Tags:", ignoreCase = true) -> {
                    val tagString = line.substringAfter(":").trim()
                    if (tagString.isNotBlank()) {
                        tags.addAll(
                            tagString
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                        )
                    }
                }

                // Bullet point patterns for highlights
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") -> {
                    val highlight =
                        line
                            .removePrefix("-")
                            .removePrefix("•")
                            .removePrefix("*")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    if (highlight != null && index > titleLineIndex) {
                        highlights.add(highlight)
                    }
                }
            }
        }

        // Return model only if we have meaningful structure
        val hasStructure =
            priceSuggestion != null || condition != null ||
                description != null || highlights.isNotEmpty() || tags.isNotEmpty()

        return if (hasStructure) {
            AssistantDisplayModel(
                title = title!!,
                priceSuggestion = priceSuggestion,
                condition = condition,
                description = description,
                highlights = highlights,
                tags = tags,
            )
        } else {
            null
        }
    }

    /**
     * Checks if a model is valid for rendering.
     * At minimum, must have a non-empty title.
     */
    private fun isValid(model: AssistantDisplayModel): Boolean = model.title.isNotBlank()
}
