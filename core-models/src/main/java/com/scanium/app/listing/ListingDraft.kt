package com.scanium.app.listing

import com.scanium.shared.core.models.listing.DraftCompleteness
import com.scanium.shared.core.models.listing.DraftField
import com.scanium.shared.core.models.listing.DraftFieldKey
import com.scanium.shared.core.models.listing.DraftPhotoRef
import com.scanium.shared.core.models.listing.DraftProvenance
import com.scanium.shared.core.models.listing.DraftRequiredField
import com.scanium.shared.core.models.listing.DraftStatus
import com.scanium.shared.core.models.listing.ExportProfile
import com.scanium.shared.core.models.listing.ListingDraft as SharedListingDraft
import com.scanium.shared.core.models.listing.ListingDraftBuilder as SharedListingDraftBuilder
import com.scanium.shared.core.models.listing.DraftFieldsSerializer as SharedDraftFieldsSerializer

typealias ListingDraft = SharedListingDraft
typealias DraftField<T> = com.scanium.shared.core.models.listing.DraftField<T>
typealias DraftFieldKey = com.scanium.shared.core.models.listing.DraftFieldKey
typealias DraftPhotoRef = com.scanium.shared.core.models.listing.DraftPhotoRef
typealias DraftProvenance = com.scanium.shared.core.models.listing.DraftProvenance
typealias DraftRequiredField = com.scanium.shared.core.models.listing.DraftRequiredField
typealias DraftCompleteness = com.scanium.shared.core.models.listing.DraftCompleteness
typealias DraftStatus = com.scanium.shared.core.models.listing.DraftStatus
typealias ExportProfile = com.scanium.shared.core.models.listing.ExportProfile
typealias ListingDraftBuilder = SharedListingDraftBuilder
typealias DraftFieldsSerializer = SharedDraftFieldsSerializer
