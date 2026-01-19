package com.scanium.app.items.network

import com.scanium.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Photo metadata structure
@Serializable
data class PhotoMetadataDto(
    val id: String,
    val type: String, // "primary", "additional", "closeup"
    val capturedAt: String,
    val hash: String,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
)

@Serializable
data class PhotosMetadataJson(
    val photos: List<PhotoMetadataDto>,
)

// Item DTO (matches backend Item model)
@Serializable
data class ItemDto(
    val id: String,
    val userId: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    // Core metadata
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val priceEstimateLow: Double? = null,
    val priceEstimateHigh: Double? = null,
    val userPriceCents: Long? = null,
    val condition: String? = null,
    // JSON fields (stored as strings)
    val attributesJson: String? = null,
    val detectedAttributesJson: String? = null,
    val visionAttributesJson: String? = null,
    val enrichmentStatusJson: String? = null,
    // Quality metrics
    val completenessScore: Int = 0,
    val missingAttributesJson: String? = null,
    val capturedShotTypesJson: String? = null,
    val isReadyForListing: Boolean = false,
    val lastEnrichedAt: String? = null,
    // Export Assistant
    val exportTitle: String? = null,
    val exportDescription: String? = null,
    val exportBulletsJson: String? = null,
    val exportGeneratedAt: String? = null,
    val exportFromCache: Boolean = false,
    val exportModel: String? = null,
    val exportConfidenceTier: String? = null,
    // Classification
    val classificationStatus: String = "PENDING",
    val domainCategoryId: String? = null,
    val classificationErrorMessage: String? = null,
    val classificationRequestId: String? = null,
    // Photo metadata
    val photosMetadataJson: PhotosMetadataJson? = null,
    // Multi-object scanning
    val attributesSummaryText: String? = null,
    val summaryTextUserEdited: Boolean = false,
    val sourcePhotoId: String? = null,
    // Listing associations
    val listingStatus: String = "NOT_LISTED",
    val listingId: String? = null,
    val listingUrl: String? = null,
    // OCR/barcode
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val labelText: String? = null,
    // Sync metadata
    val syncVersion: Int = 1,
    val clientUpdatedAt: String? = null,
)

// Create item request
@Serializable
data class CreateItemRequest(
    val localId: String,
    val clientUpdatedAt: String,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val priceEstimateLow: Double? = null,
    val priceEstimateHigh: Double? = null,
    val userPriceCents: Long? = null,
    val condition: String? = null,
    val attributesJson: String? = null,
    val detectedAttributesJson: String? = null,
    val visionAttributesJson: String? = null,
    val enrichmentStatusJson: String? = null,
    val completenessScore: Int = 0,
    val missingAttributesJson: String? = null,
    val capturedShotTypesJson: String? = null,
    val isReadyForListing: Boolean = false,
    val lastEnrichedAt: String? = null,
    val exportTitle: String? = null,
    val exportDescription: String? = null,
    val exportBulletsJson: String? = null,
    val exportGeneratedAt: String? = null,
    val exportFromCache: Boolean = false,
    val exportModel: String? = null,
    val exportConfidenceTier: String? = null,
    val classificationStatus: String = "PENDING",
    val domainCategoryId: String? = null,
    val classificationErrorMessage: String? = null,
    val classificationRequestId: String? = null,
    val photosMetadataJson: PhotosMetadataJson? = null,
    val attributesSummaryText: String? = null,
    val summaryTextUserEdited: Boolean = false,
    val sourcePhotoId: String? = null,
    val listingStatus: String = "NOT_LISTED",
    val listingId: String? = null,
    val listingUrl: String? = null,
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val labelText: String? = null,
)

@Serializable
data class CreateItemResponse(
    val item: ItemDto,
    val localId: String,
    val correlationId: String,
)

// Update item request
@Serializable
data class UpdateItemRequest(
    val syncVersion: Int,
    val clientUpdatedAt: String,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val priceEstimateLow: Double? = null,
    val priceEstimateHigh: Double? = null,
    val userPriceCents: Long? = null,
    val condition: String? = null,
    val attributesJson: String? = null,
    val detectedAttributesJson: String? = null,
    val visionAttributesJson: String? = null,
    val enrichmentStatusJson: String? = null,
    val completenessScore: Int? = null,
    val missingAttributesJson: String? = null,
    val capturedShotTypesJson: String? = null,
    val isReadyForListing: Boolean? = null,
    val lastEnrichedAt: String? = null,
    val exportTitle: String? = null,
    val exportDescription: String? = null,
    val exportBulletsJson: String? = null,
    val exportGeneratedAt: String? = null,
    val exportFromCache: Boolean? = null,
    val exportModel: String? = null,
    val exportConfidenceTier: String? = null,
    val classificationStatus: String? = null,
    val domainCategoryId: String? = null,
    val classificationErrorMessage: String? = null,
    val classificationRequestId: String? = null,
    val photosMetadataJson: PhotosMetadataJson? = null,
    val attributesSummaryText: String? = null,
    val summaryTextUserEdited: Boolean? = null,
    val sourcePhotoId: String? = null,
    val listingStatus: String? = null,
    val listingId: String? = null,
    val listingUrl: String? = null,
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val labelText: String? = null,
)

@Serializable
data class UpdateItemResponse(
    val item: ItemDto,
    val correlationId: String,
)

// Get items response
@Serializable
data class GetItemsResponse(
    val items: List<ItemDto>,
    val hasMore: Boolean,
    val nextSince: String? = null,
    val correlationId: String,
)

// Sync request/response
@Serializable
data class SyncChange(
    val action: String, // "CREATE", "UPDATE", "DELETE"
    val localId: String,
    val serverId: String? = null,
    val syncVersion: Int? = null,
    val clientUpdatedAt: String,
    val data: CreateItemRequest? = null,
)

@Serializable
data class SyncRequest(
    val clientTimestamp: String,
    val lastSyncTimestamp: String? = null,
    val changes: List<SyncChange>,
)

@Serializable
data class SyncResult(
    val localId: String,
    val serverId: String? = null,
    val status: String, // "SUCCESS", "CONFLICT", "ERROR"
    val error: String? = null,
    val conflictResolution: String? = null, // "SERVER_WINS", "CLIENT_WINS"
    val item: ItemDto? = null,
)

@Serializable
data class SyncResponse(
    val results: List<SyncResult>,
    val serverChanges: List<ItemDto>,
    val syncTimestamp: String,
    val correlationId: String,
)

/**
 * ItemsApi - Retrofit-style API for items sync (Phase E)
 * Requires auth token via AuthTokenInterceptor
 */
class ItemsApi(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "${BuildConfig.SCANIUM_API_BASE_URL}/v1/items"

    /**
     * GET /v1/items?since=<timestamp>&limit=<n>
     * Get items modified since last sync
     */
    suspend fun getItems(
        since: String? = null,
        limit: Int = 100,
    ): Result<GetItemsResponse> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    buildString {
                        append(baseUrl)
                        append("?limit=$limit")
                        since?.let { append("&since=$it") }
                    }

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Get items failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val getItemsResponse =
                    json.decodeFromString(
                        GetItemsResponse.serializer(),
                        responseBody,
                    )

                Result.success(getItemsResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * POST /v1/items
     * Create new item
     */
    suspend fun createItem(request: CreateItemRequest): Result<CreateItemResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    json
                        .encodeToString(
                            CreateItemRequest.serializer(),
                            request,
                        ).toRequestBody(mediaType)

                val httpRequest =
                    Request
                        .Builder()
                        .url(baseUrl)
                        .post(requestBody)
                        .build()

                val response = httpClient.newCall(httpRequest).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Create item failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val createResponse =
                    json.decodeFromString(
                        CreateItemResponse.serializer(),
                        responseBody,
                    )

                Result.success(createResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * PATCH /v1/items/:id
     * Update existing item with optimistic locking
     */
    suspend fun updateItem(
        itemId: String,
        request: UpdateItemRequest,
    ): Result<UpdateItemResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    json
                        .encodeToString(
                            UpdateItemRequest.serializer(),
                            request,
                        ).toRequestBody(mediaType)

                val httpRequest =
                    Request
                        .Builder()
                        .url("$baseUrl/$itemId")
                        .patch(requestBody)
                        .build()

                val response = httpClient.newCall(httpRequest).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Update item failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val updateResponse =
                    json.decodeFromString(
                        UpdateItemResponse.serializer(),
                        responseBody,
                    )

                Result.success(updateResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * DELETE /v1/items/:id
     * Soft delete item
     */
    suspend fun deleteItem(itemId: String): Result<ItemDto> =
        withContext(Dispatchers.IO) {
            try {
                val httpRequest =
                    Request
                        .Builder()
                        .url("$baseUrl/$itemId")
                        .delete()
                        .build()

                val response = httpClient.newCall(httpRequest).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Delete item failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                // Parse response which contains deleted item
                @Serializable
                data class DeleteResponse(
                    val item: ItemDto,
                    val correlationId: String,
                )

                val deleteResponse =
                    json.decodeFromString(
                        DeleteResponse.serializer(),
                        responseBody,
                    )

                Result.success(deleteResponse.item)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * POST /v1/items/sync
     * Batch sync endpoint
     */
    suspend fun syncItems(request: SyncRequest): Result<SyncResponse> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    json
                        .encodeToString(
                            SyncRequest.serializer(),
                            request,
                        ).toRequestBody(mediaType)

                val httpRequest =
                    Request
                        .Builder()
                        .url("$baseUrl/sync")
                        .post(requestBody)
                        .build()

                val response = httpClient.newCall(httpRequest).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Sync items failed: ${response.code}"),
                    )
                }

                val responseBody =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                val syncResponse =
                    json.decodeFromString(
                        SyncResponse.serializer(),
                        responseBody,
                    )

                Result.success(syncResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
