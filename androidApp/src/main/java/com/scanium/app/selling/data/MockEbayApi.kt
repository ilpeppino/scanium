package com.scanium.app.selling.data

import android.util.Log
import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingStatus
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mock eBay API implementation with realistic behavior simulation.
 *
 * Features:
 * - Configurable network delays (400-1200ms)
 * - Multiple failure modes for testing
 * - Debug settings for controlled failure injection
 */
class MockEbayApi(
    private val config: MockEbayConfig = MockEbayConfig(),
) : EbayApi {
    companion object {
        private const val TAG = "MockEbayApi"

        // SEC-007: Listing field validation limits
        private const val MAX_TITLE_LENGTH = 80
        private const val MAX_DESCRIPTION_LENGTH = 4000
        private const val MIN_PRICE = 0.01
        private const val MAX_PRICE = 1_000_000.0
    }

    private val listings = mutableMapOf<String, Listing>()

    override suspend fun createListing(
        draft: ListingDraft,
        image: ListingImage?,
    ): Listing {
        Log.i(TAG, "Creating listing for item: ${draft.scannedItemId} (title: ${draft.title})")

        // Simulate realistic network delay
        val delayMs =
            if (config.simulateNetworkDelay) {
                Random.nextLong(config.minDelayMs, config.maxDelayMs)
            } else {
                0L
            }

        if (delayMs > 0) {
            Log.d(TAG, "Simulating network delay: ${delayMs}ms")
            delay(delayMs)
        }

        // Simulate failure modes
        if (config.failureMode != MockFailureMode.NONE) {
            val shouldFail = Random.nextDouble() < config.failureRate
            if (shouldFail) {
                Log.w(TAG, "Simulating failure mode: ${config.failureMode}")
                throw when (config.failureMode) {
                    MockFailureMode.NETWORK_TIMEOUT -> {
                        IllegalStateException("Mock network timeout")
                    }
                    MockFailureMode.VALIDATION_ERROR -> {
                        IllegalArgumentException("Mock validation error: Title cannot be empty")
                    }
                    MockFailureMode.IMAGE_TOO_SMALL -> {
                        IllegalArgumentException("Mock validation error: Image resolution too small (min 500x500)")
                    }
                    MockFailureMode.RANDOM -> {
                        IllegalStateException("Random mock failure")
                    }
                    MockFailureMode.NONE -> error("Should not reach here")
                }
            }
        }

        // SEC-007: Comprehensive field validation
        validateListingFields(draft)

        // Create successful listing
        val id = ListingId("EBAY-MOCK-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}")
        val listing =
            Listing(
                listingId = id,
                scannedItemId = draft.scannedItemId,
                title = draft.title,
                description = draft.description,
                category = draft.category,
                price = draft.price,
                currency = draft.currency,
                condition = draft.condition,
                image = image,
                status = ListingStatus.ACTIVE,
                externalUrl = "https://mock.ebay.local/listing/${id.value}",
            )
        listings[id.value] = listing

        Log.i(TAG, "✓ Listing created successfully: ${id.value}")
        Log.i(TAG, "  URL: ${listing.externalUrl}")

        return listing
    }

    override suspend fun getListingStatus(id: ListingId): ListingStatus {
        // Simulate small delay
        if (config.simulateNetworkDelay) {
            delay(Random.nextLong(100, 300))
        }
        return listings[id.value]?.status ?: ListingStatus.UNKNOWN
    }

    override suspend fun endListing(id: ListingId): ListingStatus {
        // Simulate small delay
        if (config.simulateNetworkDelay) {
            delay(Random.nextLong(200, 500))
        }

        val existing = listings[id.value]
        val updated = existing?.copy(status = ListingStatus.ENDED)
        if (updated != null) {
            listings[id.value] = updated
        }
        return updated?.status ?: ListingStatus.UNKNOWN
    }

    /**
     * SEC-007: Validates listing fields according to eBay API requirements.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validateListingFields(draft: ListingDraft) {
        // Title validation
        when {
            draft.title.isBlank() -> {
                throw IllegalArgumentException("Title cannot be empty")
            }
            draft.title.length > MAX_TITLE_LENGTH -> {
                throw IllegalArgumentException(
                    "Title exceeds maximum length ($MAX_TITLE_LENGTH characters): ${draft.title.length}",
                )
            }
            !draft.title.matches(Regex("^[\\w\\s.,!?'\"()-]+$")) -> {
                throw IllegalArgumentException(
                    "Title contains invalid characters (alphanumeric and basic punctuation only)",
                )
            }
        }

        // Description validation
        if (draft.description != null) {
            when {
                draft.description.length > MAX_DESCRIPTION_LENGTH -> {
                    throw IllegalArgumentException(
                        "Description exceeds maximum length ($MAX_DESCRIPTION_LENGTH characters): ${draft.description.length}",
                    )
                }
            }
        }

        // Price validation
        val priceValue = draft.price
        if (!priceValue.isFinite()) {
            throw IllegalArgumentException("Price must be a valid number: ${draft.price}")
        }
        when {
            priceValue < MIN_PRICE -> {
                throw IllegalArgumentException(
                    "Price too low (minimum: $MIN_PRICE): $priceValue",
                )
            }
            priceValue > MAX_PRICE -> {
                throw IllegalArgumentException(
                    "Price too high (maximum: $MAX_PRICE): $priceValue",
                )
            }
        }
    }

    /**
     * Returns all mock listings (for debugging).
     */
    fun getAllListings(): List<Listing> = listings.values.toList()

    /**
     * Clears all mock listings (for testing).
     */
    fun clearAllListings() {
        listings.clear()
        Log.i(TAG, "Cleared all mock listings")
    }
}

/**
 * Configuration for MockEbayApi behavior.
 */
data class MockEbayConfig(
    val simulateNetworkDelay: Boolean = true,
    val minDelayMs: Long = 400,
    val maxDelayMs: Long = 1200,
    val failureMode: MockFailureMode = MockFailureMode.NONE,
    val failureRate: Double = 0.0,
// 0.0 = never fail, 1.0 = always fail
)

/**
 * Failure modes for testing.
 */
enum class MockFailureMode {
    NONE,
    NETWORK_TIMEOUT,
    VALIDATION_ERROR,
    IMAGE_TOO_SMALL,
    RANDOM,
}
