package com.scanium.app.selling.data

import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingStatus

interface EbayApi {
    suspend fun createListing(draft: ListingDraft, image: ListingImage?): Listing

    suspend fun getListingStatus(id: ListingId): ListingStatus

    suspend fun endListing(id: ListingId): ListingStatus
}
