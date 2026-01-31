package com.scanium.app.items.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.state.ItemFieldUpdate
import com.scanium.app.pricing.PricingInputs
import com.scanium.app.pricing.PricingUiState
import com.scanium.app.pricing.PricingV4Exception
import com.scanium.app.pricing.PricingV4Repository
import com.scanium.app.pricing.PricingV4Request
import com.scanium.app.pricing.VariantSchema
import com.scanium.app.pricing.VariantSchemaRepository
import com.scanium.shared.core.models.items.ItemCondition
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PricingAssistantStep {
    INTRO,
    VARIANTS,
    COMPLETENESS,
    IDENTIFIER,
    CONFIRM,
}

data class PricingAssistantState(
    val steps: List<PricingAssistantStep> = listOf(PricingAssistantStep.INTRO, PricingAssistantStep.CONFIRM),
    val currentStepIndex: Int = 0,
    val brand: String = "",
    val productType: String = "",
    val model: String = "",
    val condition: ItemCondition? = null,
    val variantSchema: VariantSchema = VariantSchema(),
    val variantValues: Map<String, String> = emptyMap(),
    val completenessValues: Set<String> = emptySet(),
    val identifier: String? = null,
    val pricingUiState: PricingUiState = PricingUiState.Idle,
    val isLoadingSchema: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentStep: PricingAssistantStep
        get() = steps.getOrElse(currentStepIndex) { PricingAssistantStep.CONFIRM }

    val pricingInputs: PricingInputs
        get() =
            PricingInputs(
                brand = brand,
                productType = productType,
                model = model,
                condition = condition,
            )
}

class PricingAssistantViewModel
    @AssistedInject
    constructor(
        @Assisted private val itemId: String,
        @Assisted private val itemsViewModel: com.scanium.app.items.ItemsViewModel,
        private val pricingV4Repository: PricingV4Repository,
        private val variantSchemaRepository: VariantSchemaRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: String,
                itemsViewModel: com.scanium.app.items.ItemsViewModel,
            ): PricingAssistantViewModel
        }

        private val _state = MutableStateFlow(PricingAssistantState())
        val state: StateFlow<PricingAssistantState> = _state.asStateFlow()

        init {
            refreshFromItem()
        }

        fun refreshFromItem() {
            val current = itemsViewModel.getItem(itemId)
            val brand = current?.attributes?.get("brand")?.value.orEmpty()
            val productType = current?.attributes?.get("itemType")?.value.orEmpty()
            val model = current?.attributes?.get("model")?.value.orEmpty()
            val condition =
                current?.attributes?.get("condition")?.value?.takeIf { it.isNotEmpty() }?.let { raw ->
                    runCatching { ItemCondition.valueOf(raw.uppercase()) }.getOrNull()
                } ?: current?.condition
            val identifier = current?.barcodeValue?.takeIf { it.isNotBlank() }

            _state.update { prev ->
                val updated =
                    prev.copy(
                        brand = brand,
                        productType = productType,
                        model = model,
                        condition = condition,
                        identifier = identifier,
                    )
                prev.copy(
                    brand = updated.brand,
                    productType = updated.productType,
                    model = updated.model,
                    condition = updated.condition,
                    identifier = updated.identifier,
                    pricingUiState = deriveReadyState(updated),
                )
            }

            if (productType.isNotBlank()) {
                loadSchema(productType)
            } else {
                updateSteps()
            }
        }

        fun loadSchema(productType: String) {
            if (productType.isBlank()) return
            _state.update { it.copy(isLoadingSchema = true, errorMessage = null) }
            viewModelScope.launch {
                val result = variantSchemaRepository.fetchSchema(productType)
                result.onSuccess { schema ->
                    _state.update { prev ->
                        prev.copy(variantSchema = schema, isLoadingSchema = false)
                    }
                    updateSteps()
                }.onFailure { error ->
                    _state.update { prev ->
                        prev.copy(
                            isLoadingSchema = false,
                            errorMessage = error.message ?: "Unable to load variant options",
                        )
                    }
                    updateSteps()
                }
            }
        }

        fun updateVariantValue(
            key: String,
            value: String,
        ) {
            _state.update { prev ->
                val updated =
                    prev.variantValues
                        .toMutableMap()
                        .apply { if (value.isBlank()) remove(key) else put(key, value) }
                val nextState = prev.copy(variantValues = updated, pricingUiState = PricingUiState.Idle)
                nextState.copy(pricingUiState = deriveReadyState(nextState))
            }
        }

        fun toggleCompleteness(option: String) {
            _state.update { prev ->
                val updated = prev.completenessValues.toMutableSet()
                if (updated.contains(option)) {
                    updated.remove(option)
                } else {
                    updated.add(option)
                }
                val nextState = prev.copy(completenessValues = updated, pricingUiState = PricingUiState.Idle)
                nextState.copy(pricingUiState = deriveReadyState(nextState))
            }
        }

        fun updateIdentifier(value: String) {
            _state.update { prev ->
                val nextState =
                    prev.copy(identifier = value.ifBlank { null }, pricingUiState = PricingUiState.Idle)
                nextState.copy(pricingUiState = deriveReadyState(nextState))
            }
            updateSteps()
        }

        fun nextStep() {
            _state.update { prev ->
                val nextIndex = (prev.currentStepIndex + 1).coerceAtMost(prev.steps.lastIndex)
                prev.copy(currentStepIndex = nextIndex)
            }
        }

        fun prevStep() {
            _state.update { prev ->
                val prevIndex = (prev.currentStepIndex - 1).coerceAtLeast(0)
                prev.copy(currentStepIndex = prevIndex)
            }
        }

        fun submitPricingRequest(countryCode: String) {
            val snapshot = _state.value
            val inputs = snapshot.pricingInputs
            if (!inputs.isComplete()) {
                _state.update { it.copy(pricingUiState = PricingUiState.InsufficientData) }
                return
            }

            val condition = inputs.condition ?: return
            val resolvedCountryCode = countryCode.ifBlank { "NL" }

            _state.update { it.copy(pricingUiState = PricingUiState.Loading, errorMessage = null) }

            viewModelScope.launch {
                val result =
                    pricingV4Repository.estimatePrice(
                        PricingV4Request(
                            itemId = itemId,
                            brand = inputs.brand,
                            productType = inputs.productType,
                            model = inputs.model,
                            condition = condition.name,
                            countryCode = resolvedCountryCode.uppercase(),
                            variantAttributes = snapshot.variantValues,
                            completeness = snapshot.completenessValues.toList(),
                            identifier = snapshot.identifier,
                        ),
                    )

                result.onSuccess { insights ->
                    _state.update { prev ->
                        prev.copy(pricingUiState = PricingUiState.Success(insights, isStale = false))
                    }
                }.onFailure { error ->
                    val pricingError = error as? PricingV4Exception
                    _state.update { prev ->
                        prev.copy(
                            pricingUiState =
                                PricingUiState.Error(
                                    message = pricingError?.userMessage ?: "Pricing request failed",
                                    retryable = pricingError?.retryable ?: true,
                                    retryAfterSeconds = pricingError?.retryAfterSeconds,
                                ),
                        )
                    }
                }
            }
        }

        fun applyPrice(median: Double) {
            val priceCents = (median * 100).toLong()
            itemsViewModel.updateItemsFields(
                mapOf(
                    itemId to ItemFieldUpdate(userPriceCents = priceCents),
                ),
            )
        }

        fun resetPricingState() {
            _state.update { prev -> prev.copy(pricingUiState = deriveReadyState(prev)) }
        }

        private fun updateSteps() {
            _state.update { prev ->
                val steps = buildSteps(prev.variantSchema, prev.identifier)
                val currentIndex = prev.currentStepIndex.coerceAtMost(steps.lastIndex)
                prev.copy(steps = steps, currentStepIndex = currentIndex)
            }
        }

        private fun deriveReadyState(state: PricingAssistantState): PricingUiState {
            return when (state.pricingUiState) {
                is PricingUiState.Loading,
                is PricingUiState.Success,
                is PricingUiState.Error,
                -> state.pricingUiState
                else -> {
                    if (state.pricingInputs.missingFields().isEmpty()) {
                        PricingUiState.Ready
                    } else {
                        PricingUiState.InsufficientData
                    }
                }
            }
        }

        private fun buildSteps(
            schema: VariantSchema,
            identifier: String?,
        ): List<PricingAssistantStep> {
            val steps = mutableListOf(PricingAssistantStep.INTRO)
            if (schema.fields.isNotEmpty()) {
                steps.add(PricingAssistantStep.VARIANTS)
            }
            if (schema.completenessOptions.isNotEmpty()) {
                steps.add(PricingAssistantStep.COMPLETENESS)
            }
            if (!identifier.isNullOrBlank()) {
                steps.add(PricingAssistantStep.IDENTIFIER)
            }
            steps.add(PricingAssistantStep.CONFIRM)
            return steps
        }
    }
