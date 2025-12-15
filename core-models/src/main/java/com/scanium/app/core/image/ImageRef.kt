package com.scanium.app.core.image

sealed interface ImageRef {
    data class Bytes(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
    ) : ImageRef
}
