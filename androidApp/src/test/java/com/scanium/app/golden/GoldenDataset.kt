package com.scanium.app.golden

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExpectedRule(
    val presence: Boolean? = null,
    val oneOf: List<String> = emptyList(),
    val containsAny: List<String> = emptyList(),
    val notes: String? = null,
)

data class ExpectedSubtype(
    val subtypeId: String,
    val expectedAttributes: Map<String, ExpectedRule> = emptyMap(),
    val minConfidencePositive: Float? = null,
    val maxConfidenceNegative: Float? = null,
    val notes: String? = null,
)

data class GoldenSubtype(
    val subtypeId: String,
    val expected: ExpectedSubtype,
    val positiveImages: List<File>,
    val negativeImages: List<File>,
)

data class GoldenDataset(
    val rootDir: File,
    val subtypes: List<GoldenSubtype>,
)

object GoldenDatasetLoader {
    private const val ENV_DATASET_PATH = "SCANIUM_GOLDEN_TESTS_PATH"
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun loadFromEnvOrDefault(): GoldenDataset? {
        val root = resolveDatasetRoot() ?: return null
        return loadDataset(root)
    }

    fun isFullSweepEnabled(): Boolean {
        val env = System.getenv("SCANIUM_GOLDEN_TESTS_FULL")?.trim()
        val prop = System.getProperty("SCANIUM_GOLDEN_TESTS_FULL")?.trim()
        return env == "1" || prop == "1" || env.equals("true", ignoreCase = true) || prop.equals("true", ignoreCase = true)
    }

    private fun resolveDatasetRoot(): File? {
        val explicit = System.getenv(ENV_DATASET_PATH)?.takeIf { it.isNotBlank() }
        val baseDir = File(System.getProperty("user.dir"))

        if (explicit != null) {
            val explicitFile = File(explicit).let { if (it.isAbsolute) it else File(baseDir, explicit) }
            return resolveBySubtypeDir(explicitFile)
        }

        val defaultCandidates =
            listOf(
                File(baseDir, "external/golden-tests/scanium-golden-tests/golden_images/by_subtype"),
                File(baseDir, "external/golden-tests/scanium-golden-tests/tests/golden_images/by_subtype"),
            )

        return defaultCandidates.firstOrNull { it.isDirectory }
    }

    private fun resolveBySubtypeDir(base: File): File? {
        if (base.isDirectory && base.name == "by_subtype") return base
        val bySubtype = File(base, "by_subtype")
        if (bySubtype.isDirectory) return bySubtype
        return null
    }

    private fun loadDataset(bySubtypeDir: File): GoldenDataset {
        val subtypeDirs =
            bySubtypeDir.listFiles { file -> file.isDirectory }?.sortedBy { it.name }.orEmpty()

        val subtypes =
            subtypeDirs.map { subtypeDir ->
                val expectedFile = File(subtypeDir, "expected.json")
                val expected =
                    if (expectedFile.isFile) {
                        parseExpected(expectedFile, subtypeDir.name)
                    } else {
                        ExpectedSubtype(
                            subtypeId = subtypeDir.name,
                            expectedAttributes = emptyMap(),
                            notes = "expected.json missing",
                        )
                    }
                val positiveImages = listImages(File(subtypeDir, "positive"))
                val negativeImages = listImages(File(subtypeDir, "negative"))

                GoldenSubtype(
                    subtypeId = subtypeDir.name,
                    expected = expected,
                    positiveImages = positiveImages,
                    negativeImages = negativeImages,
                )
            }

        return GoldenDataset(
            rootDir = bySubtypeDir,
            subtypes = subtypes,
        )
    }

    private fun listImages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && isImageFile(file.name)
        }?.sortedBy { it.name }.orEmpty()
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    }

    private fun parseExpected(file: File, fallbackSubtype: String): ExpectedSubtype {
        val element = json.parseToJsonElement(file.readText()).jsonObject
        val subtypeId =
            element.stringValue("subtype")
                ?: element.stringValue("subtype_id")
                ?: fallbackSubtype

        val expectedAttributes = parseExpectedAttributes(element["expectedAttributes"])
        val minPositive = element.floatValue("minConfidencePositive") ?: element.floatValue("min_confidence_positive")
        val maxNegative = element.floatValue("maxConfidenceNegative") ?: element.floatValue("max_confidence_negative")
        val notes = element.stringValue("notes")

        return ExpectedSubtype(
            subtypeId = subtypeId,
            expectedAttributes = expectedAttributes,
            minConfidencePositive = minPositive,
            maxConfidenceNegative = maxNegative,
            notes = notes,
        )
    }

    private fun parseExpectedAttributes(element: JsonElement?): Map<String, ExpectedRule> {
        val obj = element as? JsonObject ?: return emptyMap()

        val legacyValidation = obj.stringValue("validation_type")
        val legacyAttributes = obj["attributes"] as? JsonArray
        if (legacyValidation != null && legacyAttributes != null) {
            val tokens = legacyAttributes.mapNotNull { it.jsonPrimitive.contentOrNull }
            val rule =
                when (legacyValidation) {
                    "containsAny" -> ExpectedRule(containsAny = tokens)
                    "oneOf" -> ExpectedRule(oneOf = tokens)
                    "presence" -> ExpectedRule(presence = true)
                    else -> ExpectedRule(oneOf = tokens)
                }
            return mapOf("form_factor" to rule)
        }

        return obj.mapNotNull { (key, value) ->
            val ruleObj = value as? JsonObject ?: return@mapNotNull null
            val presence = ruleObj["presence"]?.jsonPrimitive?.booleanOrNull
            val oneOf = ruleObj.stringList("oneOf") + ruleObj.stringList("one_of")
            val containsAny = ruleObj.stringList("containsAny") + ruleObj.stringList("contains_any")
            val notes = ruleObj.stringValue("notes")
            key to ExpectedRule(
                presence = presence,
                oneOf = oneOf,
                containsAny = containsAny,
                notes = notes,
            )
        }.toMap()
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.floatValue(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
}
