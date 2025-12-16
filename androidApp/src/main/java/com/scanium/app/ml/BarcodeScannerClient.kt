package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toNormalizedRect
import com.scanium.app.items.ScannedItem
import kotlinx.coroutines.tasks.await

/**
 * Client for ML Kit Barcode Scanning.
 *
 * Configures ML Kit to scan all barcode formats and convert results to ScannedItems.
 */
class BarcodeScannerClient {

    companion object {
        private const val TAG = "BarcodeScannerClient"
    }

    // ML Kit barcode scanner configured to detect all formats
    private val scanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()

        Log.d(TAG, "Creating barcode scanner with all formats")
        BarcodeScanning.getClient(options)
    }

    /**
     * Processes an image and scans for barcodes.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for cropping thumbnails
     * @return List of ScannedItems with detected barcodes
     */
    suspend fun scanBarcodes(
        image: InputImage,
        sourceBitmap: Bitmap?
    ): List<ScannedItem> {
        return try {
            Log.d(TAG, "Starting barcode scan on image ${image.width}x${image.height}, rotation=${image.rotationDegrees}")

            // Run barcode scanning
            val barcodes = scanner.process(image).await()

            Log.d(TAG, "Detected ${barcodes.size} barcode(s)")
            barcodes.forEachIndexed { index, barcode ->
                Log.d(TAG, "Barcode $index: format=${barcode.format}, value=${barcode.rawValue}")
            }

            // Convert each detected barcode to ScannedItem
            val items = barcodes.mapNotNull { barcode ->
                convertToScannedItem(
                    barcode = barcode,
                    sourceBitmap = sourceBitmap,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
            }

            Log.d(TAG, "Converted to ${items.size} scanned items")
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning barcodes", e)
            emptyList()
        }
    }

    /**
     * Converts a Barcode from ML Kit to a ScannedItem.
     *
     * Prefers NormalizedRect (portable type) over Rect (Android-specific).
     * Converts to pixel coordinates only when needed for cropping.
     */
    private fun convertToScannedItem(
        barcode: Barcode,
        sourceBitmap: Bitmap?,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): ScannedItem? {
        return try {
            // Use barcode rawValue as stable ID (or displayValue if rawValue is null)
            val barcodeValue = barcode.rawValue ?: barcode.displayValue ?: return null
            val id = "barcode_$barcodeValue" // Prefix to distinguish from object detection IDs

            val frameWidth = sourceBitmap?.width ?: fallbackWidth
            val frameHeight = sourceBitmap?.height ?: fallbackHeight

            // Convert to NormalizedRect early (prefer portable type)
            val bboxNorm = barcode.boundingBox?.toNormalizedRect(
                frameWidth = frameWidth,
                frameHeight = frameHeight
            ) ?: com.scanium.app.model.NormalizedRect(0f, 0f, 0.1f, 0.1f)

            // For cropping, convert back to pixel coordinates temporarily
            val boundingBoxPixels = barcode.boundingBox ?: Rect(0, 0, 100, 100)

            // Crop thumbnail from source bitmap
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBoxPixels) }
            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)

            // Determine category from barcode format
            val category = determineCategoryFromBarcode(barcode)

            // Barcode scanning has very high confidence (typically 1.0 for successful scans)
            // Use a high confidence value for barcodes since they're binary (detected or not)
            val confidence = 0.95f

            // Calculate normalized area from portable type
            val boxArea = bboxNorm.area

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = id,
                thumbnail = thumbnailRef,
                thumbnailRef = thumbnailRef,
                category = category,
                priceRange = priceRange,
                confidence = confidence,
                boundingBox = bboxNorm
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting barcode to item", e)
            null
        }
    }

    /**
     * Determines the item category based on barcode type.
     * Product barcodes (EAN, UPC) are likely to be product items.
     */
    private fun determineCategoryFromBarcode(barcode: Barcode): ItemCategory {
        return when (barcode.format) {
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E -> {
                // Product barcodes - could be food, electronics, fashion, etc.
                // For PoC, we'll default to UNKNOWN and let the pricing engine handle it
                Log.d(TAG, "Product barcode detected: ${barcode.rawValue}")
                ItemCategory.UNKNOWN
            }
            Barcode.FORMAT_QR_CODE -> {
                // QR codes can contain various data
                Log.d(TAG, "QR code detected: ${barcode.rawValue}")
                ItemCategory.UNKNOWN
            }
            else -> {
                Log.d(TAG, "Other barcode format: ${barcode.format}")
                ItemCategory.UNKNOWN
            }
        }
    }

    /**
     * Crops a thumbnail from the source bitmap using the bounding box.
     */
    private fun cropThumbnail(source: Bitmap, boundingBox: Rect): Bitmap? {
        return try {
            // Ensure bounding box is within bitmap bounds
            val left = boundingBox.left.coerceIn(0, source.width - 1)
            val top = boundingBox.top.coerceIn(0, source.height - 1)
            val width = (boundingBox.width()).coerceIn(1, source.width - left)
            val height = (boundingBox.height()).coerceIn(1, source.height - top)

            // CRITICAL: Limit thumbnail size to save memory (max 200x200)
            val maxDimension = 200
            val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(width, height))
            val thumbnailWidth = (width * scale).toInt().coerceAtLeast(1)
            val thumbnailHeight = (height * scale).toInt().coerceAtLeast(1)

            // Create small thumbnail with independent pixel data
            val thumbnail = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(thumbnail)
            val srcRect = android.graphics.Rect(left, top, left + width, top + height)
            val dstRect = android.graphics.Rect(0, 0, thumbnailWidth, thumbnailHeight)
            canvas.drawBitmap(source, srcRect, dstRect, null)
            thumbnail
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop thumbnail", e)
            null
        }
    }

    /**
     * Cleanup resources when done.
     */
    fun close() {
        try {
            scanner.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing scanner", e)
        }
    }
}
