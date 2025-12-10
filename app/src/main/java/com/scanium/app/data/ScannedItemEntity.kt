package com.scanium.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import java.io.ByteArrayOutputStream

/**
 * Room entity representing a scanned item in the database.
 *
 * This entity stores all the data needed to persist ScannedItem objects,
 * including the thumbnail as compressed JPEG bytes for efficient storage.
 *
 * Design decisions:
 * - Thumbnail is stored as JPEG bytes (compressed) rather than raw bitmap
 * - Price range is stored as two separate columns (low and high)
 * - Category is stored as String enum name for compatibility
 * - BoundingBox is stored as 4 separate Float columns (can be null)
 */
@Entity(tableName = "scanned_items")
data class ScannedItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "thumbnail_bytes", typeAffinity = ColumnInfo.BLOB)
    val thumbnailBytes: ByteArray? = null,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "price_low")
    val priceLow: Double,

    @ColumnInfo(name = "price_high")
    val priceHigh: Double,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "recognized_text")
    val recognizedText: String? = null,

    @ColumnInfo(name = "barcode_value")
    val barcodeValue: String? = null,

    @ColumnInfo(name = "bbox_left")
    val bboxLeft: Float? = null,

    @ColumnInfo(name = "bbox_top")
    val bboxTop: Float? = null,

    @ColumnInfo(name = "bbox_right")
    val bboxRight: Float? = null,

    @ColumnInfo(name = "bbox_bottom")
    val bboxBottom: Float? = null,

    @ColumnInfo(name = "label_text")
    val labelText: String? = null
) {
    /**
     * Converts this entity to a ScannedItem domain model.
     * Decompresses the thumbnail bytes back to a Bitmap.
     */
    fun toScannedItem(): ScannedItem {
        val thumbnail = thumbnailBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        val boundingBox = if (bboxLeft != null && bboxTop != null && bboxRight != null && bboxBottom != null) {
            RectF(bboxLeft, bboxTop, bboxRight, bboxBottom)
        } else {
            null
        }

        return ScannedItem(
            id = id,
            thumbnail = thumbnail,
            category = ItemCategory.valueOf(category),
            priceRange = priceLow to priceHigh,
            confidence = confidence,
            timestamp = timestamp,
            recognizedText = recognizedText,
            barcodeValue = barcodeValue,
            boundingBox = boundingBox,
            labelText = labelText
        )
    }

    companion object {
        /**
         * Creates a ScannedItemEntity from a ScannedItem domain model.
         * Compresses the thumbnail to JPEG bytes for efficient storage.
         *
         * @param item The domain model to convert
         * @param compressionQuality JPEG compression quality (0-100), default 85
         */
        fun fromScannedItem(item: ScannedItem, compressionQuality: Int = 85): ScannedItemEntity {
            val thumbnailBytes = item.thumbnail?.let { bitmap ->
                compressBitmapToBytes(bitmap, compressionQuality)
            }

            return ScannedItemEntity(
                id = item.id,
                thumbnailBytes = thumbnailBytes,
                category = item.category.name,
                priceLow = item.priceRange.first,
                priceHigh = item.priceRange.second,
                confidence = item.confidence,
                timestamp = item.timestamp,
                recognizedText = item.recognizedText,
                barcodeValue = item.barcodeValue,
                bboxLeft = item.boundingBox?.left,
                bboxTop = item.boundingBox?.top,
                bboxRight = item.boundingBox?.right,
                bboxBottom = item.boundingBox?.bottom,
                labelText = item.labelText
            )
        }

        /**
         * Compresses a Bitmap to JPEG bytes.
         *
         * @param bitmap The bitmap to compress
         * @param quality JPEG compression quality (0-100)
         * @return Compressed JPEG bytes
         */
        private fun compressBitmapToBytes(bitmap: Bitmap, quality: Int): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            return stream.toByteArray()
        }
    }

    // Override equals and hashCode to handle ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScannedItemEntity

        if (id != other.id) return false
        if (thumbnailBytes != null) {
            if (other.thumbnailBytes == null) return false
            if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        } else if (other.thumbnailBytes != null) return false
        if (category != other.category) return false
        if (priceLow != other.priceLow) return false
        if (priceHigh != other.priceHigh) return false
        if (confidence != other.confidence) return false
        if (timestamp != other.timestamp) return false
        if (recognizedText != other.recognizedText) return false
        if (barcodeValue != other.barcodeValue) return false
        if (bboxLeft != other.bboxLeft) return false
        if (bboxTop != other.bboxTop) return false
        if (bboxRight != other.bboxRight) return false
        if (bboxBottom != other.bboxBottom) return false
        if (labelText != other.labelText) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (thumbnailBytes?.contentHashCode() ?: 0)
        result = 31 * result + category.hashCode()
        result = 31 * result + priceLow.hashCode()
        result = 31 * result + priceHigh.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (recognizedText?.hashCode() ?: 0)
        result = 31 * result + (barcodeValue?.hashCode() ?: 0)
        result = 31 * result + (bboxLeft?.hashCode() ?: 0)
        result = 31 * result + (bboxTop?.hashCode() ?: 0)
        result = 31 * result + (bboxRight?.hashCode() ?: 0)
        result = 31 * result + (bboxBottom?.hashCode() ?: 0)
        result = 31 * result + (labelText?.hashCode() ?: 0)
        return result
    }
}
