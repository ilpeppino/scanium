package com.scanium.app.ml.classification

import android.util.Log
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.shared.core.models.model.ImageRef
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Gatekeeper for cloud classification calls.
 * Enforces cooldowns, stability checks, and duplicate prevention.
 */
class CloudCallGate(
    private val isCloudMode: () -> Boolean,
    private val cooldownMs: Long = 8_000L,
) {
    companion object {
        private const val TAG = "CloudCallGate"
        private const val MIN_STABILITY_FRAMES = 5
        private const val MIN_STABILITY_DURATION_MS = 500L
    }

    private val lastCallTime = ConcurrentHashMap<String, Long>()
    private val processedHashes = ConcurrentHashMap<String, String>() // itemId -> hash

    /**
     * Checks if the item is allowed to be classified via cloud.
     */
    fun canClassify(
        item: AggregatedItem,
        thumbnail: ImageRef?,
    ): Boolean {
        if (!isCloudMode()) {
            return false
        }

        // 1. Stability Check
        // Use mergeCount as a proxy for frame stability if not explicit
        if (item.mergeCount < MIN_STABILITY_FRAMES) {
            // Check temporal stability if available
            val age = System.currentTimeMillis() - item.firstSeenTimestamp
            if (age < MIN_STABILITY_DURATION_MS) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Blocked: Unstable item ${item.aggregatedId} (merges=${item.mergeCount}, age=${age}ms)")
                }
                return false
            }
        }

        // 2. Cooldown Check
        val now = System.currentTimeMillis()
        val lastTime = lastCallTime[item.aggregatedId] ?: 0L

        // Check remote config cooldown if available via CloudClassificationConfig or similar
        // For now using the passed cooldownMs which comes from remote config in ItemsViewModel
        if (now - lastTime < cooldownMs) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Blocked: Cooldown active for ${item.aggregatedId} (${now - lastTime}ms < $cooldownMs)")
            }
            return false
        }

        // 3. Duplicate Input Check (Hash)
        if (thumbnail is ImageRef.Bytes) {
            val currentHash = computeBytesHash(thumbnail.bytes)
            val lastHash = processedHashes[item.aggregatedId]
            if (currentHash == lastHash) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Blocked: Duplicate input for ${item.aggregatedId}")
                }
                return false // Identical image, skip
            }
        }

        return true
    }

    /**
     * Records a successful classification trigger.
     */
    fun onClassificationTriggered(
        item: AggregatedItem,
        thumbnail: ImageRef?,
    ) {
        val now = System.currentTimeMillis()
        lastCallTime[item.aggregatedId] = now
        if (thumbnail is ImageRef.Bytes) {
            processedHashes[item.aggregatedId] = computeBytesHash(thumbnail.bytes)
        }
    }

    fun reset() {
        lastCallTime.clear()
        processedHashes.clear()
    }

    private fun computeBytesHash(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
