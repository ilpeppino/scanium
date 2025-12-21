package com.scanium.app.selling.persistence

import com.scanium.app.listing.ListingDraft

interface ListingDraftStore {
    suspend fun getAll(): List<ListingDraft>
    suspend fun getByItemId(itemId: String): ListingDraft?
    suspend fun upsert(draft: ListingDraft)
    suspend fun deleteById(id: String)
}

class ListingDraftRepository(
    private val dao: ListingDraftDao
) : ListingDraftStore {
    override suspend fun getAll(): List<ListingDraft> = dao.getAll().map { it.toModel() }

    override suspend fun getByItemId(itemId: String): ListingDraft? = dao.getByItemId(itemId)?.toModel()

    override suspend fun upsert(draft: ListingDraft) {
        dao.upsert(draft.toEntity())
    }

    override suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }
}

object NoopListingDraftStore : ListingDraftStore {
    override suspend fun getAll(): List<ListingDraft> = emptyList()
    override suspend fun getByItemId(itemId: String): ListingDraft? = null
    override suspend fun upsert(draft: ListingDraft) = Unit
    override suspend fun deleteById(id: String) = Unit
}
