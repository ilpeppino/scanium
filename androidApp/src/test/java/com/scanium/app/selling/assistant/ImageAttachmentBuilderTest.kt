package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftPhotoRef
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ListingDraft
import com.scanium.shared.core.models.model.ImageRef
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageAttachmentBuilderTest {

    @Test
    fun `buildAttachments returns empty when toggle is OFF`() {
        val draft = createDraftWithPhotos("item-1", 2)
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = false,
        )

        assertThat(result.attachments).isEmpty()
        assertThat(result.totalBytes).isEqualTo(0L)
        assertThat(result.itemImageCounts).isEmpty()
    }

    @Test
    fun `buildAttachments returns empty when toggle is ON but no photos`() {
        val draft = createDraftWithPhotos("item-1", 0)
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = true,
        )

        assertThat(result.attachments).isEmpty()
        assertThat(result.totalBytes).isEqualTo(0L)
        assertThat(result.itemImageCounts).isEmpty()
    }

    @Test
    fun `buildAttachments returns empty when toggle is ON but drafts is empty`() {
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = emptyMap(),
            allowImages = true,
        )

        assertThat(result.attachments).isEmpty()
        assertThat(result.totalBytes).isEqualTo(0L)
        assertThat(result.itemImageCounts).isEmpty()
    }

    @Test
    fun `buildAttachments builds attachments when toggle is ON and photos exist`() {
        val draft = createDraftWithPhotos("item-1", 2)
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = true,
        )

        assertThat(result.attachments).hasSize(2)
        assertThat(result.totalBytes).isGreaterThan(0L)
        assertThat(result.itemImageCounts).containsEntry("item-1", 2)

        // Verify all attachments have correct itemId
        result.attachments.forEach { attachment ->
            assertThat(attachment.itemId).isEqualTo("item-1")
            assertThat(attachment.mimeType).isEqualTo("image/jpeg")
            assertThat(attachment.imageBytes).isNotEmpty()
        }
    }

    @Test
    fun `buildAttachments limits to MAX_IMAGES_PER_ITEM`() {
        // Create draft with 5 photos, but only first 3 should be used
        val draft = createDraftWithPhotos("item-1", 5)
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = true,
        )

        assertThat(result.attachments).hasSize(ImageAttachmentBuilder.MAX_IMAGES_PER_ITEM)
        assertThat(result.itemImageCounts).containsEntry("item-1", 3)
    }

    @Test
    fun `buildAttachments handles multiple items`() {
        val draft1 = createDraftWithPhotos("item-1", 2)
        val draft2 = createDraftWithPhotos("item-2", 1)
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft1, "item-2" to draft2),
            allowImages = true,
        )

        assertThat(result.attachments).hasSize(3)
        assertThat(result.itemImageCounts).containsEntry("item-1", 2)
        assertThat(result.itemImageCounts).containsEntry("item-2", 1)

        // Verify item IDs are correct
        val item1Attachments = result.attachments.filter { it.itemId == "item-1" }
        val item2Attachments = result.attachments.filter { it.itemId == "item-2" }
        assertThat(item1Attachments).hasSize(2)
        assertThat(item2Attachments).hasSize(1)
    }

    @Test
    fun `buildAttachments passes through images under size limit`() {
        val smallBytes = createTestImageBytes(100) // 100 bytes, well under 2MB
        val draft = createDraftWithCustomPhotos("item-1", listOf(smallBytes))
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = true,
        )

        assertThat(result.attachments).hasSize(1)
        assertThat(result.recompressedCount).isEqualTo(0)
        assertThat(result.attachments[0].imageBytes).isEqualTo(smallBytes)
    }

    @Test
    fun `AttachmentResult tracks skipped count`() {
        // CacheKey references are not supported yet, so they should be skipped
        val draft = createDraftWithCacheKeyPhoto("item-1")
        val result = ImageAttachmentBuilder.buildAttachments(
            itemDrafts = mapOf("item-1" to draft),
            allowImages = true,
        )

        assertThat(result.attachments).isEmpty()
        assertThat(result.skippedCount).isEqualTo(1)
    }

    // Helper methods

    private fun createDraftWithPhotos(itemId: String, photoCount: Int): ListingDraft {
        val photos = (0 until photoCount).map { index ->
            DraftPhotoRef(
                image = ImageRef.Bytes(
                    bytes = createTestImageBytes(1024 + index), // 1KB+ each
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                ),
                source = DraftProvenance.DETECTED,
            )
        }
        return createTestDraft(itemId, photos)
    }

    private fun createDraftWithCustomPhotos(itemId: String, bytesList: List<ByteArray>): ListingDraft {
        val photos = bytesList.map { bytes ->
            DraftPhotoRef(
                image = ImageRef.Bytes(
                    bytes = bytes,
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                ),
                source = DraftProvenance.DETECTED,
            )
        }
        return createTestDraft(itemId, photos)
    }

    private fun createDraftWithCacheKeyPhoto(itemId: String): ListingDraft {
        val photos = listOf(
            DraftPhotoRef(
                image = ImageRef.CacheKey(
                    key = "cache-key-123",
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                ),
                source = DraftProvenance.DETECTED,
            ),
        )
        return createTestDraft(itemId, photos)
    }

    private fun createTestDraft(itemId: String, photos: List<DraftPhotoRef>): ListingDraft {
        return ListingDraft(
            id = "draft-$itemId",
            itemId = itemId,
            profile = ExportProfileId.GENERIC,
            title = DraftField("Test Item", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            description = DraftField("Test description", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            fields = emptyMap(),
            price = DraftField(10.0, confidence = 0.5f, source = DraftProvenance.DEFAULT),
            photos = photos,
            status = DraftStatus.DRAFT,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun createTestImageBytes(size: Int): ByteArray {
        // Create a minimal valid JPEG header followed by padding
        // This is a very minimal JPEG that some decoders may accept
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI marker
            0xFF.toByte(), 0xE0.toByte(), // APP0 marker
            0x00, 0x10, // Length: 16
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00, // "JFIF\0"
            0x01, 0x01, // Version 1.1
            0x00, // Aspect ratio units
            0x00, 0x01, // X density
            0x00, 0x01, // Y density
            0x00, 0x00, // Thumbnail size
        )
        // For test purposes, just use raw bytes
        return ByteArray(size) { it.toByte() }
    }
}
