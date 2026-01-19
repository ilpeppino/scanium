package com.scanium.app.di

import android.content.Context
import com.scanium.app.auth.AuthRepository
import com.scanium.app.auth.GoogleAuthApi
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.items.network.ItemsApi
import com.scanium.app.network.AuthTokenInterceptor
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthApiHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    /**
     * Phase C: HTTP client for auth API calls (login, refresh, logout).
     * Does NOT include AuthTokenInterceptor to avoid circular dependency.
     */
    @Provides
    @Singleton
    @AuthApiHttpClient
    fun provideAuthApiHttpClient(): OkHttpClient =
        AssistantOkHttpClientFactory.create(
            config = AssistantHttpConfig.DEFAULT,
            additionalInterceptors = emptyList(),
        )

    @Provides
    @Singleton
    fun provideGoogleAuthApi(
        @AuthApiHttpClient httpClient: OkHttpClient,
    ): GoogleAuthApi = GoogleAuthApi(httpClient)

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        apiKeyStore: SecureApiKeyStore,
        authApi: GoogleAuthApi,
    ): AuthRepository = AuthRepository(context, apiKeyStore, authApi)

    /**
     * Phase C: Auth token interceptor with silent session renewal.
     * Requires AuthRepository to be provided first to avoid circular dependency.
     */
    @Provides
    @Singleton
    fun provideAuthTokenInterceptor(
        apiKeyStore: SecureApiKeyStore,
        authRepository: AuthRepository,
    ): AuthTokenInterceptor =
        AuthTokenInterceptor(
            tokenProvider = { apiKeyStore.getAuthToken() },
            authRepository = authRepository,
        )

    /**
     * Phase C: HTTP client for business API calls (assistant, etc.).
     * Includes AuthTokenInterceptor for automatic token injection and renewal.
     */
    @Provides
    @Singleton
    @AuthHttpClient
    fun provideAuthHttpClient(authTokenInterceptor: AuthTokenInterceptor): OkHttpClient =
        AssistantOkHttpClientFactory.create(
            config = AssistantHttpConfig.DEFAULT,
            additionalInterceptors = listOf(authTokenInterceptor),
        )

    /**
     * Phase E: ItemsApi for multi-device sync.
     * Uses AuthHttpClient for automatic token injection.
     */
    @Provides
    @Singleton
    fun provideItemsApi(
        @AuthHttpClient httpClient: OkHttpClient,
    ): ItemsApi = ItemsApi(httpClient)
}
