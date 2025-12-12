package com.scanium.app.selling.data

import android.content.Context
import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingError
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingImageSource
import com.scanium.app.selling.domain.ListingStatus
import com.scanium.app.selling.util.ListingDraftMapper
import com.scanium.app.selling.util.ListingImagePreparer

/**
 * Result of a listing creation operation.
 */
sealed class ListingCreationResult {
    data class Success(val listing: Listing) : ListingCreationResult()
    data class Error(val error: ListingError, val message: String? = null) : ListingCreationResult()
}

/**
 * Service for orchestrating eBay marketplace operations.
 *
 * Handles:
 * - Listing creation with image preparation
 * - Status management
 * - Communication with eBay API (mock or real)
 * - Background processing of images
 */
class EbayMarketplaceService(
    private val context: Context,
    private val ebayApi: EbayApi,
    private val repository: ListingRepository = ListingRepository()
) {
    companion object {
        private const val TAG = "EbayMarketplaceService"
    }

    private val imagePreparer = ListingImagePreparer(context)

    /**
     * Creates listings for multiple items.
     * Processes each item independently, so partial failures are allowed.
     */
    suspend fun createListingsForItems(items: List<ScannedItem>): List<ListingCreationResult> {
        return items.map { createListingForItem(it) }
    }

    /**
     * Creates a single eBay listing for a scanned item.
     *
     * Steps:
     * 1. Convert ScannedItem to ListingDraft
     * 2. Prepare high-quality listing image (background thread)
     * 3. Call eBay API to create listing
     * 4. Cache the result locally
     *
     * All image processing happens on Dispatchers.IO automatically.
     */
    suspend fun createListingForItem(item: ScannedItem): ListingCreationResult {
        Log.i(TAG, "═════════════════════════════════════════════════════")
        Log.i(TAG, "Creating listing for item: ${item.id}")

        // Step 1: Create draft
        val draft = ListingDraftMapper.fromScannedItem(item)
        Log.i(TAG, "Draft: ${draft.title} - €${draft.price}")

        // Step 2: Prepare image (runs on background thread)
        val imageResult = imagePreparer.prepareListingImage(
            itemId = item.id,
            fullImageUri = item.fullImageUri,
            thumbnail = item.thumbnail
        )

        val listingImage = when (imageResult) {
            is ListingImagePreparer.PrepareResult.Success -> {
                ListingImage(
                    source = when (imageResult.source) {
                        "fullImageUri" -> ListingImageSource.HIGH_RES_CAPTURE
                        "thumbnail_scaled" -> ListingImageSource.DETECTION_THUMBNAIL
                        else -> ListingImageSource.DETECTION_THUMBNAIL
                    },
                    uri = imageResult.uri.toString()
                )
            }
            is ListingImagePreparer.PrepareResult.Failure -> {
                Log.e(TAG, "Image preparation failed: ${imageResult.reason}")
                return ListingCreationResult.Error(
                    error = ListingError.VALIDATION_ERROR,
                    message = imageResult.reason
                )
            }
        }

        // Step 3: Create listing via API
        return try {
            val listing = ebayApi.createListing(draft, listingImage)
            repository.save(listing)
            Log.i(TAG, "✓ Listing created: ${listing.listingId.value}")
            Log.i(TAG, "═════════════════════════════════════════════════════")
            ListingCreationResult.Success(listing)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Listing creation failed: ${e.message}", e)
            Log.i(TAG, "═════════════════════════════════════════════════════")
            val error = when {
                e is IllegalArgumentException -> ListingError.VALIDATION_ERROR
                e.message?.contains("timeout", ignoreCase = true) == true -> ListingError.NETWORK_ERROR
                else -> ListingError.UNKNOWN_ERROR
            }
            ListingCreationResult.Error(error, e.message)
        }
    }

    /**
     * Refreshes the status of an existing listing.
     */
    suspend fun refreshListingStatus(id: ListingId): Listing? {
        val status = ebayApi.getListingStatus(id)
        val cached = repository.get(id)
        val updated = cached?.copy(status = status)
        if (updated != null) {
            repository.save(updated)
        }
        return updated
    }

    /**
     * Ends (removes) an active listing.
     */
    suspend fun endListing(id: ListingId): ListingStatus {
        val status = ebayApi.endListing(id)
        val cached = repository.get(id)
        val updated = cached?.copy(status = status)
        if (updated != null) {
            repository.save(updated)
        }
        return status
    }

    /**
     * Cleans up old cached listing images.
     * Call this after successful posting or periodically.
     */
    fun cleanupOldImages() {
        imagePreparer.cleanupOldImages()
    }
}
