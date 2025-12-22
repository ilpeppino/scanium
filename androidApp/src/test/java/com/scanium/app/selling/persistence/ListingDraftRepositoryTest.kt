package com.scanium.app.selling.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scanium.app.items.ScannedItem
import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListingDraftRepositoryTest {

    private lateinit var repository: ListingDraftRepository
    private lateinit var database: com.scanium.app.items.persistence.ScannedItemDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            com.scanium.app.items.persistence.ScannedItemDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = ListingDraftRepository(database.listingDraftDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `repository stores and retrieves drafts`() = runBlocking {
        val draft = sampleDraft()

        repository.upsert(draft)

        val loaded = repository.getByItemId(draft.itemId)
        assertThat(loaded).isNotNull()
        val loadedDraft = requireNotNull(loaded)
        assertThat(loadedDraft.itemId).isEqualTo(draft.itemId)
        assertThat(loadedDraft.title.value).isEqualTo(draft.title.value)

        val all = repository.getAll()
        assertThat(all).hasSize(1)
    }

    private fun sampleDraft(): ListingDraft {
        val item = ScannedItem(
            id = "draft-item",
            category = ItemCategory.HOME_GOOD,
            priceRange = 5.0 to 15.0,
            confidence = 0.5f,
            labelText = "Lamp",
            thumbnail = ImageRef.Bytes(
                bytes = ByteArray(12) { it.toByte() },
                mimeType = "image/jpeg",
                width = 3,
                height = 4
            ),
            timestamp = 500L
        )

        val draft = ListingDraftBuilder.build(item)
        return draft.copy(
            status = DraftStatus.SAVED,
            updatedAt = 600L,
            title = draft.title.copy(source = DraftProvenance.USER_EDITED)
        )
    }
}
