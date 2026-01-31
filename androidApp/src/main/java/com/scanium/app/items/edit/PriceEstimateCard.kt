package com.scanium.app.items.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.pricing.PricingMissingField
import com.scanium.app.pricing.PricingUiState
import com.scanium.app.ui.shimmerEffect
import com.scanium.app.util.openInAppBrowser
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.assistant.SampleListing
import kotlin.math.roundToInt

@Composable
fun PriceEstimateCard(
    uiState: PricingUiState,
    missingFields: Set<PricingMissingField>,
    regionLabel: String,
    onGetEstimate: () -> Unit,
    onUsePrice: (Double) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (uiState) {
                PricingUiState.Loading -> LoadingState()
                PricingUiState.Ready -> ReadyState(regionLabel = regionLabel, onGetEstimate = onGetEstimate)
                PricingUiState.InsufficientData ->
                    InsufficientDataState(
                        missingFields = missingFields,
                        onGetEstimate = onGetEstimate,
                    )
                PricingUiState.Idle ->
                    InsufficientDataState(
                        missingFields = missingFields,
                        onGetEstimate = onGetEstimate,
                    )
                is PricingUiState.Error -> ErrorState(uiState = uiState, onRetry = onRetry)
                is PricingUiState.Success ->
                    SuccessState(
                        uiState = uiState,
                        onUsePrice = onUsePrice,
                        onRefresh = onRefresh,
                        onRetry = onRetry,
                    )
            }
        }
    }
}

@Composable
private fun ReadyState(
    regionLabel: String,
    onGetEstimate: () -> Unit,
) {
    Text(
        text = stringResource(R.string.pricing_card_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.pricing_ready_subtitle, regionLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onGetEstimate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pricing_button_get_estimate))
    }
}

@Composable
private fun InsufficientDataState(
    missingFields: Set<PricingMissingField>,
    onGetEstimate: () -> Unit,
) {
    Text(
        text = stringResource(R.string.pricing_card_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.pricing_insufficient_data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onGetEstimate,
        modifier = Modifier.fillMaxWidth(),
        enabled = missingFields.isEmpty(),
    ) {
        Text(stringResource(R.string.pricing_button_get_estimate))
    }
}

@Composable
private fun LoadingState() {
    Text(
        text = stringResource(R.string.pricing_card_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect(),
    )
    Text(
        text = stringResource(R.string.pricing_loading),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect(),
    )
}

@Composable
private fun ErrorState(
    uiState: PricingUiState.Error,
    onRetry: () -> Unit,
) {
    Text(
        text = stringResource(R.string.pricing_card_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = uiState.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pricing_button_retry))
    }
}

@Composable
private fun SuccessState(
    uiState: PricingUiState.Success,
    onUsePrice: (Double) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    val insights = uiState.insights
    when (insights.status.uppercase()) {
        "NO_RESULTS" -> NoResultsState(onRetry = onRetry)
        "OK", "FALLBACK" ->
            PricingResultState(
                insights = insights,
                isStale = uiState.isStale,
                onUsePrice = onUsePrice,
                onRefresh = onRefresh,
                showFallbackWarning = insights.status.uppercase() == "FALLBACK",
            )
        else ->
            ErrorState(
                uiState =
                    PricingUiState.Error(
                        message = stringResource(R.string.pricing_error_region, insights.countryCode),
                        retryable = false,
                    ),
                onRetry = onRetry,
            )
    }
}

@Composable
private fun PricingResultState(
    insights: PricingInsights,
    isStale: Boolean,
    onUsePrice: (Double) -> Unit,
    onRefresh: () -> Unit,
    showFallbackWarning: Boolean,
) {
    val range = insights.range ?: return
    val currencySymbol = remember(range.currency) { getCurrencySymbol(range.currency) }
    val lowValue = range.low.roundToInt()
    val highValue = range.high.roundToInt()
    val median = range.median ?: (range.low + range.high) / 2.0
    val marketplaceNames =
        remember(insights.marketplacesUsed) {
            insights.marketplacesUsed.map { it.name }.filter { it.isNotBlank() }
        }
    val marketplacesLabel =
        marketplaceNames.joinToString(", ").ifBlank {
            stringResource(R.string.pricing_marketplaces_fallback)
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pricing_estimated_title),
            style = MaterialTheme.typography.titleSmall,
        )
        if (isStale) {
            Text(
                text = stringResource(R.string.pricing_stale_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Text(
        text = "$currencySymbol$lowValue - $currencySymbol$highValue",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )

    ConfidenceRow(confidence = insights.confidence)

    val resultCount = insights.totalListingsAnalyzed ?: insights.results.size
    Text(
        text =
            if (resultCount > 0) {
                stringResource(
                    R.string.pricing_based_on,
                    resultCount,
                    marketplacesLabel,
                )
            } else {
                stringResource(R.string.pricing_based_on_conditions)
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    insights.timeWindowDays?.let { windowDays ->
        Text(
            text = stringResource(R.string.pricing_time_window, windowDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showFallbackWarning) {
        Text(
            text = stringResource(R.string.pricing_fallback_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (insights.confidence == PricingConfidence.LOW) {
        Text(
            text = stringResource(R.string.pricing_low_confidence_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (isStale) {
        Text(
            text = stringResource(R.string.pricing_stale_warning, stringResource(R.string.pricing_stale_reason_generic)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (insights.marketplacesUsed.isNotEmpty()) {
        VerifiableSourcesSection(sources = insights.marketplacesUsed)
    }

    if (insights.sampleListings.isNotEmpty()) {
        SampleListingsSection(sampleListings = insights.sampleListings, currencySymbol = currencySymbol)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { onUsePrice(median) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.pricing_button_use_price, "$currencySymbol${median.roundToInt()}"))
        }
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.pricing_button_refresh))
        }
    }
}

@Composable
private fun VerifiableSourcesSection(sources: List<com.scanium.shared.core.models.assistant.MarketplaceUsed>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.pricing_based_on_sources),
            style = MaterialTheme.typography.labelMedium,
        )
        sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = source.name)
                source.listingCount?.let { count ->
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.pricing_listing_count,
                                count,
                                count,
                            ),
                    )
                }
            }
            source.searchUrl?.let { url ->
                TextButton(onClick = { openInAppBrowser(context, url) }) {
                    Text(stringResource(R.string.pricing_view_listings))
                }
            }
        }
    }
}

@Composable
private fun SampleListingsSection(
    sampleListings: List<SampleListing>,
    currencySymbol: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.pricing_sample_listings),
            style = MaterialTheme.typography.labelMedium,
        )
        sampleListings.take(3).forEach { listing ->
            Text(
                text =
                    listOfNotNull(
                        listing.title,
                        listing.price.amount.takeIf { it > 0 }?.let { "$currencySymbol${it.roundToInt()}" },
                    ).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConfidenceRow(confidence: PricingConfidence?) {
    val (label, color, fraction) =
        when (confidence) {
            PricingConfidence.HIGH ->
                Triple(
                    stringResource(R.string.pricing_confidence_high),
                    MaterialTheme.colorScheme.primary,
                    1f,
                )
            PricingConfidence.MED ->
                Triple(
                    stringResource(R.string.pricing_confidence_med),
                    MaterialTheme.colorScheme.tertiary,
                    0.66f,
                )
            PricingConfidence.LOW ->
                Triple(
                    stringResource(R.string.pricing_confidence_low),
                    MaterialTheme.colorScheme.error,
                    0.33f,
                )
            null -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, 0f)
        }

    if (label.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun NoResultsState(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.pricing_card_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.pricing_no_results_title),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.pricing_no_results_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pricing_button_try_again))
    }
}

private fun getCurrencySymbol(currencyCode: String): String =
    when (currencyCode.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CHF" -> "CHF "
        else -> "$currencyCode "
    }
