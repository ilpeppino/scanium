package com.example.objecta.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.objecta.items.ScannedItem
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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

            // Get bounding box
            val boundingBox = barcode.boundingBox ?: Rect(0, 0, 100, 100)

            // Crop thumbnail from source bitmap
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox) }

            // Determine category from barcode format
            val category = determineCategoryFromBarcode(barcode)

            // Barcode scanning has very high confidence (typically 1.0 for successful scans)
            // Use a high confidence value for barcodes since they're binary (detected or not)
            val confidence = 0.95f

            // Calculate normalized bounding box area for pricing
            val boxArea = calculateNormalizedArea(
                box = boundingBox,
                imageWidth = sourceBitmap?.width ?: fallbackWidth,
                imageHeight = sourceBitmap?.height ?: fallbackHeight
            )

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = id,
                thumbnail = thumbnail,
                category = category,
                priceRange = priceRange,
                confidence = confidence
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

            Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop thumbnail", e)
            null
        }
    }

    /**
     * Calculates normalized bounding box area (0.0 to 1.0).
     */
    private fun calculateNormalizedArea(box: Rect, imageWidth: Int, imageHeight: Int): Float {
        val boxArea = box.width() * box.height()
        val totalArea = imageWidth * imageHeight
        return if (totalArea > 0) {
            (boxArea.toFloat() / totalArea).coerceIn(0f, 1f)
        } else {
            0f
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
