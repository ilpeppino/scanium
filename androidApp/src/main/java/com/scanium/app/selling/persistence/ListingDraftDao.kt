package com.scanium.app.selling.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ListingDraftDao {
    @Query("SELECT * FROM listing_drafts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ListingDraftEntity>

    @Query("SELECT * FROM listing_drafts WHERE itemId = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): ListingDraftEntity?

    @Upsert
    suspend fun upsert(draft: ListingDraftEntity)

    @Query("DELETE FROM listing_drafts WHERE id = :id")
    suspend fun deleteById(id: String)
}
