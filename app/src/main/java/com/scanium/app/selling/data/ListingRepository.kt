package com.scanium.app.selling.data

import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingId

class ListingRepository {
    private val listings = LinkedHashMap<String, Listing>()

    fun save(listing: Listing) {
        listings[listing.listingId.value] = listing
    }

    fun get(id: ListingId): Listing? = listings[id.value]

    fun getAll(): List<Listing> = listings.values.toList()
}
