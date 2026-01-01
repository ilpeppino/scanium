package com.scanium.shared.core.models.assistant

import com.scanium.shared.core.models.listing.DraftField
import com.scanium.shared.core.models.listing.DraftFieldKey
import com.scanium.shared.core.models.listing.DraftPhotoRef
import com.scanium.shared.core.models.listing.DraftProvenance
import com.scanium.shared.core.models.listing.DraftStatus
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ExportProfiles
import com.scanium.shared.core.models.listing.ListingDraft
import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantPromptBuilderTest {
    @Test
    fun buildRequest_isDeterministic() {
        val draft = sampleDraft()
        val snapshot = ItemContextSnapshotBuilder.fromDraft(draft)
        val profile = ExportProfiles.generic()
        val first =
            AssistantPromptBuilder.buildRequest(
                items = listOf(snapshot),
                userMessage = "Suggest a better title",
                exportProfile = profile,
                conversation = emptyList(),
            )
        val second =
            AssistantPromptBuilder.buildRequest(
                items = listOf(snapshot),
                userMessage = "Suggest a better title",
                exportProfile = profile,
                conversation = emptyList(),
            )

        assertEquals(first, second)
    }

    @Test
    fun snapshotBuilder_usesDraftFields() {
        val draft = sampleDraft()
        val snapshot = ItemContextSnapshotBuilder.fromDraft(draft)

        assertEquals("item-1", snapshot.itemId)
        assertEquals("Vintage Lamp", snapshot.title)
        assertEquals("Lighting", snapshot.category)
        assertEquals(0.9f, snapshot.confidence)
        assertEquals(2, snapshot.photosCount)
        assertEquals(42.0, snapshot.priceEstimate)
        assertEquals(ExportProfileId.GENERIC, snapshot.exportProfileId)
        assertEquals(
            listOf(
                ItemAttributeSnapshot("brand", "Acme", 0.7f),
                ItemAttributeSnapshot("category", "Lighting", 0.9f),
            ),
            snapshot.attributes,
        )
    }

    private fun sampleDraft(): ListingDraft {
        val photo =
            DraftPhotoRef(
                image =
                    ImageRef.Bytes(
                        bytes = byteArrayOf(1, 2, 3),
                        mimeType = "image/jpeg",
                        width = 10,
                        height = 10,
                    ),
            )

        return ListingDraft(
            id = "draft-1",
            itemId = "item-1",
            profile = ExportProfileId.GENERIC,
            title = DraftField("Vintage Lamp", confidence = 0.8f, source = DraftProvenance.USER_EDITED),
            description = DraftField("Nice lamp", confidence = 0.6f, source = DraftProvenance.DEFAULT),
            fields =
                mapOf(
                    DraftFieldKey.CATEGORY to DraftField("Lighting", 0.9f, DraftProvenance.DETECTED),
                    DraftFieldKey.BRAND to DraftField("Acme", 0.7f, DraftProvenance.DETECTED),
                ),
            price = DraftField(42.0, confidence = 0.5f, source = DraftProvenance.USER_EDITED),
            photos = listOf(photo, photo),
            status = DraftStatus.DRAFT,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }
}
