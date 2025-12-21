package com.scanium.shared.core.models.listing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExportProfileDefinition(
    val id: ExportProfileId,
    val displayName: String,
    val localeHint: String? = null,
    val currencyHint: String? = null,
    val titleRules: ExportTitleRules = ExportTitleRules(),
    val descriptionRules: ExportDescriptionRules = ExportDescriptionRules(),
    val fieldOrdering: List<ExportFieldKey> = ExportFieldKey.defaultOrdering(),
    val requiredFields: List<ExportFieldKey> = emptyList(),
    val optionalFieldLabels: Map<ExportFieldKey, String> = emptyMap(),
    val missingFieldPolicy: MissingFieldPolicy = MissingFieldPolicy.OMIT
)

@Serializable
data class ExportTitleRules(
    val maxLen: Int = 80,
    val includeBrandInTitle: Boolean = true,
    val includeModelInTitle: Boolean = true,
    val capitalization: TitleCapitalization = TitleCapitalization.SENTENCE_CASE
)

@Serializable
data class ExportDescriptionRules(
    val format: DescriptionFormat = DescriptionFormat.PARAGRAPH,
    val includeMeasurements: Boolean = false,
    val includeConditionLine: Boolean = true,
    val includeDisclaimerLine: Boolean = false
)

@Serializable
enum class DescriptionFormat {
    PARAGRAPH,
    BULLETS
}

@Serializable
enum class TitleCapitalization {
    NONE,
    SENTENCE_CASE,
    TITLE_CASE,
    UPPERCASE
}

@Serializable
enum class MissingFieldPolicy {
    OMIT,
    SHOW_UNKNOWN
}

@Serializable
enum class ExportFieldKey(val defaultLabel: String) {
    @SerialName("title")
    TITLE("Title"),
    @SerialName("price")
    PRICE("Price"),
    @SerialName("condition")
    CONDITION("Condition"),
    @SerialName("category")
    CATEGORY("Category"),
    @SerialName("brand")
    BRAND("Brand"),
    @SerialName("model")
    MODEL("Model"),
    @SerialName("color")
    COLOR("Color"),
    @SerialName("description")
    DESCRIPTION("Description"),
    @SerialName("photos")
    PHOTOS("Photos");

    companion object {
        fun defaultOrdering(): List<ExportFieldKey> {
            return listOf(
                PRICE,
                CONDITION,
                CATEGORY,
                BRAND,
                MODEL,
                COLOR,
                PHOTOS
            )
        }
    }
}

interface ExportProfileRepository {
    suspend fun getProfiles(): List<ExportProfileDefinition>
    suspend fun getProfile(id: ExportProfileId): ExportProfileDefinition?
    suspend fun getDefaultProfileId(): ExportProfileId
}

object ExportProfiles {
    private val genericDefinition = ExportProfileDefinition(
        id = ExportProfileId.GENERIC,
        displayName = "Generic",
        localeHint = "en_US",
        currencyHint = "EUR",
        titleRules = ExportTitleRules(),
        descriptionRules = ExportDescriptionRules(),
        fieldOrdering = ExportFieldKey.defaultOrdering(),
        requiredFields = listOf(
            ExportFieldKey.TITLE,
            ExportFieldKey.PRICE,
            ExportFieldKey.CONDITION,
            ExportFieldKey.CATEGORY,
            ExportFieldKey.PHOTOS
        ),
        optionalFieldLabels = emptyMap(),
        missingFieldPolicy = MissingFieldPolicy.OMIT
    )

    fun generic(): ExportProfileDefinition = genericDefinition
}
