package com.scanium.app.enrichment

import kotlinx.serialization.Serializable

/**
 * Enrichment stage enum matching backend stages.
 */
enum class EnrichmentStage {
    QUEUED,
    VISION_STARTED,
    VISION_DONE,
    ATTRIBUTES_STARTED,
    ATTRIBUTES_DONE,
    DRAFT_STARTED,
    DRAFT_DONE,
    FAILED;

    val isComplete: Boolean
        get() = this == DRAFT_DONE || this == FAILED

    val isInProgress: Boolean
        get() = !isComplete

    val hasVisionResults: Boolean
        get() = ordinal >= VISION_DONE.ordinal

    val hasAttributeResults: Boolean
        get() = ordinal >= ATTRIBUTES_DONE.ordinal

    val hasDraftResults: Boolean
        get() = this == DRAFT_DONE
}

/**
 * Confidence tier for enrichment attributes.
 */
enum class EnrichmentConfidence {
    HIGH,
    MED,
    LOW
}

/**
 * Source of attribute detection.
 */
enum class EnrichmentSource {
    VISION_OCR,
    VISION_LOGO,
    VISION_LABEL,
    VISION_COLOR,
    LLM_INFERRED,
    USER
}

/**
 * A normalized attribute from enrichment.
 */
@Serializable
data class NormalizedAttribute(
    val key: String,
    val value: String,
    val confidence: String, // "HIGH", "MED", "LOW"
    val source: String, // "VISION_OCR", "VISION_LOGO", etc.
    val evidence: String? = null,
)

/**
 * Vision facts summary from enrichment.
 */
@Serializable
data class VisionFactsSummary(
    val ocrSnippets: List<String> = emptyList(),
    val logoHints: List<LogoHintSummary> = emptyList(),
    val dominantColors: List<ColorSummary> = emptyList(),
    val labelHints: List<String> = emptyList(),
)

@Serializable
data class LogoHintSummary(
    val name: String,
    val confidence: Float,
)

@Serializable
data class ColorSummary(
    val name: String,
    val hex: String,
    val pct: Float,
)

/**
 * Generated listing draft from enrichment.
 */
@Serializable
data class ListingDraft(
    val title: String,
    val description: String,
    val missingFields: List<String>? = null,
    val confidence: String, // "HIGH", "MED", "LOW"
)

/**
 * Error info for failed enrichment.
 */
@Serializable
data class EnrichmentErrorInfo(
    val code: String,
    val message: String,
    val stage: String,
    val retryable: Boolean,
)

/**
 * Timing info for enrichment stages.
 */
@Serializable
data class EnrichmentTimings(
    val vision: Long? = null,
    val attributes: Long? = null,
    val draft: Long? = null,
    val total: Long? = null,
)

/**
 * Enrichment status from backend.
 */
@Serializable
data class EnrichmentStatus(
    val requestId: String,
    val correlationId: String,
    val itemId: String,
    val stage: String,
    val visionFacts: VisionFactsSummary? = null,
    val normalizedAttributes: List<NormalizedAttribute>? = null,
    val draft: ListingDraft? = null,
    val error: EnrichmentErrorInfo? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val timings: EnrichmentTimings? = null,
) {
    val stageEnum: EnrichmentStage
        get() = try {
            EnrichmentStage.valueOf(stage)
        } catch (e: Exception) {
            EnrichmentStage.FAILED
        }

    val isComplete: Boolean
        get() = stageEnum.isComplete

    val isSuccess: Boolean
        get() = stageEnum == EnrichmentStage.DRAFT_DONE

    val isFailed: Boolean
        get() = stageEnum == EnrichmentStage.FAILED
}

/**
 * Response from POST /v1/items/enrich.
 */
@Serializable
data class EnrichSubmitResponse(
    val success: Boolean,
    val requestId: String? = null,
    val correlationId: String? = null,
    val error: EnrichmentApiError? = null,
)

/**
 * Response from GET /v1/items/enrich/status/:requestId.
 */
@Serializable
data class EnrichStatusResponse(
    val success: Boolean,
    val status: EnrichmentStatus? = null,
    val error: EnrichmentApiError? = null,
)

@Serializable
data class EnrichmentApiError(
    val code: String,
    val message: String,
    val correlationId: String? = null,
)

/**
 * Exception thrown when enrichment fails.
 */
class EnrichmentException(
    val errorCode: String,
    val userMessage: String,
    val retryable: Boolean = false,
) : Exception(userMessage)
