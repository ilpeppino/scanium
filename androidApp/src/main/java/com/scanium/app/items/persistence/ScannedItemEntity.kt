package com.scanium.app.items.persistence

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scanium.app.items.ItemCondition
import com.scanium.app.items.ItemListingStatus
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.items.EnrichmentLayerStatus
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.items.LayerState
import com.scanium.shared.core.models.items.PhotoType
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import com.scanium.shared.core.models.items.VisionLabel
import com.scanium.shared.core.models.items.VisionLogo
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "scanned_items")
data class ScannedItemEntity(
    @PrimaryKey val id: String,
    val category: String,
    val priceLow: Double,
    val priceHigh: Double,
    val confidence: Float,
    val timestamp: Long,
    val labelText: String?,
    val recognizedText: String?,
    val barcodeValue: String?,
    val boundingBoxLeft: Float?,
    val boundingBoxTop: Float?,
    val boundingBoxRight: Float?,
    val boundingBoxBottom: Float?,
    val thumbnailBytes: ByteArray?,
    val thumbnailMimeType: String?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val thumbnailRefBytes: ByteArray?,
    val thumbnailRefMimeType: String?,
    val thumbnailRefWidth: Int?,
    val thumbnailRefHeight: Int?,
    val fullImageUri: String?,
    val fullImagePath: String?,
    val listingStatus: String,
    val listingId: String?,
    val listingUrl: String?,
    val classificationStatus: String,
    val domainCategoryId: String?,
    val classificationErrorMessage: String?,
    val classificationRequestId: String?,
    val userPriceCents: Long?,
    val condition: String?,
    val attributesJson: String?,
    val detectedAttributesJson: String?,
    val visionAttributesJson: String?,
    // New fields for multi-object scanning (v7)
    val attributesSummaryText: String?,
    val summaryTextUserEdited: Int,
    val additionalPhotosJson: String?,
    val sourcePhotoId: String?,
    val enrichmentStatusJson: String?,
    // Export Assistant fields (v8)
    val exportTitle: String?,
    val exportDescription: String?,
    val exportBulletsJson: String?,
    val exportGeneratedAt: Long?,
    val exportFromCache: Int,
    val exportModel: String?,
    val exportConfidenceTier: String?,
    // Quality Loop fields (v9)
    val completenessScore: Int,
    val missingAttributesJson: String?,
    val lastEnrichedAt: Long?,
    val capturedShotTypesJson: String?,
    val isReadyForListing: Int,
    // Sync fields (Phase E: v10)
    val serverId: String? = null,
    val needsSync: Int = 1,
    val lastSyncedAt: Long? = null,
    val syncVersion: Int = 1,
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

fun ScannedItem.toEntity(): ScannedItemEntity {
    val thumbnailData = thumbnail.toImageFields()
    val thumbnailRefData = thumbnailRef.toImageFields()
    val boundingBox = boundingBox

    return ScannedItemEntity(
        id = id,
        category = category.name,
        priceLow = priceRange.first,
        priceHigh = priceRange.second,
        confidence = confidence,
        timestamp = timestamp,
        labelText = labelText,
        recognizedText = recognizedText,
        barcodeValue = barcodeValue,
        boundingBoxLeft = boundingBox?.left,
        boundingBoxTop = boundingBox?.top,
        boundingBoxRight = boundingBox?.right,
        boundingBoxBottom = boundingBox?.bottom,
        thumbnailBytes = thumbnailData?.bytes,
        thumbnailMimeType = thumbnailData?.mimeType,
        thumbnailWidth = thumbnailData?.width,
        thumbnailHeight = thumbnailData?.height,
        thumbnailRefBytes = thumbnailRefData?.bytes,
        thumbnailRefMimeType = thumbnailRefData?.mimeType,
        thumbnailRefWidth = thumbnailRefData?.width,
        thumbnailRefHeight = thumbnailRefData?.height,
        fullImageUri = fullImageUri?.toString(),
        fullImagePath = fullImagePath,
        listingStatus = listingStatus.name,
        listingId = listingId,
        listingUrl = listingUrl,
        classificationStatus = classificationStatus,
        domainCategoryId = domainCategoryId,
        classificationErrorMessage = classificationErrorMessage,
        classificationRequestId = classificationRequestId,
        userPriceCents = userPriceCents,
        condition = condition?.name,
        attributesJson = serializeAttributes(attributes),
        detectedAttributesJson = serializeAttributes(detectedAttributes),
        visionAttributesJson = serializeVisionAttributes(visionAttributes),
        attributesSummaryText = attributesSummaryText.takeIf { it.isNotEmpty() },
        summaryTextUserEdited = if (summaryTextUserEdited) 1 else 0,
        additionalPhotosJson = serializeAdditionalPhotos(additionalPhotos),
        sourcePhotoId = sourcePhotoId,
        enrichmentStatusJson = serializeEnrichmentStatus(enrichmentStatus),
        // Export fields
        exportTitle = exportTitle,
        exportDescription = exportDescription,
        exportBulletsJson = serializeExportBullets(exportBullets),
        exportGeneratedAt = exportGeneratedAt,
        exportFromCache = if (exportFromCache) 1 else 0,
        exportModel = exportModel,
        exportConfidenceTier = exportConfidenceTier,
        // Quality Loop fields
        completenessScore = completenessScore,
        missingAttributesJson = serializeStringList(missingAttributes),
        lastEnrichedAt = lastEnrichedAt,
        capturedShotTypesJson = serializeStringList(capturedShotTypes),
        isReadyForListing = if (isReadyForListing) 1 else 0,
    )
}

fun ScannedItemEntity.toModel(): ScannedItem {
    val categoryValue =
        runCatching { ItemCategory.valueOf(category) }
            .getOrElse { ItemCategory.UNKNOWN }
    val listingStatusValue =
        runCatching { ItemListingStatus.valueOf(listingStatus) }
            .getOrElse { ItemListingStatus.NOT_LISTED }
    val conditionValue =
        condition?.let { name ->
            runCatching { ItemCondition.valueOf(name) }.getOrNull()
        }
    val boundingBoxValue =
        if (
            boundingBoxLeft != null &&
            boundingBoxTop != null &&
            boundingBoxRight != null &&
            boundingBoxBottom != null
        ) {
            NormalizedRect(
                left = boundingBoxLeft,
                top = boundingBoxTop,
                right = boundingBoxRight,
                bottom = boundingBoxBottom,
            )
        } else {
            null
        }

    val estimatedRange = PriceRange(Money(priceLow), Money(priceHigh))

    return ScannedItem(
        id = id,
        thumbnail =
            imageRefFrom(
                bytes = thumbnailBytes,
                mimeType = thumbnailMimeType,
                width = thumbnailWidth,
                height = thumbnailHeight,
            ),
        thumbnailRef =
            imageRefFrom(
                bytes = thumbnailRefBytes,
                mimeType = thumbnailRefMimeType,
                width = thumbnailRefWidth,
                height = thumbnailRefHeight,
            ),
        category = categoryValue,
        priceRange = priceLow to priceHigh,
        estimatedPriceRange = estimatedRange,
        priceEstimationStatus = PriceEstimationStatus.Ready(estimatedRange),
        confidence = confidence,
        timestamp = timestamp,
        recognizedText = recognizedText,
        barcodeValue = barcodeValue,
        boundingBox = boundingBoxValue,
        labelText = labelText,
        fullImageUri = fullImageUri?.let(Uri::parse),
        fullImagePath = fullImagePath,
        listingStatus = listingStatusValue,
        listingId = listingId,
        listingUrl = listingUrl,
        classificationStatus = classificationStatus,
        domainCategoryId = domainCategoryId,
        classificationErrorMessage = classificationErrorMessage,
        classificationRequestId = classificationRequestId,
        userPriceCents = userPriceCents,
        condition = conditionValue,
        attributes = deserializeAttributes(attributesJson),
        detectedAttributes = deserializeAttributes(detectedAttributesJson),
        visionAttributes = deserializeVisionAttributes(visionAttributesJson),
        attributesSummaryText = attributesSummaryText ?: "",
        summaryTextUserEdited = summaryTextUserEdited == 1,
        additionalPhotos = deserializeAdditionalPhotos(additionalPhotosJson),
        sourcePhotoId = sourcePhotoId,
        enrichmentStatus = deserializeEnrichmentStatus(enrichmentStatusJson),
        // Export fields
        exportTitle = exportTitle,
        exportDescription = exportDescription,
        exportBullets = deserializeExportBullets(exportBulletsJson),
        exportGeneratedAt = exportGeneratedAt,
        exportFromCache = exportFromCache == 1,
        exportModel = exportModel,
        exportConfidenceTier = exportConfidenceTier,
        // Quality Loop fields
        completenessScore = completenessScore,
        missingAttributes = deserializeStringList(missingAttributesJson),
        lastEnrichedAt = lastEnrichedAt,
        capturedShotTypes = deserializeStringList(capturedShotTypesJson),
        isReadyForListing = isReadyForListing == 1,
    )
}

private data class ImageFields(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

private fun ImageRef?.toImageFields(): ImageFields? =
    when (this) {
        is ImageRef.Bytes -> {
            ImageFields(bytes = bytes, mimeType = mimeType, width = width, height = height)
        }

        is ImageRef.CacheKey -> {
            // CRITICAL FIX: Resolve CacheKey from ThumbnailCache before persisting
            // This prevents photo placeholders after app process death (e.g., returning from share intents)
            val resolved = ThumbnailCache.get(key)
            resolved?.let {
                ImageFields(bytes = it.bytes, mimeType = it.mimeType, width = it.width, height = it.height)
            }
        }

        null -> {
            null
        }
    }

private fun imageRefFrom(
    bytes: ByteArray?,
    mimeType: String?,
    width: Int?,
    height: Int?,
): ImageRef? {
    if (bytes == null || bytes.isEmpty()) return null
    if (mimeType.isNullOrBlank()) return null
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return ImageRef.Bytes(bytes = bytes, mimeType = mimeType, width = width, height = height)
}

/**
 * Serialize attributes map to JSON string for database storage.
 *
 * Format: { "key": { "value": "...", "confidence": 0.85, "source": "ocr" }, ... }
 */
private fun serializeAttributes(attributes: Map<String, ItemAttribute>): String? {
    if (attributes.isEmpty()) return null
    return try {
        val json = JSONObject()
        for ((key, attr) in attributes) {
            val attrJson =
                JSONObject().apply {
                    put("value", attr.value)
                    put("confidence", attr.confidence.toDouble())
                    if (attr.source != null) {
                        put("source", attr.source)
                    }
                }
            json.put(key, attrJson)
        }
        json.toString()
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserialize JSON string back to attributes map.
 */
private fun deserializeAttributes(json: String?): Map<String, ItemAttribute> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val result = mutableMapOf<String, ItemAttribute>()
        val jsonObject = JSONObject(json)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val attrJson = jsonObject.getJSONObject(key)
            val attr =
                ItemAttribute(
                    value = attrJson.getString("value"),
                    confidence = attrJson.optDouble("confidence", 0.0).toFloat(),
                    source = attrJson.optString("source").takeIf { it.isNotBlank() },
                )
            result[key] = attr
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun serializeVisionAttributes(visionAttributes: VisionAttributes): String? {
    if (visionAttributes.isEmpty) return null
    return try {
        val json = JSONObject()

        visionAttributes.ocrText?.let { json.put("ocrText", it) }
        visionAttributes.itemType?.let { json.put("itemType", it) }

        if (visionAttributes.colors.isNotEmpty()) {
            val colors = JSONArray()
            visionAttributes.colors.forEach { color ->
                val colorJson = JSONObject()
                colorJson.put("name", color.name)
                colorJson.put("hex", color.hex)
                colorJson.put("score", color.score.toDouble())
                colors.put(colorJson)
            }
            json.put("colors", colors)
        }

        if (visionAttributes.logos.isNotEmpty()) {
            val logos = JSONArray()
            visionAttributes.logos.forEach { logo ->
                val logoJson = JSONObject()
                logoJson.put("name", logo.name)
                logoJson.put("score", logo.score.toDouble())
                logos.put(logoJson)
            }
            json.put("logos", logos)
        }

        if (visionAttributes.labels.isNotEmpty()) {
            val labels = JSONArray()
            visionAttributes.labels.forEach { label ->
                val labelJson = JSONObject()
                labelJson.put("name", label.name)
                labelJson.put("score", label.score.toDouble())
                labels.put(labelJson)
            }
            json.put("labels", labels)
        }

        if (visionAttributes.brandCandidates.isNotEmpty()) {
            val brands = JSONArray()
            visionAttributes.brandCandidates.forEach { brands.put(it) }
            json.put("brandCandidates", brands)
        }

        if (visionAttributes.modelCandidates.isNotEmpty()) {
            val models = JSONArray()
            visionAttributes.modelCandidates.forEach { models.put(it) }
            json.put("modelCandidates", models)
        }

        json.toString()
    } catch (e: Exception) {
        null
    }
}

private fun deserializeVisionAttributes(json: String?): VisionAttributes {
    if (json.isNullOrBlank()) return VisionAttributes.EMPTY
    return try {
        val obj = JSONObject(json)

        val colors = mutableListOf<VisionColor>()
        val colorsArray = obj.optJSONArray("colors")
        if (colorsArray != null) {
            for (i in 0 until colorsArray.length()) {
                val colorObj = colorsArray.optJSONObject(i) ?: continue
                val name = colorObj.optString("name")
                val hex = colorObj.optString("hex")
                val score = colorObj.optDouble("score", 0.0).toFloat()
                if (name.isNotBlank() && hex.isNotBlank()) {
                    colors.add(VisionColor(name = name, hex = hex, score = score))
                }
            }
        }

        val logos = mutableListOf<VisionLogo>()
        val logosArray = obj.optJSONArray("logos")
        if (logosArray != null) {
            for (i in 0 until logosArray.length()) {
                val logoObj = logosArray.optJSONObject(i) ?: continue
                val name = logoObj.optString("name")
                val score = logoObj.optDouble("score", 0.0).toFloat()
                if (name.isNotBlank()) {
                    logos.add(VisionLogo(name = name, score = score))
                }
            }
        }

        val labels = mutableListOf<VisionLabel>()
        val labelsArray = obj.optJSONArray("labels")
        if (labelsArray != null) {
            for (i in 0 until labelsArray.length()) {
                val labelObj = labelsArray.optJSONObject(i) ?: continue
                val name = labelObj.optString("name")
                val score = labelObj.optDouble("score", 0.0).toFloat()
                if (name.isNotBlank()) {
                    labels.add(VisionLabel(name = name, score = score))
                }
            }
        }

        val brandCandidates = mutableListOf<String>()
        val brandsArray = obj.optJSONArray("brandCandidates")
        if (brandsArray != null) {
            for (i in 0 until brandsArray.length()) {
                val brand = brandsArray.optString(i)
                if (brand.isNotBlank()) {
                    brandCandidates.add(brand)
                }
            }
        }

        val modelCandidates = mutableListOf<String>()
        val modelsArray = obj.optJSONArray("modelCandidates")
        if (modelsArray != null) {
            for (i in 0 until modelsArray.length()) {
                val model = modelsArray.optString(i)
                if (model.isNotBlank()) {
                    modelCandidates.add(model)
                }
            }
        }

        VisionAttributes(
            colors = colors,
            ocrText = obj.optString("ocrText").takeIf { it.isNotBlank() },
            logos = logos,
            labels = labels,
            brandCandidates = brandCandidates,
            modelCandidates = modelCandidates,
            itemType = obj.optString("itemType").takeIf { it.isNotBlank() },
        )
    } catch (e: Exception) {
        VisionAttributes.EMPTY
    }
}

/**
 * Serialize additional photos list to JSON string.
 *
 * Format: [{ "id": "...", "uri": "...", "mimeType": "...", "width": 0, "height": 0, "capturedAt": 0, "photoHash": "...", "photoType": "PRIMARY" }, ...]
 */
private fun serializeAdditionalPhotos(photos: List<ItemPhoto>): String? {
    if (photos.isEmpty()) return null
    return try {
        val jsonArray = JSONArray()
        for (photo in photos) {
            val photoJson =
                JSONObject().apply {
                    put("id", photo.id)
                    photo.uri?.let { put("uri", it) }
                    put("mimeType", photo.mimeType)
                    put("width", photo.width)
                    put("height", photo.height)
                    put("capturedAt", photo.capturedAt)
                    photo.photoHash?.let { put("photoHash", it) }
                    put("photoType", photo.photoType.name)
                }
            jsonArray.put(photoJson)
        }
        jsonArray.toString()
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserialize JSON string back to additional photos list.
 */
private fun deserializeAdditionalPhotos(json: String?): List<ItemPhoto> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val result = mutableListOf<ItemPhoto>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val photoJson = jsonArray.getJSONObject(i)
            val photoType =
                runCatching {
                    PhotoType.valueOf(photoJson.optString("photoType", "PRIMARY"))
                }.getOrElse { PhotoType.PRIMARY }

            val photo =
                ItemPhoto(
                    id = photoJson.getString("id"),
                    uri = photoJson.optString("uri").takeIf { it.isNotBlank() },
                    bytes = null, // Bytes are not persisted to JSON
                    mimeType = photoJson.optString("mimeType", "image/jpeg"),
                    width = photoJson.optInt("width", 0),
                    height = photoJson.optInt("height", 0),
                    capturedAt = photoJson.optLong("capturedAt", 0L),
                    photoHash = photoJson.optString("photoHash").takeIf { it.isNotBlank() },
                    photoType = photoType,
                )
            result.add(photo)
        }
        result
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Serialize enrichment status to JSON string.
 *
 * Format: { "layerA": "COMPLETED", "layerB": "IN_PROGRESS", "layerC": "PENDING", "lastUpdated": 12345 }
 */
private fun serializeEnrichmentStatus(status: EnrichmentLayerStatus): String? {
    // Don't persist if all layers are pending (default state)
    if (status.layerA == LayerState.PENDING &&
        status.layerB == LayerState.PENDING &&
        status.layerC == LayerState.PENDING
    ) {
        return null
    }
    return try {
        JSONObject()
            .apply {
                put("layerA", status.layerA.name)
                put("layerB", status.layerB.name)
                put("layerC", status.layerC.name)
                put("lastUpdated", status.lastUpdated)
            }.toString()
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserialize JSON string back to enrichment status.
 */
private fun deserializeEnrichmentStatus(json: String?): EnrichmentLayerStatus {
    if (json.isNullOrBlank()) return EnrichmentLayerStatus()
    return try {
        val obj = JSONObject(json)
        val layerA =
            runCatching {
                LayerState.valueOf(obj.optString("layerA", "PENDING"))
            }.getOrElse { LayerState.PENDING }
        val layerB =
            runCatching {
                LayerState.valueOf(obj.optString("layerB", "PENDING"))
            }.getOrElse { LayerState.PENDING }
        val layerC =
            runCatching {
                LayerState.valueOf(obj.optString("layerC", "PENDING"))
            }.getOrElse { LayerState.PENDING }
        val lastUpdated = obj.optLong("lastUpdated", 0L)

        EnrichmentLayerStatus(
            layerA = layerA,
            layerB = layerB,
            layerC = layerC,
            lastUpdated = lastUpdated,
        )
    } catch (e: Exception) {
        EnrichmentLayerStatus()
    }
}

/**
 * Serialize export bullets list to JSON string.
 *
 * Format: ["bullet 1", "bullet 2", ...]
 */
private fun serializeExportBullets(bullets: List<String>): String? {
    if (bullets.isEmpty()) return null
    return try {
        val jsonArray = JSONArray()
        bullets.forEach { jsonArray.put(it) }
        jsonArray.toString()
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserialize JSON string back to export bullets list.
 */
private fun deserializeExportBullets(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val result = mutableListOf<String>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val bullet = jsonArray.optString(i)
            if (bullet.isNotBlank()) {
                result.add(bullet)
            }
        }
        result
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Serialize a generic string list to JSON string.
 */
private fun serializeStringList(list: List<String>): String? {
    if (list.isEmpty()) return null
    return try {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        jsonArray.toString()
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserialize JSON string back to string list.
 */
private fun deserializeStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val result = mutableListOf<String>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optString(i)
            if (item.isNotBlank()) {
                result.add(item)
            }
        }
        result
    } catch (e: Exception) {
        emptyList()
    }
}
