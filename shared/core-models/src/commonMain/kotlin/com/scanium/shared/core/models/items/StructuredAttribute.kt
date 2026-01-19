package com.scanium.shared.core.models.items

import kotlinx.serialization.Serializable

/**
 * Source/provenance of an attribute value.
 * Determines merge priority: USER > DETECTED > DEFAULT > UNKNOWN
 */
enum class AttributeSource {
    USER,
    DETECTED,
    DEFAULT,
    UNKNOWN,
}

/**
 * Confidence tier for attribute values.
 */
enum class StructuredAttributeConfidence {
    HIGH,
    MED,
    LOW,
    ;

    companion object {
        fun fromString(value: String): StructuredAttributeConfidence {
            return when (value.uppercase()) {
                "HIGH" -> HIGH
                "MED", "MEDIUM" -> MED
                "LOW" -> LOW
                else -> LOW
            }
        }
    }
}

/**
 * Evidence type indicating how the value was detected.
 */
enum class EvidenceType {
    OCR,
    LOGO,
    COLOR,
    LABEL,
    LLM,
    RULE,
}

/**
 * Reference to evidence supporting an attribute value.
 */
@Serializable
data class EvidenceRef(
    /** Type of evidence */
    val type: String,
    /** Raw value from detection (e.g., OCR text, logo name) */
    val rawValue: String,
    /** Optional score from detection (0-1) */
    val score: Float? = null,
    /** Optional reference to image (hash or cropId) */
    val imageRef: String? = null,
) {
    val evidenceType: EvidenceType
        get() =
            try {
                EvidenceType.valueOf(type.uppercase())
            } catch (e: Exception) {
                EvidenceType.RULE
            }
}

/**
 * A structured attribute with full provenance.
 * This is the canonical format for storing and transmitting item attributes.
 */
@Serializable
data class StructuredAttribute(
    /** Attribute key (e.g., "brand", "color") */
    val key: String,
    /** Attribute value */
    val value: String,
    /** Source/provenance of the value */
    val source: String,
    /** Confidence level */
    val confidence: String,
    /** Evidence references (for DETECTED values) */
    val evidence: List<EvidenceRef>? = null,
    /** Timestamp when this value was set/updated (epoch ms) */
    val updatedAt: Long = 0,
) {
    val sourceEnum: AttributeSource
        get() =
            try {
                AttributeSource.valueOf(source.uppercase())
            } catch (e: Exception) {
                AttributeSource.UNKNOWN
            }

    val confidenceEnum: StructuredAttributeConfidence
        get() = StructuredAttributeConfidence.fromString(confidence)

    val isUserProvided: Boolean
        get() = sourceEnum == AttributeSource.USER

    val isDetected: Boolean
        get() = sourceEnum == AttributeSource.DETECTED

    companion object {
        /**
         * Create a USER-sourced attribute.
         */
        fun fromUser(
            key: String,
            value: String,
        ): StructuredAttribute {
            return StructuredAttribute(
                key = key,
                value = value,
                source = "USER",
                confidence = "HIGH",
                updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            )
        }

        /**
         * Create a DETECTED attribute.
         */
        fun detected(
            key: String,
            value: String,
            confidence: StructuredAttributeConfidence = StructuredAttributeConfidence.MED,
            evidence: List<EvidenceRef>? = null,
        ): StructuredAttribute {
            return StructuredAttribute(
                key = key,
                value = value,
                source = "DETECTED",
                confidence = confidence.name,
                evidence = evidence,
                updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}

/**
 * A suggested addition for user review.
 * Generated when summaryTextUserEdited=true and new attributes are detected.
 */
@Serializable
data class SuggestedAddition(
    /** The attribute being suggested */
    val attribute: StructuredAttribute,
    /** Human-readable reason for the suggestion */
    val reason: String,
    /** Whether this replaces an existing value or adds a new key */
    val action: String,
    /** Existing value being replaced (if action='replace') */
    val existingValue: String? = null,
) {
    val isAdd: Boolean
        get() = action.lowercase() == "add"

    val isReplace: Boolean
        get() = action.lowercase() == "replace"
}

/**
 * Complete enrichment state for an item.
 * This is the persisted state that combines structured attributes with summary text.
 */
@Serializable
data class ItemEnrichmentStateDto(
    /** Structured attributes with provenance */
    val attributesStructured: List<StructuredAttribute> = emptyList(),
    /** Generated summary text from attributes */
    val summaryText: String = "",
    /** Whether user has manually edited the summary text */
    val summaryTextUserEdited: Boolean = false,
    /** Pending suggestions for user review */
    val suggestedAdditions: List<SuggestedAddition> = emptyList(),
    /** Timestamp of last enrichment update */
    val lastEnrichmentAt: Long? = null,
)
