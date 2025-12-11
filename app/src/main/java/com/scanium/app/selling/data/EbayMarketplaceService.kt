package com.scanium.app.selling.data

import android.content.Context
import android.graphics.Bitmap
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingError
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingImageSource
import com.scanium.app.selling.domain.ListingStatus
import com.scanium.app.selling.util.ListingDraftMapper
import java.io.File
import java.io.FileOutputStream

sealed class ListingCreationResult {
    data class Success(val listing: Listing) : ListingCreationResult()
    data class Error(val error: ListingError) : ListingCreationResult()
}

class EbayMarketplaceService(
    private val context: Context,
    private val ebayApi: EbayApi,
    private val repository: ListingRepository = ListingRepository()
) {

    suspend fun createListingsForItems(items: List<ScannedItem>): List<ListingCreationResult> {
        return items.map { createListingForItem(it) }
    }

    suspend fun createListingForItem(item: ScannedItem): ListingCreationResult {
        val draft = ListingDraftMapper.fromScannedItem(item)
        val image = prepareListingImage(item, draft)
            ?: return ListingCreationResult.Error(ListingError.VALIDATION_ERROR)
        return try {
            val listing = ebayApi.createListing(draft, image)
            repository.save(listing)
            ListingCreationResult.Success(listing)
        } catch (e: Exception) {
            ListingCreationResult.Error(ListingError.UNKNOWN_ERROR)
        }
    }

    suspend fun refreshListingStatus(id: ListingId): Listing? {
        val status = ebayApi.getListingStatus(id)
        val cached = repository.get(id)
        val updated = cached?.copy(status = status)
        if (updated != null) {
            repository.save(updated)
        }
        return updated
    }

    suspend fun endListing(id: ListingId): ListingStatus {
        val status = ebayApi.endListing(id)
        val cached = repository.get(id)
        val updated = cached?.copy(status = status)
        if (updated != null) {
            repository.save(updated)
        }
        return status
    }

    private fun prepareListingImage(item: ScannedItem, draft: ListingDraft): ListingImage? {
        val existingHighRes = item.fullImageUri?.toString()
        if (existingHighRes != null) {
            return ListingImage(
                source = ListingImageSource.HIGH_RES_CAPTURE,
                uri = existingHighRes
            )
        }

        val bitmap = item.thumbnail ?: return null
        val uri = writeBitmapToCache(bitmap, draft.scannedItemId)
        return ListingImage(
            source = ListingImageSource.DETECTION_THUMBNAIL,
            uri = uri
        )
    }

    private fun writeBitmapToCache(bitmap: Bitmap, itemId: String): String {
        val outputDir = File(context.cacheDir, "listing_images")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, "${itemId}_thumb.jpg")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        return outputFile.toURI().toString()
    }
}
