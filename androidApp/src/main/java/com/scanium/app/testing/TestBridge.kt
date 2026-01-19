package com.scanium.app.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.scanium.app.BuildConfig
import com.scanium.app.ScannedItem
import com.scanium.app.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.app.NormalizedRect
import java.util.UUID

/**
 * Test bridge for debug-only test operations.
 *
 * Provides hooks for instrumented tests to:
 * - Create test items with generated bitmaps (no binary assets needed)
 * - Trigger classification requests for test items
 * - Access internal state for assertions
 *
 * ***REMOVED******REMOVED*** Usage in Tests
 * ```kotlin
 * val testItem = TestBridge.createTestItem(
 *     category = ItemCategory.FASHION,
 *     label = "Test Shoe"
 * )
 * itemsViewModel.addTestItem(testItem)
 * ```
 *
 * **Security Note:** This object only operates in DEBUG builds.
 * All methods return null/no-op in release builds.
 */
object TestBridge {
    private const val TAG = "TestBridge"

    /**
     * Check if test bridge is available (debug builds only).
     */
    val isAvailable: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Generate a solid color bitmap for testing (no binary assets needed).
     *
     * @param width Bitmap width in pixels
     * @param height Bitmap height in pixels
     * @param color Color to fill (ARGB format)
     * @return Generated Bitmap
     */
    fun generateTestBitmap(
        width: Int = 200,
        height: Int = 200,
        color: Int = 0xFF3498DB.toInt(),
// Default: nice blue
    ): Bitmap? {
        if (!BuildConfig.DEBUG) return null

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint =
                Paint().apply {
                    this.color = color
                    style = Paint.Style.FILL
                }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Add a diagonal stripe to make it more distinctive
            paint.color = (color and 0x00FFFFFF) or 0x80000000.toInt() // Semi-transparent version
            canvas.drawRect(0f, 0f, width * 0.3f, height.toFloat(), paint)
        }
    }

    /**
     * Generate a gradient test bitmap for more visual variety.
     *
     * @param width Bitmap width
     * @param height Bitmap height
     * @param startColor Start color of gradient
     * @param endColor End color of gradient
     */
    fun generateGradientBitmap(
        width: Int = 200,
        height: Int = 200,
        startColor: Int = 0xFF3498DB.toInt(),
        endColor: Int = 0xFF9B59B6.toInt(),
    ): Bitmap? {
        if (!BuildConfig.DEBUG) return null

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                val ratio = y.toFloat() / height
                val r = ((startColor shr 16 and 0xFF) * (1 - ratio) + (endColor shr 16 and 0xFF) * ratio).toInt()
                val g = ((startColor shr 8 and 0xFF) * (1 - ratio) + (endColor shr 8 and 0xFF) * ratio).toInt()
                val b = ((startColor and 0xFF) * (1 - ratio) + (endColor and 0xFF) * ratio).toInt()
                val color = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                for (x in 0 until width) {
                    setPixel(x, y, color)
                }
            }
        }
    }

    /**
     * Create a test ScannedItem with generated bitmap thumbnail.
     *
     * @param id Unique identifier (auto-generated if null)
     * @param category Item category
     * @param label Display label
     * @param confidence Classification confidence
     * @param priceLow Minimum estimated price
     * @param priceHigh Maximum estimated price
     * @param bitmapColor Color for generated thumbnail
     * @param classificationStatus Classification status string
     * @return Test ScannedItem ready for insertion
     */
    fun createTestItem(
        id: String = UUID.randomUUID().toString(),
        category: ItemCategory = ItemCategory.UNKNOWN,
        label: String = "Test Item",
        confidence: Float = 0.85f,
        priceLow: Double = 10.0,
        priceHigh: Double = 50.0,
        bitmapColor: Int = 0xFF3498DB.toInt(),
        classificationStatus: String = "PENDING",
    ): ScannedItem? {
        if (!BuildConfig.DEBUG) return null

        val bitmap = generateTestBitmap(200, 200, bitmapColor) ?: return null
        val thumbnailBytes = bitmapToJpegBytes(bitmap)

        return ScannedItem(
            id = id,
            category = category,
            confidence = confidence,
            thumbnail =
                ImageRef.Bytes(
                    bytes = thumbnailBytes,
                    mimeType = "image/jpeg",
                    width = 200,
                    height = 200,
                ),
            priceRange = priceLow to priceHigh,
            labelText = label,
            boundingBox = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f),
            timestamp = System.currentTimeMillis(),
            classificationStatus = classificationStatus,
        )
    }

    /**
     * Create multiple test items for batch testing.
     *
     * @param count Number of items to create
     * @param categoryRotation Categories to rotate through
     * @return List of test items
     */
    fun createTestItems(
        count: Int = 3,
        categoryRotation: List<ItemCategory> =
            listOf(
                ItemCategory.FASHION,
                ItemCategory.ELECTRONICS,
                ItemCategory.HOME_GOOD,
            ),
    ): List<ScannedItem> {
        if (!BuildConfig.DEBUG) return emptyList()

        val colors =
            listOf(
                0xFF3498DB.toInt(),
// Blue
                0xFF2ECC71.toInt(),
// Green
                0xFFE74C3C.toInt(),
// Red
                0xFF9B59B6.toInt(),
// Purple
                0xFFF39C12.toInt(),
// Orange
            )

        return (0 until count).mapNotNull { index ->
            createTestItem(
                id = "test_item_$index",
                category = categoryRotation[index % categoryRotation.size],
                label = "Test Item ${index + 1}",
                confidence = 0.75f + (index % 3) * 0.1f,
                priceLow = 10.0 + index * 5,
                priceHigh = 50.0 + index * 10,
                bitmapColor = colors[index % colors.size],
            )
        }
    }

    /**
     * Convert Bitmap to JPEG bytes.
     */
    private fun bitmapToJpegBytes(
        bitmap: Bitmap,
        quality: Int = 85,
    ): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Create ImageRef.Bytes from a generated bitmap.
     */
    fun createTestImageRef(
        width: Int = 200,
        height: Int = 200,
        color: Int = 0xFF3498DB.toInt(),
    ): ImageRef.Bytes? {
        if (!BuildConfig.DEBUG) return null

        val bitmap = generateTestBitmap(width, height, color) ?: return null
        val bytes = bitmapToJpegBytes(bitmap)

        return ImageRef.Bytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            width = width,
            height = height,
        )
    }
}
