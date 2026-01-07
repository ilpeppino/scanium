package com.scanium.shared.core.models.items

import kotlinx.datetime.Clock

/**
 * Represents a photo attached to an item.
 *
 * Each item can have multiple photos:
 * - PRIMARY: The main/initial photo from capture (usually the cropped bounding box)
 * - CLOSEUP: Additional close-up photos added by the user
 * - FULL_SHOT: Reference to the full scene photo (parent image for multi-object captures)
 *
 * @param id Unique identifier for this photo
 * @param uri File path or URI to the photo (for persisted photos)
 * @param bytes In-memory byte array (for temporary/unpersisted photos)
 * @param mimeType MIME type (e.g., "image/jpeg")
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @param capturedAt Timestamp when the photo was captured
 * @param photoHash Perceptual hash for deduplication within the item
 * @param photoType Type of photo (PRIMARY, CLOSEUP, FULL_SHOT)
 */
data class ItemPhoto(
    val id: String,
    val uri: String? = null,
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val capturedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val photoHash: String? = null,
    val photoType: PhotoType = PhotoType.PRIMARY,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ItemPhoto

        if (id != other.id) return false
        if (uri != other.uri) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (mimeType != other.mimeType) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (capturedAt != other.capturedAt) return false
        if (photoHash != other.photoHash) return false
        if (photoType != other.photoType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (uri?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + (photoHash?.hashCode() ?: 0)
        result = 31 * result + photoType.hashCode()
        return result
    }
}

/**
 * Type of photo attached to an item.
 */
enum class PhotoType {
    /**
     * Main/initial photo from capture.
     * For multi-object captures, this is the cropped bounding box.
     */
    PRIMARY,

    /**
     * Additional close-up photo added by the user.
     */
    CLOSEUP,

    /**
     * Reference to the full scene photo.
     * Used for multi-object captures to link back to the parent image.
     */
    FULL_SHOT,
}
