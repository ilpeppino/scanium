package com.scanium.core.models.image

/**
 * Portable reference to an image used across shared modules.
 *
 * Current implementation embeds the encoded bytes directly (Option A), which is safer when
 * a cross-platform cache is not yet available. A key-based indirection (Option B) can be
 * added later without breaking consumers.
 */
sealed class ImageRef {
    abstract val mimeType: String
    abstract val width: Int
    abstract val height: Int

    data class Bytes(
        val bytes: ByteArray,
        override val mimeType: String,
        override val width: Int,
        override val height: Int,
    ) : ImageRef() {
        init {
            require(bytes.isNotEmpty()) { "bytes must not be empty" }
            require(mimeType.isNotBlank()) { "mimeType must not be blank" }
            require(width > 0 && height > 0) { "width and height must be positive" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Bytes

            if (!bytes.contentEquals(other.bytes)) return false
            if (mimeType != other.mimeType) return false
            if (width != other.width) return false
            if (height != other.height) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}
