package com.scanium.app.selling.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftPhotoRef
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.DraftFieldsSerializer
import com.scanium.shared.core.models.model.ImageRef

@Entity(tableName = "listing_drafts")
data class ListingDraftEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val profileId: String,
    val title: String,
    val titleConfidence: Float,
    val titleSource: String,
    val description: String,
    val descriptionConfidence: Float,
    val descriptionSource: String,
    val fieldsJson: String,
    val price: Double,
    val priceConfidence: Float,
    val priceSource: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val photoBytes: ByteArray?,
    val photoMimeType: String?,
    val photoWidth: Int?,
    val photoHeight: Int?
)

fun ListingDraft.toEntity(): ListingDraftEntity {
    val primaryPhoto = photos.firstOrNull()
    val photoData = primaryPhoto?.image.toImageFields()

    return ListingDraftEntity(
        id = id,
        itemId = itemId,
        profileId = profile.value,
        title = title.value.orEmpty(),
        titleConfidence = title.confidence,
        titleSource = title.source.name,
        description = description.value.orEmpty(),
        descriptionConfidence = description.confidence,
        descriptionSource = description.source.name,
        fieldsJson = DraftFieldsSerializer.toJson(fields),
        price = price.value ?: 0.0,
        priceConfidence = price.confidence,
        priceSource = price.source.name,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoBytes = photoData?.bytes,
        photoMimeType = photoData?.mimeType,
        photoWidth = photoData?.width,
        photoHeight = photoData?.height
    )
}

fun ListingDraftEntity.toModel(): ListingDraft {
    val fields = DraftFieldsSerializer.fromJson(fieldsJson)
    val photoRef = imageRefFrom(
        bytes = photoBytes,
        mimeType = photoMimeType,
        width = photoWidth,
        height = photoHeight
    )

    val draft = ListingDraft(
        id = id,
        itemId = itemId,
        profile = profileId.takeIf { it.isNotBlank() }?.let { ExportProfileId(it) } ?: ExportProfileId.GENERIC,
        title = DraftField(
            value = title,
            confidence = titleConfidence,
            source = runCatching { DraftProvenance.valueOf(titleSource) }.getOrElse { DraftProvenance.UNKNOWN }
        ),
        description = DraftField(
            value = description,
            confidence = descriptionConfidence,
            source = runCatching { DraftProvenance.valueOf(descriptionSource) }.getOrElse { DraftProvenance.UNKNOWN }
        ),
        fields = fields,
        price = DraftField(
            value = price,
            confidence = priceConfidence,
            source = runCatching { DraftProvenance.valueOf(priceSource) }.getOrElse { DraftProvenance.UNKNOWN }
        ),
        photos = photoRef?.let { listOf(DraftPhotoRef(it, DraftProvenance.DETECTED)) } ?: emptyList(),
        status = runCatching { DraftStatus.valueOf(status) }.getOrElse { DraftStatus.DRAFT },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    return draft.recomputeCompleteness()
}

private data class ImageFields(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int
)

private fun ImageRef?.toImageFields(): ImageFields? {
    return when (this) {
        is ImageRef.Bytes -> ImageFields(bytes = bytes, mimeType = mimeType, width = width, height = height)
        null -> null
    }
}

private fun imageRefFrom(
    bytes: ByteArray?,
    mimeType: String?,
    width: Int?,
    height: Int?
): ImageRef? {
    if (bytes == null || bytes.isEmpty()) return null
    if (mimeType.isNullOrBlank()) return null
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return ImageRef.Bytes(bytes = bytes, mimeType = mimeType, width = width, height = height)
}
