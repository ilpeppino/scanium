package com.scanium.app.model

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
    }
}
