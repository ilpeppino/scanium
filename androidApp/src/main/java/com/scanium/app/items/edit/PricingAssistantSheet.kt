package com.scanium.app.items.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingAssistantSheet(
    viewModel: PricingAssistantViewModel,
    countryCode: String,
    onDismiss: () -> Unit,
    onUsePrice: (Double) -> Unit,
    onOpenListingAssistant: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val state by viewModel.state.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PricingAssistantContent(
            state = state,
            onDismiss = onDismiss,
            onNext = viewModel::nextStep,
            onBack = viewModel::prevStep,
            onUpdateVariant = viewModel::updateVariantValue,
            onToggleCompleteness = viewModel::toggleCompleteness,
            onUpdateIdentifier = viewModel::updateIdentifier,
            onSubmit = { viewModel.submitPricingRequest(countryCode) },
            onUsePrice = onUsePrice,
            onOpenListingAssistant = onOpenListingAssistant,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PricingAssistantContent(
    state: PricingAssistantState,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onUpdateVariant: (String, String) -> Unit,
    onToggleCompleteness: (String) -> Unit,
    onUpdateIdentifier: (String) -> Unit,
    onSubmit: () -> Unit,
    onUsePrice: (Double) -> Unit,
    onOpenListingAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missingFields = state.pricingInputs.missingFields()
    val isLastStep = state.currentStepIndex == state.steps.lastIndex
    val canProceed =
        when (state.currentStep) {
            PricingAssistantStep.VARIANTS -> {
                val required = state.variantSchema.fields.filter { it.required }
                required.all { field -> state.variantValues[field.key].isNullOrBlank().not() }
            }
            else -> true
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PricingAssistantHeader()

        PricingAssistantStepIndicator(
            total = state.steps.size,
            currentIndex = state.currentStepIndex,
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        when (state.currentStep) {
            PricingAssistantStep.INTRO -> {
                Text(
                    text = stringResource(R.string.pricing_assistant_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.pricing_assistant_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PricingAssistantStep.VARIANTS -> {
                Text(
                    text = stringResource(R.string.pricing_assistant_variants_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                state.variantSchema.fields.forEach { field ->
                    VariantFieldInput(
                        field = field,
                        value = state.variantValues[field.key].orEmpty(),
                        onValueChange = { onUpdateVariant(field.key, it) },
                    )
                }
            }

            PricingAssistantStep.COMPLETENESS -> {
                Text(
                    text = stringResource(R.string.pricing_assistant_completeness_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.pricing_assistant_completeness_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.variantSchema.completenessOptions.forEach { option ->
                        val selected = state.completenessValues.contains(option)
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleCompleteness(option) },
                            label = { Text(option) },
                        )
                    }
                }
            }

            PricingAssistantStep.IDENTIFIER -> {
                Text(
                    text = stringResource(R.string.pricing_assistant_identifier_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.pricing_assistant_identifier_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.identifier.orEmpty(),
                    onValueChange = onUpdateIdentifier,
                    label = { Text(stringResource(R.string.pricing_assistant_identifier_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PricingAssistantStep.CONFIRM -> {
                Text(
                    text = stringResource(R.string.pricing_assistant_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                PricingAssistantSummary(state = state)

                PriceEstimateCard(
                    uiState = state.pricingUiState,
                    missingFields = missingFields,
                    regionLabel = stringResource(R.string.pricing_region_generic),
                    onGetEstimate = onSubmit,
                    onUsePrice = onUsePrice,
                    onRefresh = onSubmit,
                    onRetry = onSubmit,
                )

                TextButton(onClick = onOpenListingAssistant) {
                    Text(stringResource(R.string.pricing_assistant_open_listing_assistant))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.currentStepIndex > 0) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_back))
                }
            }

            if (!isLastStep) {
                Button(
                    onClick = onNext,
                    enabled = canProceed,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            } else {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}

@Composable
private fun PricingAssistantHeader() {
    Text(
        text = stringResource(R.string.pricing_assistant_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PricingAssistantStepIndicator(
    total: Int,
    currentIndex: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val color =
                if (index == currentIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(color = color, shape = CircleShape),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VariantFieldInput(
    field: com.scanium.app.pricing.VariantField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (field.type) {
            "select" -> {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {},
                        readOnly = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        placeholder = { Text(stringResource(R.string.common_select)) },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        field.options.orEmpty().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            else -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.pricing_assistant_variants_placeholder)) },
                )
            }
        }
    }
}

@Composable
private fun PricingAssistantSummary(state: PricingAssistantState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SummaryRow(label = stringResource(R.string.pricing_assistant_summary_brand), value = state.brand)
        SummaryRow(label = stringResource(R.string.pricing_assistant_summary_model), value = state.model)
        SummaryRow(label = stringResource(R.string.pricing_assistant_summary_type), value = state.productType)

        if (state.variantValues.isNotEmpty()) {
            SummaryRow(
                label = stringResource(R.string.pricing_assistant_summary_variants),
                value = state.variantValues.values.joinToString(", "),
            )
        }

        if (state.completenessValues.isNotEmpty()) {
            SummaryRow(
                label = stringResource(R.string.pricing_assistant_summary_completeness),
                value = state.completenessValues.joinToString(", "),
            )
        }

        state.identifier?.let { identifier ->
            SummaryRow(
                label = stringResource(R.string.pricing_assistant_summary_identifier),
                value = identifier,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
