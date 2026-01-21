package com.scanium.app.classification.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Helper for preparing correction history for backend classification requests.
 * Part of the local learning overlay feature (Delta A4).
 */
object CorrectionHistoryHelper {

    /**
     * Convert recent corrections to JSON for backend classification API.
     *
     * @param corrections Recent corrections from local database
     * @param limit Maximum number of corrections to include (default: 20)
     * @return JSON string for backend, or null if no corrections
     */
    fun toBackendJson(
        corrections: List<ClassificationCorrectionEntity>,
        limit: Int = 20
    ): String? {
        if (corrections.isEmpty()) return null

        val recentCorrections = corrections
            .take(limit)
            .map { entity ->
                RecentCorrectionPayload(
                    originalCategoryId = entity.originalCategoryId ?: "unknown",
                    correctedCategoryId = entity.correctedCategoryId,
                    correctedCategoryName = entity.correctedCategoryName,
                    correctedAt = entity.correctedAt,
                    visualFingerprint = entity.visualContext // Use visual context as fingerprint
                )
            }

        return Json.encodeToString(recentCorrections)
    }

    /**
     * Recent correction payload for backend API.
     * Matches backend's RecentCorrection type.
     */
    @Serializable
    private data class RecentCorrectionPayload(
        val originalCategoryId: String,
        val correctedCategoryId: String,
        val correctedCategoryName: String,
        val correctedAt: Long,
        val visualFingerprint: String? = null
    )
}

// TODO: Wire this up in CloudClassifier or ClassificationOrchestrator:
// 1. Inject ClassificationCorrectionDao into CloudClassifier
// 2. Before calling api.classify(), fetch recent corrections:
//    val corrections = correctionDao.getRecentCorrections(limit = 20)
//    val correctionsJson = CorrectionHistoryHelper.toBackendJson(corrections)
// 3. Pass correctionsJson to api.classify(bitmap, config, correctionsJson, onAttempt)
