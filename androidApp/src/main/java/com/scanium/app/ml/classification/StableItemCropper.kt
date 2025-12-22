package com.scanium.app.ml.classification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toBitmap
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.camera.ImageUtils
import com.scanium.app.platform.toRectF
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Provides prepared thumbnails for classification.
 */
interface ClassificationThumbnailProvider {
    suspend fun prepare(item: AggregatedItem): ImageRef?
}

/**
 * No-op implementation that simply reuses the existing thumbnail.
 */
object NoopClassificationThumbnailProvider : ClassificationThumbnailProvider {
    override suspend fun prepare(item: AggregatedItem): ImageRef? = item.thumbnail
}

/**
 * Creates padded crops for stable aggregated items to feed classifiers.
 *
 * Crops are derived once per classification attempt from the best available source:
 * - High-resolution capture when [AggregatedItem.fullImageUri] is available
 * - Otherwise the existing detection thumbnail
 *
 * The resulting bitmap is scaled down to [maxDimension] to limit memory/CPU usage.
 */
class StableItemCropper(
    private val context: Context,
    private val paddingRatio: Float = 0.12f,
    private val maxDimension: Int = 640,
    private val sourceMaxDimension: Int = 1536
): ClassificationThumbnailProvider {
    companion object {
        private const val TAG = "StableItemCropper"
    }

    /**
     * Prepares a cropped [ImageRef] for the aggregated item.
     *
     * @return Cropped ImageRef or the existing thumbnail if cropping fails.
     */
    override suspend fun prepare(item: AggregatedItem): ImageRef? = withContext(Dispatchers.IO) {
        val sourceBitmap = loadSourceBitmap(item.fullImageUri, item.thumbnail)
            ?: return@withContext item.thumbnail

        val normalizedRect = item.boundingBox
        val rectF = normalizedRect.toRectF(sourceBitmap.width, sourceBitmap.height)
        val cropRect = calculateCropRect(sourceBitmap.width, sourceBitmap.height, rectF)
        val croppedBitmap = runCatching {
            Bitmap.createBitmap(
                sourceBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed to crop stable item bitmap: ${error.message}")
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
            return@withContext item.thumbnail
        }

        // Scale down to reasonable classification size
        val scaledBitmap = scaleIfNeeded(croppedBitmap)
        if (sourceBitmap != croppedBitmap && !sourceBitmap.isRecycled) {
            sourceBitmap.recycle()
        }
        if (scaledBitmap != croppedBitmap && !croppedBitmap.isRecycled) {
            croppedBitmap.recycle()
        }

        val imageRef = scaledBitmap.toImageRefJpeg(quality = 88)
        if (!scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
        imageRef
    }

    private suspend fun loadSourceBitmap(
        uri: Uri?,
        fallback: ImageRef?
    ): Bitmap? {
        uri?.let {
            val bitmap = ImageUtils.createThumbnailFromUri(
                context = context,
                uri = it,
                maxDimension = sourceMaxDimension
            )
            if (bitmap != null) {
                return bitmap
            }
            Log.w(TAG, "Failed to load bitmap from $uri, falling back to detection thumbnail")
        }

        return (fallback as? ImageRef.Bytes)?.toBitmap()
    }

    private fun calculateCropRect(
        width: Int,
        height: Int,
        boundingBox: RectF
    ): android.graphics.Rect {
        val rect = boundingBox.toRect(width, height)

        val padX = (rect.width() * paddingRatio).roundToInt()
        val padY = (rect.height() * paddingRatio).roundToInt()

        val left = (rect.left - padX).coerceAtLeast(0)
        val top = (rect.top - padY).coerceAtLeast(0)
        val right = (rect.right + padX).coerceAtMost(width)
        val bottom = (rect.bottom + padY).coerceAtMost(height)

        val clampedWidth = max(1, right - left)
        val clampedHeight = max(1, bottom - top)

        return android.graphics.Rect(left, top, left + clampedWidth, top + clampedHeight)
    }

    private fun RectF.toRect(width: Int, height: Int): android.graphics.Rect {
        val clamped = RectF(
            left.coerceIn(0f, width.toFloat()),
            top.coerceIn(0f, height.toFloat()),
            right.coerceIn(0f, width.toFloat()),
            bottom.coerceIn(0f, height.toFloat())
        )

        val rectWidth = max(1, (clamped.width()).roundToInt())
        val rectHeight = max(1, (clamped.height()).roundToInt())

        val left = clamped.left.roundToInt().coerceIn(0, width - 1)
        val top = clamped.top.roundToInt().coerceIn(0, height - 1)

        val adjustedLeft = left.coerceAtMost(width - rectWidth)
        val adjustedTop = top.coerceAtMost(height - rectHeight)

        return android.graphics.Rect(
            adjustedLeft,
            adjustedTop,
            adjustedLeft + rectWidth,
            adjustedTop + rectHeight
        )
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / largest
        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }
}
