package com.scanium.app.items.export.bundle

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats export bundles as structured JSON.
 *
 * Output format:
 * {
 *   "id": "item-uuid",
 *   "title": "...",
 *   "description": "...",
 *   "bullets": ["...", "..."],
 *   "category": "FASHION",
 *   "attributes": {
 *     "brand": { "value": "Nike", "confidence": 0.95, "source": "vision" },
 *     ...
 *   },
 *   "photos": ["photo1.jpg", "photo2.jpg"],
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "exportStatus": {
 *     "ready": true,
 *     "needsAi": false,
 *     "confidenceTier": "HIGH",
 *     "model": "gpt-4"
 *   }
 * }
 */
object ListingJsonFormatter {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /**
     * Format a single bundle as a JSON object.
     */
    fun formatSingle(bundle: ExportItemBundle): JSONObject =
        JSONObject().apply {
            put("id", bundle.itemId)
            put("title", bundle.title)
            put("description", bundle.description)

            // Bullets array
            if (bundle.bullets.isNotEmpty()) {
                put("bullets", JSONArray(bundle.bullets))
            }

            // Category
            put("category", bundle.category.name)
            put("categoryDisplayName", bundle.category.displayName)

            // Attributes with full structure
            put("attributes", formatAttributes(bundle))

            // Photos (relative paths for ZIP, absolute for standalone)
            // Primary photo is first, followed by additional photos (deduplicated)
            val photos = JSONArray()
            val seenPaths = mutableSetOf<String>()
            bundle.primaryPhotoUri?.let {
                photos.put(it)
                seenPaths.add(it)
            }
            bundle.photoUris.forEach { uri ->
                if (uri !in seenPaths) {
                    photos.put(uri)
                    seenPaths.add(uri)
                }
            }
            put("photos", photos)
            put("photoCount", photos.length())

            // Timestamps
            put("createdAt", isoFormat.format(Date(bundle.createdAt)))

            // Export status
            put(
                "exportStatus",
                JSONObject().apply {
                    put("ready", bundle.isReady)
                    put("needsAi", bundle.needsAi)
                    put("hasPhotos", !bundle.hasNoPhotos)
                    bundle.confidenceTier?.let { put("confidenceTier", it) }
                    bundle.exportModel?.let { put("model", it) }
                },
            )
        }

    /**
     * Format a single bundle as a JSON string.
     */
    fun formatSingleString(
        bundle: ExportItemBundle,
        indent: Int = 2,
    ): String = formatSingle(bundle).toString(indent)

    /**
     * Format multiple bundles as a JSON array.
     */
    fun formatMultiple(bundles: List<ExportItemBundle>): JSONArray =
        JSONArray().apply {
            bundles.forEach { bundle ->
                put(formatSingle(bundle))
            }
        }

    /**
     * Format multiple bundles as a JSON string.
     */
    fun formatMultipleString(
        bundles: List<ExportItemBundle>,
        indent: Int = 2,
    ): String = formatMultiple(bundles).toString(indent)

    /**
     * Format attributes as a JSON object with full metadata.
     */
    private fun formatAttributes(bundle: ExportItemBundle): JSONObject =
        JSONObject().apply {
            bundle.attributes.forEach { (key, attr) ->
                put(
                    key,
                    JSONObject().apply {
                        put("value", attr.value)
                        put("confidence", attr.confidence)
                        attr.source?.let { put("source", it) }
                    },
                )
            }
        }

    /**
     * Format attributes as a simple key-value object.
     */
    fun formatSimpleAttributes(bundle: ExportItemBundle): JSONObject =
        JSONObject().apply {
            bundle.attributes.forEach { (key, attr) ->
                put(key, attr.value)
            }
        }
}
