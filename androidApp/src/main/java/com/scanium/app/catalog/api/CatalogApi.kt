package com.scanium.app.catalog.api

import com.scanium.app.BuildConfig
import com.scanium.app.catalog.model.CatalogBrandsResponse
import com.scanium.app.catalog.model.CatalogModelsResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CatalogApi(
    private val httpClient: OkHttpClient,
    private val baseUrlProvider: () -> String = { BuildConfig.SCANIUM_API_BASE_URL },
    private val json: Json = DEFAULT_JSON,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getBrands(subtype: String): CatalogBrandsResponse =
        withContext(ioDispatcher) {
            val baseUrl = baseUrlProvider().trimEnd('/')
            require(baseUrl.isNotBlank()) { "Catalog API base URL is not configured" }

            val endpoint = "$baseUrl/v1/catalog/$subtype/brands"
            val request = Request.Builder().url(endpoint).get().build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    throw IOException("Catalog brands request failed: ${response.code}")
                }

                json.decodeFromString(CatalogBrandsResponse.serializer(), body)
            }
        }

    suspend fun getModels(
        subtype: String,
        brand: String,
    ): CatalogModelsResponse =
        withContext(ioDispatcher) {
            val baseUrl = baseUrlProvider().trimEnd('/')
            require(baseUrl.isNotBlank()) { "Catalog API base URL is not configured" }

            val endpoint = "$baseUrl/v1/catalog/$subtype/models"
            val url =
                endpoint
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("brand", brand)
                    .build()

            val request = Request.Builder().url(url).get().build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    throw IOException("Catalog models request failed: ${response.code}")
                }

                json.decodeFromString(CatalogModelsResponse.serializer(), body)
            }
        }

    companion object {
        private val DEFAULT_JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            }
    }
}
