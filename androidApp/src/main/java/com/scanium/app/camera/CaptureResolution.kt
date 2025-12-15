package com.scanium.app.camera

/**
 * Image capture resolution options for the camera.
 *
 * These settings control the resolution of images captured via ImageCapture,
 * which are stored as high-quality source images for items.
 *
 * Note: These settings do NOT affect ImageAnalysis resolution (used for ML detection),
 * which remains optimized for processing speed.
 */
enum class CaptureResolution(
    val displayName: String,
    val description: String
) {
    /**
     * Low resolution - smallest file sizes, suitable for quick sharing/mobile data.
     * Target: ~1280x720 (HD)
     */
    LOW(
        displayName = "Low",
        description = "Smaller files, faster processing"
    ),

    /**
     * Normal resolution - balanced quality and file size (DEFAULT).
     * Target: ~1920x1080 (Full HD)
     */
    NORMAL(
        displayName = "Normal",
        description = "Balanced quality and size"
    ),

    /**
     * High resolution - maximum quality, larger file sizes.
     * Target: ~3840x2160 (4K) or highest available
     */
    HIGH(
        displayName = "High",
        description = "Best quality, larger files"
    );

    companion object {
        /**
         * Default resolution for new users.
         */
        val DEFAULT = NORMAL
    }
}
