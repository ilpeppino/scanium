package com.scanium.app.selling.domain

enum class ListingImageSource {
    DETECTION_THUMBNAIL,
    HIGH_RES_CAPTURE,
    LOCAL_URI,
}

data class ListingImage(
    val source: ListingImageSource,
    val uri: String,
)
