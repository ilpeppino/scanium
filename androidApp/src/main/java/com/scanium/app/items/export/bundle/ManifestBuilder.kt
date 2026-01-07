package com.scanium.app.items.export.bundle

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the export-manifest.json for ZIP exports.
 *
 * The manifest provides a summary of the export:
 * - Export metadata (timestamp, version, device info)
 * - Item summary with status flags
 * - Statistics about the export
 */
object ManifestBuilder {
    private const val MANIFEST_VERSION = "1.0"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /**
     * Build manifest for a set of export bundles.
     *
     * @param result The export bundle result
     * @param exportFormat Format of the export (e.g., "zip", "text")
     * @return JSON string for export-manifest.json
     */
    fun build(
        result: ExportBundleResult,
        exportFormat: String = "zip",
    ): String {
        val manifest = JSONObject().apply {
            // Manifest metadata
            put("manifestVersion", MANIFEST_VERSION)
            put("exportFormat", exportFormat)
            put("exportedAt", isoFormat.format(Date()))

            // App info
            put("app", JSONObject().apply {
                put("name", "Scanium")
                put("platform", "Android")
                put("sdkVersion", Build.VERSION.SDK_INT)
            })

            // Export statistics
            put("statistics", JSONObject().apply {
                put("totalItems", result.totalItems)
                put("readyCount", result.readyCount)
                put("needsAiCount", result.needsAiCount)
                put("noPhotosCount", result.noPhotosCount)
                put("allReady", result.allReady)
            })

            // Items summary
            put("items", buildItemsSummary(result.bundles))
        }

        return manifest.toString(2)
    }

    /**
     * Build a summary array of items for the manifest.
     */
    private fun buildItemsSummary(bundles: List<ExportItemBundle>): JSONArray {
        return JSONArray().apply {
            bundles.forEach { bundle ->
                put(JSONObject().apply {
                    put("id", bundle.itemId)
                    put("title", bundle.title)
                    put("category", bundle.category.name)
                    put("photoCount", bundle.photoCount)
                    put("status", determineStatus(bundle))
                    put("flags", JSONArray(bundle.flags.map { it.name }))

                    // Path references for the ZIP structure
                    put("paths", JSONObject().apply {
                        put("folder", "items/${bundle.itemId}")
                        put("listingTxt", "items/${bundle.itemId}/listing.txt")
                        put("listingJson", "items/${bundle.itemId}/listing.json")
                        if (bundle.photoCount > 0) {
                            put("photos", "items/${bundle.itemId}/photos/")
                        }
                    })
                })
            }
        }
    }

    /**
     * Determine the human-readable status for an item.
     */
    private fun determineStatus(bundle: ExportItemBundle): String {
        return when {
            bundle.isReady && !bundle.hasNoPhotos -> "READY"
            bundle.isReady && bundle.hasNoPhotos -> "READY_NO_PHOTOS"
            bundle.needsAi && !bundle.hasNoPhotos -> "NEEDS_AI"
            bundle.needsAi && bundle.hasNoPhotos -> "NEEDS_AI_NO_PHOTOS"
            else -> "UNKNOWN"
        }
    }

    /**
     * Build a minimal manifest for text-only exports.
     */
    fun buildMinimal(result: ExportBundleResult): String {
        val manifest = JSONObject().apply {
            put("manifestVersion", MANIFEST_VERSION)
            put("exportFormat", "text")
            put("exportedAt", isoFormat.format(Date()))
            put("totalItems", result.totalItems)
            put("readyCount", result.readyCount)
            put("needsAiCount", result.needsAiCount)

            // Simple item list
            put("itemIds", JSONArray().apply {
                result.bundles.forEach { bundle ->
                    put(bundle.itemId)
                }
            })
        }

        return manifest.toString(2)
    }
}
