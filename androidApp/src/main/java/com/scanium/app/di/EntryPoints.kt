package com.scanium.app.di

import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.TourViewModel
import com.scanium.app.items.edit.ExportAssistantViewModel
import com.scanium.app.selling.assistant.AssistantViewModel
import com.scanium.app.selling.persistence.ListingDraftRepository
import com.scanium.app.selling.ui.DraftReviewViewModel
import com.scanium.app.selling.ui.ListingViewModel
import com.scanium.app.selling.ui.PostingAssistViewModel
import com.scanium.app.pricing.PricingV3Repository
import com.scanium.app.pricing.PricingV4Repository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing TourViewModel's assisted factory.
 * Part of ARCH-001: Hilt DI migration.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TourViewModelFactoryEntryPoint {
    fun tourViewModelFactory(): TourViewModel.Factory
}

/**
 * Entry point for accessing ListingDraftRepository in composables.
 * Part of ARCH-001: Hilt DI migration.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DraftStoreEntryPoint {
    fun draftStore(): ListingDraftRepository
}

/**
 * Entry point for accessing SettingsRepository in composables.
 * Part of ARCH-001: Hilt DI migration.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsRepositoryEntryPoint {
    fun settingsRepository(): SettingsRepository
}

/**
 * Entry point for accessing AssistantViewModel's assisted factory.
 * Part of ARCH-001/DX-003: Hilt DI migration to reduce ViewModel boilerplate.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantViewModelFactoryEntryPoint {
    fun assistantViewModelFactory(): AssistantViewModel.Factory
}

/**
 * Entry point for accessing DraftReviewViewModel's assisted factory.
 * Part of ARCH-001/DX-003: Hilt DI migration to reduce ViewModel boilerplate.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DraftReviewViewModelFactoryEntryPoint {
    fun draftReviewViewModelFactory(): DraftReviewViewModel.Factory
}

/**
 * Entry point for accessing PostingAssistViewModel's assisted factory.
 * Part of ARCH-001/DX-003: Hilt DI migration to reduce ViewModel boilerplate.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PostingAssistViewModelFactoryEntryPoint {
    fun postingAssistViewModelFactory(): PostingAssistViewModel.Factory
}

/**
 * Entry point for accessing ListingViewModel's assisted factory.
 * Part of ARCH-001/DX-003: Hilt DI migration to reduce ViewModel boilerplate.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ListingViewModelFactoryEntryPoint {
    fun listingViewModelFactory(): ListingViewModel.Factory
}

/**
 * Entry point for accessing ExportAssistantViewModel's assisted factory.
 * Part of Phase 4: Export Assistant feature.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExportAssistantViewModelFactoryEntryPoint {
    fun exportAssistantViewModelFactory(): ExportAssistantViewModel.Factory
}

/**
 * Entry point for accessing Pricing V3 repository in composables.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PricingV3RepositoryEntryPoint {
    fun pricingV3Repository(): PricingV3Repository
}

/**
 * Entry point for accessing Pricing V4 repository in composables.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PricingV4RepositoryEntryPoint {
    fun pricingV4Repository(): PricingV4Repository
}
