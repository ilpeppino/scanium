package com.scanium.app.selling.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scanium.app.listing.ListingDraft
import com.scanium.app.logging.ScaniumLog
import com.scanium.shared.core.models.model.ImageRef
import java.io.ByteArrayOutputStream

/**
 * Builds image attachments for assistant requests from ListingDraft photos.
 *
 * Enforces backend constraints:
 * - Max 3 images per item
 * - Max 2MB per image (will recompress if needed)
 *
 * Privacy: Only processes images when explicitly enabled via toggle.
 */
object ImageAttachmentBuilder {
    private const val TAG = "ImageAttachmentBuilder"

    /** Maximum images per item (backend constraint) */
    const val MAX_IMAGES_PER_ITEM = 3

    /** Maximum bytes per image (backend constraint: 2MB) */
    const val MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024

    /** Initial JPEG quality for recompression */
    private const val INITIAL_JPEG_QUALITY = 85

    /** Minimum JPEG quality to try before giving up */
    private const val MIN_JPEG_QUALITY = 40

    /** Quality step for iterative recompression */
    private const val QUALITY_STEP = 15

    /**
     * Result of building image attachments for a request.
     */
    data class AttachmentResult(
        val attachments: List<ItemImageAttachment>,
        val totalBytes: Long,
        val itemImageCounts: Map<String, Int>,
        val skippedCount: Int,
        val recompressedCount: Int,
    )

    /**
     * Builds image attachments from a list of drafts.
     *
     * @param itemDrafts Map of itemId to ListingDraft
     * @param allowImages Whether images are allowed (toggle must be ON)
     * @return AttachmentResult with attachments and metadata
     */
    fun buildAttachments(
        itemDrafts: Map<String, ListingDraft>,
        allowImages: Boolean,
    ): AttachmentResult {
        if (!allowImages) {
            ScaniumLog.d(TAG, "Images disabled by toggle, returning empty attachments")
            return AttachmentResult(
                attachments = emptyList(),
                totalBytes = 0L,
                itemImageCounts = emptyMap(),
                skippedCount = 0,
                recompressedCount = 0,
            )
        }

        val attachments = mutableListOf<ItemImageAttachment>()
        val itemImageCounts = mutableMapOf<String, Int>()
        var skippedCount = 0
        var recompressedCount = 0
        var totalBytes = 0L

        for ((itemId, draft) in itemDrafts) {
            val photos = draft.photos.take(MAX_IMAGES_PER_ITEM)
            var itemAttachmentCount = 0

            ScaniumLog.d(
                TAG,
                "Processing item=$itemId photos=${draft.photos.size} (using first $MAX_IMAGES_PER_ITEM)",
            )

            for ((index, photoRef) in photos.withIndex()) {
                val imageRef = photoRef.image
                val result = processImageRef(itemId, index, imageRef)

                when (result) {
                    is ProcessResult.Success -> {
                        attachments.add(result.attachment)
                        totalBytes += result.attachment.imageBytes.size
                        itemAttachmentCount++
                        if (result.wasRecompressed) {
                            recompressedCount++
                        }
                    }

                    is ProcessResult.Skipped -> {
                        skippedCount++
                        ScaniumLog.w(TAG, "Skipped image $index for item=$itemId: ${result.reason}")
                    }
                }
            }

            if (itemAttachmentCount > 0) {
                itemImageCounts[itemId] = itemAttachmentCount
            }
        }

        ScaniumLog.i(
            TAG,
            "Built ${attachments.size} attachments, totalBytes=$totalBytes, " +
                "itemCounts=$itemImageCounts, skipped=$skippedCount, recompressed=$recompressedCount",
        )

        return AttachmentResult(
            attachments = attachments,
            totalBytes = totalBytes,
            itemImageCounts = itemImageCounts,
            skippedCount = skippedCount,
            recompressedCount = recompressedCount,
        )
    }

    private sealed class ProcessResult {
        data class Success(
            val attachment: ItemImageAttachment,
            val wasRecompressed: Boolean,
        ) : ProcessResult()

        data class Skipped(
            val reason: String,
        ) : ProcessResult()
    }

    private fun processImageRef(
        itemId: String,
        index: Int,
        imageRef: ImageRef,
    ): ProcessResult =
        when (imageRef) {
            is ImageRef.Bytes -> processBytes(itemId, index, imageRef)
            is ImageRef.CacheKey -> ProcessResult.Skipped("CacheKey references not yet supported")
        }

    private fun processBytes(
        itemId: String,
        index: Int,
        imageRef: ImageRef.Bytes,
    ): ProcessResult {
        val originalBytes = imageRef.bytes
        val mimeType = imageRef.mimeType
        val filename = "image_$index.${mimeTypeToExtension(mimeType)}"

        ScaniumLog.d(
            TAG,
            "Processing bytes image: itemId=$itemId index=$index " +
                "size=${originalBytes.size} mimeType=$mimeType",
        )

        // If already under size limit, use as-is
        if (originalBytes.size <= MAX_IMAGE_SIZE_BYTES) {
            return ProcessResult.Success(
                attachment =
                    ItemImageAttachment(
                        itemId = itemId,
                        imageBytes = originalBytes,
                        mimeType = mimeType,
                        filename = filename,
                    ),
                wasRecompressed = false,
            )
        }

        // Need to recompress - decode and re-encode with lower quality
        ScaniumLog.d(
            TAG,
            "Image too large (${originalBytes.size} > $MAX_IMAGE_SIZE_BYTES), recompressing...",
        )

        return try {
            val recompressedBytes = recompressImage(originalBytes)
            if (recompressedBytes != null && recompressedBytes.size <= MAX_IMAGE_SIZE_BYTES) {
                ScaniumLog.d(
                    TAG,
                    "Recompression successful: ${originalBytes.size} -> ${recompressedBytes.size}",
                )
                ProcessResult.Success(
                    attachment =
                        ItemImageAttachment(
                            itemId = itemId,
                            imageBytes = recompressedBytes,
                            // Always JPEG after recompression
                            mimeType = "image/jpeg",
                            filename = "image_$index.jpg",
                        ),
                    wasRecompressed = true,
                )
            } else {
                ProcessResult.Skipped(
                    "Could not recompress to under ${MAX_IMAGE_SIZE_BYTES / 1024}KB",
                )
            }
        } catch (e: Exception) {
            ScaniumLog.e(TAG, "Failed to recompress image", e)
            ProcessResult.Skipped("Recompression failed: ${e.message}")
        }
    }

    /**
     * Attempts to recompress an image to fit under the size limit.
     * Progressively reduces JPEG quality until it fits or reaches minimum quality.
     */
    private fun recompressImage(originalBytes: ByteArray): ByteArray? {
        // Decode the bitmap
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
                ?: return null

        try {
            var quality = INITIAL_JPEG_QUALITY
            while (quality >= MIN_JPEG_QUALITY) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val compressedBytes = output.toByteArray()

                if (compressedBytes.size <= MAX_IMAGE_SIZE_BYTES) {
                    return compressedBytes
                }

                quality -= QUALITY_STEP
                ScaniumLog.d(TAG, "Still too large at quality=$quality, trying lower...")
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        return null // Could not compress enough
    }

    private fun mimeTypeToExtension(mimeType: String): String =
        when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "jpg"
        }
}
