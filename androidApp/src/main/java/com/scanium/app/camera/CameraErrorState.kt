package com.scanium.app.camera

data class CameraErrorState(
    val title: String,
    val message: String,
    val canRetry: Boolean = true,
)
