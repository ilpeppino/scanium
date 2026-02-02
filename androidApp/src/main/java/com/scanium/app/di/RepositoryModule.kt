package com.scanium.app.di

import android.content.Context
import com.scanium.app.billing.BillingRepository
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.data.AndroidFeatureFlagRepository
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.data.EntitlementManager
import com.scanium.app.data.MarketplaceRepository
import com.scanium.app.data.SettingsRepository
import com.scanium.app.enrichment.EnrichmentRepository
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.ml.VisionInsightsRepository
import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.platform.ConnectivityStatusProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val BILLING_PREFS = "billing_prefs"
private val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = BILLING_PREFS)

/**
 * Hilt module providing repository dependencies.
 * Part of ARCH-001: DI Framework Migration.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideBillingDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.billingDataStore
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
    ): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun provideMarketplaceRepository(
        @ApplicationContext context: Context,
    ): MarketplaceRepository = MarketplaceRepository(context)

    @Provides
    @Singleton
    fun provideBillingRepository(
        dataStore: DataStore<Preferences>,
    ): BillingRepository = BillingRepository(dataStore)

    @Provides
    @Singleton
    fun provideFtueRepository(
        @ApplicationContext context: Context,
    ): FtueRepository = FtueRepository(context)

    @Provides
    @Singleton
    fun provideClassificationPreferences(
        @ApplicationContext context: Context,
    ): ClassificationPreferences = ClassificationPreferences(context)

    @Provides
    @Singleton
    fun provideEntitlementManager(
        settingsRepository: SettingsRepository,
        billingProvider: BillingProvider,
    ): EntitlementManager = EntitlementManager(settingsRepository, billingProvider)

    @Provides
    @Singleton
    fun provideFeatureFlagRepository(
        settingsRepository: SettingsRepository,
        configProvider: ConfigProvider,
        entitlementManager: EntitlementManager,
        connectivityStatusProvider: ConnectivityStatusProvider,
        apiKeyStore: SecureApiKeyStore,
    ): FeatureFlagRepository =
        AndroidFeatureFlagRepository(
            settingsRepository = settingsRepository,
            configProvider = configProvider,
            entitlementPolicyFlow = entitlementManager.entitlementPolicyFlow,
            connectivityStatusProvider = connectivityStatusProvider,
            apiKeyStore = apiKeyStore,
        )

    @Provides
    @Singleton
    fun provideVisionInsightsRepository(
        @ApplicationContext context: Context,
        apiKeyStore: SecureApiKeyStore,
        debugger: com.scanium.app.debug.ImageClassifierDebugger,
    ): VisionInsightsRepository =
        VisionInsightsRepository(
            apiKeyProvider = { apiKeyStore.getApiKey() },
            getDeviceId = { DeviceIdProvider.getHashedDeviceId(context) },
            debugger = debugger,
        )

    @Provides
    @Singleton
    fun provideEnrichmentRepository(
        @ApplicationContext context: Context,
        apiKeyStore: SecureApiKeyStore,
    ): EnrichmentRepository =
        EnrichmentRepository(
            apiKeyProvider = { apiKeyStore.getApiKey() },
            getDeviceId = { DeviceIdProvider.getHashedDeviceId(context) },
        )
}
