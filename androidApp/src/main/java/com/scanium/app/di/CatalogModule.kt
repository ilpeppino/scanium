package com.scanium.app.di

import android.content.Context
import com.scanium.app.catalog.CatalogRepository
import com.scanium.app.catalog.CatalogSearch
import com.scanium.app.catalog.CatalogSource
import com.scanium.app.catalog.api.CatalogApi
import com.scanium.app.catalog.impl.AssetCatalogSource
import com.scanium.app.catalog.impl.InMemoryCatalogSearch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    fun provideCatalogSource(
        @ApplicationContext context: Context,
    ): CatalogSource = AssetCatalogSource(context)

    @Provides
    @Singleton
    fun provideCatalogSearch(source: CatalogSource): CatalogSearch = InMemoryCatalogSearch(source)

    @Provides
    @Singleton
    fun provideCatalogApi(
        @AuthHttpClient httpClient: OkHttpClient,
    ): CatalogApi = CatalogApi(httpClient)

    @Provides
    @Singleton
    fun provideCatalogRepository(api: CatalogApi): CatalogRepository = CatalogRepository(api)
}
