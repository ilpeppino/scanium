package com.scanium.shared.core.models.pricing

import kotlinx.serialization.Serializable

@Serializable
data class PricingV4Request(
    val itemId: String,
    val brand: String,
    val productType: String,
    val model: String,
    val condition: String,
    val countryCode: String,
    val preferredMarketplaces: List<String> = emptyList(),
)

@Serializable
data class PricingV4Response(
    val success: Boolean,
    val pricing: PricingV4Insights? = null,
    val cached: Boolean? = null,
    val processingTimeMs: Long? = null,
)

@Serializable
data class PricingV4Insights(
    val status: String,
    val countryCode: String,
    val sources: List<MarketplaceSource> = emptyList(),
    val totalListingsAnalyzed: Int,
    val timeWindowDays: Int,
    val range: PricingV4Range? = null,
    val sampleListings: List<PricingV4SampleListing> = emptyList(),
    val confidence: String,
    val fallbackReason: String? = null,
)

@Serializable
data class MarketplaceSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val listingCount: Int,
    val searchUrl: String? = null,
)

@Serializable
data class PricingV4SampleListing(
    val title: String,
    val price: Double,
    val currency: String,
    val condition: String? = null,
    val url: String? = null,
    val marketplace: String,
)

@Serializable
data class PricingV4Range(
    val low: Double,
    val median: Double,
    val high: Double,
    val currency: String,
)
