package com.scanium.app.listing

import com.scanium.shared.core.models.listing.DraftFieldsSerializer as SharedDraftFieldsSerializer
import com.scanium.shared.core.models.listing.ListingDraft as SharedListingDraft
import com.scanium.shared.core.models.listing.ListingDraftBuilder as SharedListingDraftBuilder
import com.scanium.shared.core.models.listing.ListingDraftExport as SharedListingDraftExport
import com.scanium.shared.core.models.listing.ListingDraftFormatter as SharedListingDraftFormatter
import com.scanium.shared.core.models.listing.PostingAssistPlan as SharedPostingAssistPlan
import com.scanium.shared.core.models.listing.PostingAssistPlanBuilder as SharedPostingAssistPlanBuilder
import com.scanium.shared.core.models.listing.PostingStep as SharedPostingStep
import com.scanium.shared.core.models.listing.PostingStepId as SharedPostingStepId

typealias ListingDraft = SharedListingDraft
typealias DraftField<T> = com.scanium.shared.core.models.listing.DraftField<T>
typealias DraftFieldKey = com.scanium.shared.core.models.listing.DraftFieldKey
typealias DraftPhotoRef = com.scanium.shared.core.models.listing.DraftPhotoRef
typealias DraftProvenance = com.scanium.shared.core.models.listing.DraftProvenance
typealias DraftRequiredField = com.scanium.shared.core.models.listing.DraftRequiredField
typealias DraftCompleteness = com.scanium.shared.core.models.listing.DraftCompleteness
typealias DraftStatus = com.scanium.shared.core.models.listing.DraftStatus
typealias ExportProfileId = com.scanium.shared.core.models.listing.ExportProfileId
typealias ExportProfileDefinition = com.scanium.shared.core.models.listing.ExportProfileDefinition
typealias ExportProfiles = com.scanium.shared.core.models.listing.ExportProfiles
typealias ExportProfileRepository = com.scanium.shared.core.models.listing.ExportProfileRepository
typealias ExportFieldKey = com.scanium.shared.core.models.listing.ExportFieldKey
typealias ExportTitleRules = com.scanium.shared.core.models.listing.ExportTitleRules
typealias ExportDescriptionRules = com.scanium.shared.core.models.listing.ExportDescriptionRules
typealias DescriptionFormat = com.scanium.shared.core.models.listing.DescriptionFormat
typealias MissingFieldPolicy = com.scanium.shared.core.models.listing.MissingFieldPolicy
typealias TitleCapitalization = com.scanium.shared.core.models.listing.TitleCapitalization
typealias ListingDraftBuilder = SharedListingDraftBuilder
typealias ListingDraftFormatter = SharedListingDraftFormatter
typealias ListingDraftExport = SharedListingDraftExport
typealias DraftFieldsSerializer = SharedDraftFieldsSerializer
typealias PostingAssistPlan = SharedPostingAssistPlan
typealias PostingAssistPlanBuilder = SharedPostingAssistPlanBuilder
typealias PostingStep = SharedPostingStep
typealias PostingStepId = SharedPostingStepId
