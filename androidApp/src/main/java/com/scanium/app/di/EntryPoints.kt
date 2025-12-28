package com.scanium.app.di

import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.TourViewModel
import com.scanium.app.selling.persistence.ListingDraftRepository
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
