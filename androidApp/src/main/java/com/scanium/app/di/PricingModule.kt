package com.scanium.app.di

import android.content.Context
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.pricing.PricingV3Api
import com.scanium.app.pricing.PricingV3Repository
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PricingModule {
    @Provides
    @Singleton
    fun providePricingV3Api(): PricingV3Api =
        PricingV3Api(
            client =
                AssistantOkHttpClientFactory.create(
                    config = AssistantHttpConfig.DEFAULT,
                    logStartupPolicy = false,
                ),
        )

    @Provides
    @Singleton
    fun providePricingV3Repository(
        @ApplicationContext context: Context,
        apiKeyStore: SecureApiKeyStore,
        api: PricingV3Api,
    ): PricingV3Repository =
        PricingV3Repository(
            api = api,
            apiKeyProvider = { apiKeyStore.getApiKey() },
            authTokenProvider = { apiKeyStore.getAuthToken() },
            getDeviceId = { DeviceIdProvider.getHashedDeviceId(context) },
        )
}
