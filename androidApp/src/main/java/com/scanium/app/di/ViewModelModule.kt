package com.scanium.app.di

import android.content.Context
import com.scanium.app.auth.AuthRepository
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.PostingTargetPreferences
import com.scanium.app.diagnostics.DiagnosticsRepository
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.selling.assistant.AssistantAuthGating
import com.scanium.app.selling.assistant.AssistantPreflightManager
import com.scanium.app.selling.assistant.AssistantPreflightManagerImpl
import com.scanium.app.selling.assistant.AssistantRepository
import com.scanium.app.selling.assistant.AssistantRepositoryFactory
import com.scanium.app.selling.assistant.DevBypassAssistantRepository
import com.scanium.app.selling.assistant.DevMockAssistantRepository
import com.scanium.app.selling.assistant.LocalAssistantHelper
import com.scanium.app.selling.assistant.local.LocalSuggestionEngine
import com.scanium.app.selling.data.EbayApi
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.MockEbayApi
import com.scanium.app.selling.export.AssetExportProfileRepository
import com.scanium.app.selling.persistence.ListingDraftRepository
import com.scanium.app.selling.persistence.ListingDraftStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing ViewModel-related dependencies.
 * Part of ARCH-001/DX-003: DI Framework Migration to reduce ViewModel boilerplate.
 *
 * This module eliminates verbose Factory classes by providing dependencies
 * that ViewModels can receive via Hilt's assisted injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object ViewModelModule {
    @Provides
    @Singleton
    fun provideExportProfilePreferences(
        @ApplicationContext context: Context,
    ): ExportProfilePreferences {
        return ExportProfilePreferences(context)
    }

    @Provides
    @Singleton
    fun providePostingTargetPreferences(
        @ApplicationContext context: Context,
    ): PostingTargetPreferences {
        return PostingTargetPreferences(context)
    }

    @Provides
    @Singleton
    fun provideExportProfileRepository(
        @ApplicationContext context: Context,
    ): ExportProfileRepository {
        return AssetExportProfileRepository(context)
    }

    @Provides
    @Singleton
    fun provideListingDraftStore(listingDraftRepository: ListingDraftRepository): ListingDraftStore {
        return listingDraftRepository
    }

    /**
     * Provides the appropriate AssistantRepository based on build flavor.
     *
     * - DEV builds with DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT=true:
     *   Returns DevBypassAssistantRepository that uses mock when not signed in.
     * - BETA/PROD builds:
     *   Returns the real CloudAssistantRepository (requires Google sign-in).
     *
     * This ensures dev builds can test assistant UI without sign-in while
     * beta/prod maintain their authentication requirement.
     */
    @Provides
    @Singleton
    fun provideAssistantRepository(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
    ): AssistantRepository {
        val realRepository = AssistantRepositoryFactory(context).create()

        return if (AssistantAuthGating.isDevBypassEnabled()) {
            // DEV build: wrap with bypass logic
            DevBypassAssistantRepository(
                realRepository = realRepository,
                mockRepository = DevMockAssistantRepository(),
                authRepository = authRepository,
            )
        } else {
            // BETA/PROD: use real repository directly (always requires sign-in)
            realRepository
        }
    }

    @Provides
    @Singleton
    fun provideLocalAssistantHelper(): LocalAssistantHelper {
        return LocalAssistantHelper()
    }

    @Provides
    @Singleton
    fun provideLocalSuggestionEngine(): LocalSuggestionEngine {
        return LocalSuggestionEngine()
    }

    @Provides
    @Singleton
    fun provideDiagnosticsRepository(
        @ApplicationContext context: Context,
    ): DiagnosticsRepository {
        return DiagnosticsRepository(context)
    }

    @Provides
    @Singleton
    fun provideEbayApi(): EbayApi {
        return MockEbayApi()
    }

    @Provides
    @Singleton
    fun provideEbayMarketplaceService(
        @ApplicationContext context: Context,
        ebayApi: EbayApi,
    ): EbayMarketplaceService {
        return EbayMarketplaceService(context, ebayApi)
    }

    @Provides
    @Singleton
    fun provideAssistantPreflightManager(
        @ApplicationContext context: Context,
    ): AssistantPreflightManager {
        return AssistantPreflightManagerImpl(context)
    }
}
