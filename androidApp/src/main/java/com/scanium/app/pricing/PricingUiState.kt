package com.scanium.app.pricing

import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.items.ItemCondition

sealed interface PricingUiState {
    data object Idle : PricingUiState

    data object InsufficientData : PricingUiState

    data object Ready : PricingUiState

    data object Loading : PricingUiState

    data class Success(
        val insights: PricingInsights,
        val isStale: Boolean = false,
    ) : PricingUiState

    data class Error(
        val message: String,
        val retryable: Boolean,
        val retryAfterSeconds: Int? = null,
    ) : PricingUiState
}

data class PricingInputs(
    val brand: String,
    val productType: String,
    val model: String,
    val condition: ItemCondition?,
) {
    fun isComplete(): Boolean = missingFields().isEmpty()

    fun missingFields(): Set<PricingMissingField> {
        val missing = mutableSetOf<PricingMissingField>()
        if (brand.isBlank()) missing.add(PricingMissingField.BRAND)
        if (productType.isBlank()) missing.add(PricingMissingField.PRODUCT_TYPE)
        // Model is optional - not required for pricing
        if (condition == null) missing.add(PricingMissingField.CONDITION)
        return missing
    }

    fun matches(other: PricingInputs): Boolean =
        normalize(brand) == normalize(other.brand) &&
            normalize(productType) == normalize(other.productType) &&
            normalize(model) == normalize(other.model) &&
            condition == other.condition

    fun isStaleComparedTo(snapshot: PricingInputs?): Boolean = snapshot != null && !snapshot.matches(this)

    private fun normalize(value: String): String = value.trim().lowercase()
}

enum class PricingMissingField {
    BRAND,
    PRODUCT_TYPE,
    MODEL,
    CONDITION,
}
