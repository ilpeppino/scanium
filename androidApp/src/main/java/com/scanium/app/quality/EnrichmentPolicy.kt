package com.scanium.app.quality

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.LayerState
import com.scanium.shared.core.models.ml.ItemCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy engine for controlling enrichment operations.
 *
 * Implements cost controls:
 * - Per-item budget (max enrichment calls per item)
 * - Daily budget (max total enrichment calls per day)
 * - Photo deduplication (skip similar photos)
 * - Completeness gating (skip if already complete enough)
 *
 * This prevents runaway API costs while ensuring quality.
 */
@Singleton
class EnrichmentPolicy
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "EnrichmentPolicy"
            private const val PREFS_NAME = "enrichment_policy"
            private const val KEY_DAILY_COUNT = "daily_count"
            private const val KEY_DAILY_DATE = "daily_date"
            private const val KEY_ITEM_PREFIX = "item_count_"

            // Default limits
            const val DEFAULT_MAX_PER_ITEM = 3
            const val DEFAULT_MAX_DAILY = 100
            const val DEFAULT_COMPLETENESS_THRESHOLD = 85
            const val DEFAULT_SIMILARITY_THRESHOLD = 0.9f
        }

        private val prefs: SharedPreferences by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        /**
         * Policy configuration.
         */
        data class PolicyConfig(
            val maxEnrichmentPerItem: Int = DEFAULT_MAX_PER_ITEM,
            val maxDailyEnrichment: Int = DEFAULT_MAX_DAILY,
            val completenessThreshold: Int = DEFAULT_COMPLETENESS_THRESHOLD,
            val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
            val skipIfComplete: Boolean = true,
            val skipIfRecentlyEnriched: Boolean = true,
            val recentEnrichmentWindowMs: Long = 5 * 60 * 1000, // 5 minutes
        )

        /**
         * Enrichment decision result.
         */
        data class EnrichmentDecision(
            val shouldEnrich: Boolean,
            val reason: DecisionReason,
            val remainingItemBudget: Int,
            val remainingDailyBudget: Int,
        )

        /**
         * Reasons for enrichment decisions.
         */
        enum class DecisionReason {
            PROCEED,
            ITEM_BUDGET_EXCEEDED,
            DAILY_BUDGET_EXCEEDED,
            ALREADY_COMPLETE,
            RECENTLY_ENRICHED,
            SIMILAR_PHOTO_EXISTS,
            LAYER_ALREADY_COMPLETE,
        }

        /**
         * Check if enrichment should proceed for an item.
         *
         * @param itemId Unique item identifier
         * @param category Item category for completeness evaluation
         * @param attributes Current attributes
         * @param enrichmentStatus Current enrichment layer status
         * @param lastEnrichedAt Timestamp of last enrichment (null if never)
         * @param config Policy configuration
         * @return Decision with reason and remaining budgets
         */
        fun shouldEnrich(
            itemId: String,
            category: ItemCategory,
            attributes: Map<String, ItemAttribute>,
            enrichmentStatus: EnrichmentLayerStatus?,
            lastEnrichedAt: Long? = null,
            config: PolicyConfig = PolicyConfig(),
        ): EnrichmentDecision {
            // Check daily budget
            val dailyCount = getDailyCount()
            if (dailyCount >= config.maxDailyEnrichment) {
                Log.w(TAG, "Daily budget exceeded: $dailyCount/${config.maxDailyEnrichment}")
                return EnrichmentDecision(
                    shouldEnrich = false,
                    reason = DecisionReason.DAILY_BUDGET_EXCEEDED,
                    remainingItemBudget = getRemainingItemBudget(itemId, config),
                    remainingDailyBudget = 0,
                )
            }

            // Check per-item budget
            val itemCount = getItemCount(itemId)
            if (itemCount >= config.maxEnrichmentPerItem) {
                Log.w(TAG, "Item budget exceeded for $itemId: $itemCount/${config.maxEnrichmentPerItem}")
                return EnrichmentDecision(
                    shouldEnrich = false,
                    reason = DecisionReason.ITEM_BUDGET_EXCEEDED,
                    remainingItemBudget = 0,
                    remainingDailyBudget = config.maxDailyEnrichment - dailyCount,
                )
            }

            // Check if already complete enough
            if (config.skipIfComplete) {
                val completeness = AttributeCompletenessEvaluator.evaluate(category, attributes)
                if (completeness.score >= config.completenessThreshold) {
                    Log.d(TAG, "Item $itemId already complete: ${completeness.score}%")
                    return EnrichmentDecision(
                        shouldEnrich = false,
                        reason = DecisionReason.ALREADY_COMPLETE,
                        remainingItemBudget = config.maxEnrichmentPerItem - itemCount,
                        remainingDailyBudget = config.maxDailyEnrichment - dailyCount,
                    )
                }
            }

            // Check if recently enriched
            if (config.skipIfRecentlyEnriched && lastEnrichedAt != null) {
                val timeSinceEnrichment = System.currentTimeMillis() - lastEnrichedAt
                if (timeSinceEnrichment < config.recentEnrichmentWindowMs) {
                    Log.d(TAG, "Item $itemId recently enriched ${timeSinceEnrichment}ms ago")
                    return EnrichmentDecision(
                        shouldEnrich = false,
                        reason = DecisionReason.RECENTLY_ENRICHED,
                        remainingItemBudget = config.maxEnrichmentPerItem - itemCount,
                        remainingDailyBudget = config.maxDailyEnrichment - dailyCount,
                    )
                }
            }

            // Check if enrichment layer already completed
            if (enrichmentStatus?.layerC == LayerState.COMPLETED) {
                Log.d(TAG, "Item $itemId Layer C already completed")
                return EnrichmentDecision(
                    shouldEnrich = false,
                    reason = DecisionReason.LAYER_ALREADY_COMPLETE,
                    remainingItemBudget = config.maxEnrichmentPerItem - itemCount,
                    remainingDailyBudget = config.maxDailyEnrichment - dailyCount,
                )
            }

            return EnrichmentDecision(
                shouldEnrich = true,
                reason = DecisionReason.PROCEED,
                remainingItemBudget = config.maxEnrichmentPerItem - itemCount - 1,
                remainingDailyBudget = config.maxDailyEnrichment - dailyCount - 1,
            )
        }

        /**
         * Record an enrichment operation for budget tracking.
         */
        fun recordEnrichment(itemId: String) {
            incrementDailyCount()
            incrementItemCount(itemId)
            Log.d(TAG, "Recorded enrichment for $itemId. Daily: ${getDailyCount()}, Item: ${getItemCount(itemId)}")
        }

        /**
         * Check if a photo hash is similar to existing photos (for deduplication).
         *
         * @param newHash Perceptual hash of new photo
         * @param existingHashes Hashes of existing photos
         * @param threshold Similarity threshold (0-1, higher = more similar)
         * @return True if a similar photo exists
         */
        fun hasSimilarPhoto(
            newHash: String,
            existingHashes: List<String>,
            threshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
        ): Boolean {
            if (newHash.isBlank() || existingHashes.isEmpty()) return false

            for (existingHash in existingHashes) {
                val similarity = calculateHashSimilarity(newHash, existingHash)
                if (similarity >= threshold) {
                    Log.d(TAG, "Similar photo found: ${(similarity * 100).toInt()}% match")
                    return true
                }
            }
            return false
        }

        /**
         * Calculate similarity between two perceptual hashes.
         * Uses Hamming distance for binary hashes.
         */
        private fun calculateHashSimilarity(
            hash1: String,
            hash2: String,
        ): Float {
            if (hash1.length != hash2.length) return 0f

            val totalBits = hash1.length
            var matchingBits = 0

            for (i in hash1.indices) {
                if (hash1[i] == hash2[i]) {
                    matchingBits++
                }
            }

            return matchingBits.toFloat() / totalBits
        }

        /**
         * Get remaining item budget.
         */
        fun getRemainingItemBudget(
            itemId: String,
            config: PolicyConfig = PolicyConfig(),
        ): Int {
            val used = getItemCount(itemId)
            return (config.maxEnrichmentPerItem - used).coerceAtLeast(0)
        }

        /**
         * Get remaining daily budget.
         */
        fun getRemainingDailyBudget(config: PolicyConfig = PolicyConfig()): Int {
            val used = getDailyCount()
            return (config.maxDailyEnrichment - used).coerceAtLeast(0)
        }

        /**
         * Reset daily count (called at midnight or manually).
         */
        fun resetDailyCount() {
            prefs
                .edit()
                .putInt(KEY_DAILY_COUNT, 0)
                .putString(KEY_DAILY_DATE, LocalDate.now().toString())
                .apply()
            Log.d(TAG, "Daily count reset")
        }

        /**
         * Reset item count for a specific item.
         */
        fun resetItemCount(itemId: String) {
            prefs
                .edit()
                .remove("$KEY_ITEM_PREFIX$itemId")
                .apply()
        }

        // Private helpers

        private fun getDailyCount(): Int {
            checkAndResetDailyIfNeeded()
            return prefs.getInt(KEY_DAILY_COUNT, 0)
        }

        private fun incrementDailyCount() {
            checkAndResetDailyIfNeeded()
            val current = prefs.getInt(KEY_DAILY_COUNT, 0)
            prefs.edit().putInt(KEY_DAILY_COUNT, current + 1).apply()
        }

        private fun getItemCount(itemId: String): Int = prefs.getInt("$KEY_ITEM_PREFIX$itemId", 0)

        private fun incrementItemCount(itemId: String) {
            val current = getItemCount(itemId)
            prefs.edit().putInt("$KEY_ITEM_PREFIX$itemId", current + 1).apply()
        }

        private fun checkAndResetDailyIfNeeded() {
            val savedDate = prefs.getString(KEY_DAILY_DATE, null)
            val today = LocalDate.now().toString()

            if (savedDate != today) {
                prefs
                    .edit()
                    .putInt(KEY_DAILY_COUNT, 0)
                    .putString(KEY_DAILY_DATE, today)
                    .apply()
            }
        }
    }

/**
 * Extension to check if enrichment is allowed based on policy.
 */
fun EnrichmentPolicy.EnrichmentDecision.allowed(): Boolean = shouldEnrich
