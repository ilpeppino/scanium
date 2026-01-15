package com.scanium.app.golden

import com.scanium.app.ml.classification.VisionAttributesResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class GoldenDatasetRegressionTest {
    private lateinit var dataset: GoldenDataset
    private lateinit var classifier: GoldenClassifierClient

    @Before
    fun setUp() {
        val loaded = GoldenDatasetLoader.loadFromEnvOrDefault()
        Assume.assumeTrue(
            "Golden dataset not found. Set SCANIUM_GOLDEN_TESTS_PATH or init submodule.",
            loaded != null,
        )
        dataset = loaded!!

        val baseUrl = GoldenClassifierConfig.baseUrl()
        val apiKey = GoldenClassifierConfig.apiKey()
        Assume.assumeTrue(
            "Cloud classifier base URL missing. Set SCANIUM_BASE_URL or SCANIUM_API_BASE_URL.",
            baseUrl.isNotBlank(),
        )
        Assume.assumeTrue(
            "Cloud classifier API key missing. Set SCANIUM_API_KEY.",
            apiKey.isNotBlank(),
        )

        classifier = GoldenClassifierClient(baseUrl = baseUrl, apiKey = apiKey)
    }

    @Test
    fun goldenClassificationMatchesSubtype() {
        val limit = if (GoldenDatasetLoader.isFullSweepEnabled()) Int.MAX_VALUE else DEFAULT_IMAGE_LIMIT

        dataset.subtypes.forEach { subtype ->
            val expectedSubtype = subtype.expected.subtypeId
            val expectedKey = normalizeKey(expectedSubtype)
            val minPositive = subtype.expected.minConfidencePositive
            val maxNegative = subtype.expected.maxConfidenceNegative

            subtype.positiveImages.take(limit).forEach { image ->
                val outcome = classifier.classify(image)
                when (outcome) {
                    is GoldenClassificationOutcome.Error -> {
                        reportClassification(
                            subtype = expectedSubtype,
                            imageName = image.name,
                            status = "FAIL",
                            predicted = null,
                            score = null,
                            missing = emptyList(),
                            note = outcome.message,
                        )
                        assertThat(outcome.message).isEmpty()
                    }
                    is GoldenClassificationOutcome.Success -> {
                        val predicted = predictedSubtype(outcome.result)
                        val predictedKey = predicted?.let { normalizeKey(it) }
                        val score = outcome.result.confidence
                        val pass =
                            if (minPositive != null) {
                                predictedKey == expectedKey || score >= minPositive
                            } else {
                                predictedKey == expectedKey
                            }

                        reportClassification(
                            subtype = expectedSubtype,
                            imageName = image.name,
                            status = if (pass) "PASS" else "FAIL",
                            predicted = predicted,
                            score = score,
                            missing = emptyList(),
                            note = null,
                        )

                        assertThat(pass).isTrue()
                    }
                }
            }

            subtype.negativeImages.take(limit).forEach { image ->
                val outcome = classifier.classify(image)
                when (outcome) {
                    is GoldenClassificationOutcome.Error -> {
                        reportClassification(
                            subtype = expectedSubtype,
                            imageName = image.name,
                            status = "FAIL",
                            predicted = null,
                            score = null,
                            missing = emptyList(),
                            note = outcome.message,
                        )
                        assertThat(outcome.message).isEmpty()
                    }
                    is GoldenClassificationOutcome.Success -> {
                        val predicted = predictedSubtype(outcome.result)
                        val predictedKey = predicted?.let { normalizeKey(it) }
                        val score = outcome.result.confidence
                        val pass =
                            if (maxNegative != null) {
                                predictedKey != expectedKey || score <= maxNegative
                            } else {
                                predictedKey != expectedKey
                            }

                        reportClassification(
                            subtype = expectedSubtype,
                            imageName = image.name,
                            status = if (pass) "PASS" else "FAIL",
                            predicted = predicted,
                            score = score,
                            missing = emptyList(),
                            note = null,
                        )

                        assertThat(pass).isTrue()
                    }
                }
            }
        }
    }

    @Test
    fun goldenAttributesMatchExpectations() {
        val limit = if (GoldenDatasetLoader.isFullSweepEnabled()) Int.MAX_VALUE else DEFAULT_IMAGE_LIMIT

        dataset.subtypes.forEach { subtype ->
            val expectedAttributes = subtype.expected.expectedAttributes
            if (expectedAttributes.isEmpty()) {
                reportClassification(
                    subtype = subtype.expected.subtypeId,
                    imageName = "N/A",
                    status = "SKIP",
                    predicted = null,
                    score = null,
                    missing = emptyList(),
                    note = "No expected attributes configured",
                )
                return@forEach
            }

            subtype.positiveImages.take(limit).forEach { image ->
                val outcome = classifier.classify(image)
                when (outcome) {
                    is GoldenClassificationOutcome.Error -> {
                        reportClassification(
                            subtype = subtype.expected.subtypeId,
                            imageName = image.name,
                            status = "FAIL",
                            predicted = null,
                            score = null,
                            missing = emptyList(),
                            note = outcome.message,
                        )
                        assertThat(outcome.message).isEmpty()
                    }
                    is GoldenClassificationOutcome.Success -> {
                        val attributeValues = buildAttributeValues(outcome.result)
                        val missing = mutableListOf<String>()
                        val skipped = mutableListOf<String>()

                        expectedAttributes.forEach { (rawKey, rule) ->
                            val key = normalizeKey(rawKey)
                            if (!SUPPORTED_ATTRIBUTE_KEYS.contains(key)) {
                                skipped.add(key)
                                return@forEach
                            }

                            val values = attributeValues[key].orEmpty().map { normalizeValue(it) }
                            if (values.isEmpty() && !hasAttributeContainer(key, outcome.result)) {
                                skipped.add("$key(no data)")
                                return@forEach
                            }
                            if (rule.presence == true && values.isEmpty()) {
                                missing.add(key)
                                return@forEach
                            }

                            if (rule.oneOf.isNotEmpty()) {
                                val expectedTokens = rule.oneOf.map { normalizeValue(it) }
                                val matched = values.any { expectedTokens.contains(it) }
                                if (!matched) {
                                    missing.add(key)
                                }
                            }

                            if (rule.containsAny.isNotEmpty()) {
                                val expectedTokens = rule.containsAny.map { normalizeValue(it) }
                                val matched = values.any { value -> expectedTokens.any { token -> value.contains(token) } }
                                if (!matched) {
                                    missing.add(key)
                                }
                            }
                        }

                        reportClassification(
                            subtype = subtype.expected.subtypeId,
                            imageName = image.name,
                            status = if (missing.isEmpty()) "PASS" else "FAIL",
                            predicted = predictedSubtype(outcome.result),
                            score = outcome.result.confidence,
                            missing = missing,
                            note = skipped.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Skipped: $it" },
                        )

                        assertThat(missing).isEmpty()
                    }
                }
            }
        }
    }

    private fun buildAttributeValues(result: GoldenClassificationResult): Map<String, List<String>> {
        val values = mutableMapOf<String, MutableList<String>>()

        fun add(key: String, value: String?) {
            if (value.isNullOrBlank()) return
            values.getOrPut(normalizeKey(key)) { mutableListOf() }.add(value)
        }

        result.attributes.forEach { (key, value) -> add(key, value) }
        result.enrichedAttributes.forEach { (key, value) -> add(key, value) }
        addVisionAttributes(values, result.visionAttributes)

        return values
    }

    private fun addVisionAttributes(
        values: MutableMap<String, MutableList<String>>,
        vision: VisionAttributesResponse?,
    ) {
        if (vision == null) return
        fun add(key: String, value: String?) {
            if (value.isNullOrBlank()) return
            values.getOrPut(normalizeKey(key)) { mutableListOf() }.add(value)
        }

        vision.logos.forEach { add("brand", it.name) }
        vision.brandCandidates.forEach { add("brand", it) }
        vision.colors.forEach { add("color", it.name) }
        vision.labels.forEach { add("form_factor", it.name) }
    }

    private fun hasAttributeContainer(
        key: String,
        result: GoldenClassificationResult,
    ): Boolean {
        val normalized = normalizeKey(key)
        val hasAttributes = result.attributes.keys.any { normalizeKey(it) == normalized }
        val hasEnriched = result.enrichedAttributes.keys.any { normalizeKey(it) == normalized }
        val hasVision = result.visionAttributes != null

        return when (normalized) {
            "brand" -> hasAttributes || hasEnriched || hasVision
            "color" -> hasAttributes || hasEnriched || hasVision
            "form_factor", "formfactor" -> hasAttributes || hasVision
            else -> false
        }
    }

    private fun predictedSubtype(result: GoldenClassificationResult): String? {
        return result.domainCategoryId
            ?: result.label?.let { slugify(it) }
    }

    private fun slugify(label: String): String {
        return label.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun normalizeKey(key: String): String {
        return key.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun normalizeValue(value: String): String {
        return value.trim().lowercase()
    }

    private fun reportClassification(
        subtype: String,
        imageName: String,
        status: String,
        predicted: String?,
        score: Float?,
        missing: List<String>,
        note: String?,
    ) {
        val details =
            buildString {
                append("GOLDEN subtype=$subtype image=$imageName result=$status")
                if (predicted != null) append(" predicted=$predicted")
                if (score != null) append(" score=${"%.3f".format(score)}")
                if (missing.isNotEmpty()) append(" missing=${missing.joinToString("|")}")
                if (!note.isNullOrBlank()) append(" note=$note")
            }
        println(details)
    }

    companion object {
        private const val DEFAULT_IMAGE_LIMIT = 3
        private val SUPPORTED_ATTRIBUTE_KEYS = setOf("brand", "color", "form_factor", "formfactor")
    }
}
