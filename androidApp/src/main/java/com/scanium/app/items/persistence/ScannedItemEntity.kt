package com.scanium.app.items.persistence

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scanium.app.items.ItemCondition
import com.scanium.app.items.ItemListingStatus
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
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
    )
}

private data class ImageFields(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

private fun ImageRef?.toImageFields(): ImageFields? {
    return when (this) {
        is ImageRef.Bytes -> ImageFields(bytes = bytes, mimeType = mimeType, width = width, height = height)
        is ImageRef.CacheKey -> null // CacheKey cannot be persisted directly, would need resolution first
        null -> null
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
            val attrJson = JSONObject().apply {
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
            val attr = ItemAttribute(
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
