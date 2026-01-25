package com.scanium.app.items

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.components.AttributeChipsRow
import com.scanium.app.model.toImageBitmap
import com.scanium.app.ui.shimmerEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Content area for the items list, rendering either empty state or the LazyColumn.
 *
 * Pure UI composition - no state mutation, no ViewModel access.
 * All callbacks are passed in from the parent.
 *
 * Uses CustomerSafeCopyFormatter via ItemListViewMapper to display
 * customer-safe, localized item information.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ItemsListContent(
    items: List<ScannedItem>,
    pendingDetectionCount: Int,
    state: ItemsListState,
    mergeSuggestionState: com.scanium.app.items.merging.MergeSuggestionState,
    onItemClick: (ScannedItem) -> Unit,
    onItemLongPress: (ScannedItem) -> Unit,
    onDeleteItem: (ScannedItem) -> Unit,
    onRetryClassification: (ScannedItem) -> Unit,
    onDismissMergeSuggestions: () -> Unit,
    onAcceptAllMerges: (List<com.scanium.app.items.merging.MergeGroup>) -> Unit,
    onShowMergeReview: () -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    modifier: Modifier = Modifier,
) {
    // Map items to formatted display models using CustomerSafeCopyFormatter
    val displayItems =
        remember(items) {
            ItemListViewMapper.mapToListDisplayBatch(items, dropIfWeak = false)
        }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            items.isEmpty() -> {
                // Empty state
                EmptyItemsContent()
            }

            else -> {
                // Items list with header and CTA
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = if (state.selectionMode && state.selectedIds.isNotEmpty()) 96.dp else 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Pending detection indicator (Phase 3)
                    if (pendingDetectionCount > 0) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.items_pending_detection,
                                                pendingDetectionCount,
                                                pendingDetectionCount,
                                            ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    // Smart merge suggestions banner
                    if (mergeSuggestionState is com.scanium.app.items.merging.MergeSuggestionState.Available) {
                        item {
                            com.scanium.app.items.merging.SmartMergeBanner(
                                suggestedMergeCount = mergeSuggestionState.totalSuggestedMerges,
                                onAcceptAll = { onAcceptAllMerges(mergeSuggestionState.groups) },
                                onReview = onShowMergeReview,
                                onDismiss = onDismissMergeSuggestions,
                            )
                        }
                    }

                    // Header: item count
                    item {
                        Text(
                            text = stringResource(R.string.items_list_header_ready_for_resale, items.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }

                    // Item list
                    items(
                        items = items,
                        key = { it.id },
                    ) { item ->
                        val displayItem = displayItems.find { it.itemId == item.id }
                        val dismissState =
                            rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.StartToEnd) {
                                        onDeleteItem(item)
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )

                        val isFirstItem = items.firstOrNull() == item

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = false,
                            modifier =
                                Modifier
                                    .animateItemPlacement(
                                        animationSpec =
                                            spring(
                                                stiffness = 300f,
                                                dampingRatio = 0.8f,
                                            ),
                                    ).then(
                                        if (tourViewModel != null && isFirstItem) {
                                            Modifier.tourTarget("items_first_item", tourViewModel)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            backgroundContent = {
                                val isDismissing = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                                val containerColor =
                                    if (isDismissing) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                val iconTint =
                                    if (isDismissing) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(containerColor)
                                            .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.items_delete),
                                        tint = iconTint,
                                    )
                                }
                            },
                        ) {
                            ItemRow(
                                item = item,
                                displayItem = displayItem,
                                isSelected = state.selectedIds.contains(item.id),
                                selectionMode = state.selectionMode,
                                onClick = { onItemClick(item) },
                                onLongPress = { onItemLongPress(item) },
                                onRetryClassification = { onRetryClassification(item) },
                            )
                        }
                    }

                    // Footer: CTA
                    item {
                        Text(
                            text = stringResource(R.string.items_list_cta),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single item row in the list.
 *
 * Gesture behavior:
 * - Tap: If in selection mode, toggles selection. Otherwise, opens edit screen.
 * - Long press: Enters selection mode and selects this item.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ItemRow(
    item: ScannedItem,
    displayItem: ItemListViewMapper.ItemListDisplay?,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRetryClassification: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val confidenceLabel =
        if (FeatureFlags.showItemDiagnostics) {
            stringResource(
                R.string.items_accessibility_confidence,
                item.confidenceLevel.displayName,
            )
        } else {
            null
        }
    val classificationInProgress = stringResource(R.string.items_accessibility_classification_in_progress)
    val classificationFailed = stringResource(R.string.items_accessibility_classification_failed)
    val selectedLabel = stringResource(R.string.items_accessibility_selected)
    val toggleSelectionLabel = stringResource(R.string.items_accessibility_tap_toggle_selection)
    val tapEditLabel = stringResource(R.string.items_accessibility_tap_edit_long_press)

    // Use formatted display title, fall back to displayLabel if not available
    val displayTitle = displayItem?.title ?: item.displayLabel

    val contentDescription =
        buildString {
            append(displayTitle)
            append(". ")
            // Diagnostic info only in dev builds
            if (FeatureFlags.showItemDiagnostics) {
                append(". ")
                if (confidenceLabel != null) {
                    append(confidenceLabel)
                }
                when (item.classificationStatus) {
                    "PENDING" -> {
                        append(". $classificationInProgress")
                    }

                    "FAILED" -> {
                        append(". $classificationFailed")
                    }

                    else -> {}
                }
            }
            if (isSelected) {
                append(". $selectedLabel")
            }
            if (selectionMode) {
                append(". $toggleSelectionLabel")
            } else {
                append(". $tapEditLabel")
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interactionSource.emit(press)
                            val released = tryAwaitRelease()
                            if (released) {
                                interactionSource.emit(PressInteraction.Release(press))
                            } else {
                                interactionSource.emit(PressInteraction.Cancel(press))
                            }
                        },
                        onLongPress = {
                            onLongPress()
                        },
                        onTap = {
                            onClick()
                        },
                    )
                },
        colors =
            if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                CardDefaults.cardColors()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (FeatureFlags.showItemDiagnostics) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (FeatureFlags.showItemDiagnostics) 12.dp else 16.dp),
        ) {
            // Thumbnail (larger in beta/prod for better visual impact)
            val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()
            val thumbnailSize = if (FeatureFlags.showItemDiagnostics) 80.dp else 96.dp

            thumbnailBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.items_thumbnail),
                    modifier =
                        Modifier
                            .size(thumbnailSize)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit,
                )
            } ?: run {
                // Placeholder if no thumbnail
                Box(
                    modifier =
                        Modifier
                            .size(thumbnailSize)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ).shimmerEffect(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.common_question_mark),
                        style =
                            if (FeatureFlags.showItemDiagnostics) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.headlineLarge
                            },
                    )
                }
            }

            // Item info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Category with diagnostic badges and enrichment status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style =
                            if (FeatureFlags.showItemDiagnostics) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                    )
                    // Diagnostic badges only shown in dev builds
                    if (FeatureFlags.showItemDiagnostics) {
                        ConfidenceBadge(confidenceLevel = item.confidenceLevel)
                        ClassificationStatusBadge(status = item.classificationStatus)
                    }
                    // Enrichment status badge (always visible when enriching)
                    EnrichmentStatusBadge(status = item.enrichmentStatus)
                }

                // Condition badge (more prominent in beta/prod)
                item.condition?.let { condition ->
                    ConditionBadge(condition = condition)
                }

                // Attribute chips (show more in beta/prod with additional space)
                if (item.attributes.isNotEmpty()) {
                    AttributeChipsRow(
                        attributes = item.attributes,
                        maxVisible = if (FeatureFlags.showItemDiagnostics) 3 else 5,
                        compact = true,
                    )
                }

                // Timestamp and confidence percentage (only shown in dev builds)
                if (FeatureFlags.showItemDiagnostics) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatTimestamp(item.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.common_bullet),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.formattedConfidence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Classification error message and retry button
                if (item.classificationStatus == "FAILED") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = stringResource(R.string.cd_classification_failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text =
                                item.classificationErrorMessage
                                    ?: stringResource(R.string.items_classification_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val retryLabel = stringResource(R.string.items_retry_classification)
                        TextButton(
                            onClick = onRetryClassification,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier =
                                Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .semantics { this.contentDescription = retryLabel },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_retry),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.common_retry),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Listing status badge and view listing button
                if (item.listingStatus != ItemListingStatus.NOT_LISTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        ListingStatusBadge(status = item.listingStatus)

                        // "View listing" button for active listings
                        if (item.listingStatus == ItemListingStatus.LISTED_ACTIVE && item.listingUrl != null) {
                            val context = LocalContext.current
                            val viewListingLabel = stringResource(R.string.items_view_listing_marketplace)
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.listingUrl))
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier =
                                    Modifier
                                        .sizeIn(minHeight = 48.dp)
                                        .semantics { this.contentDescription = viewListingLabel },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.cd_view_external),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.common_view),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state content.
 */
@Composable
internal fun BoxScope.EmptyItemsContent() {
    Column(
        modifier =
            Modifier
                .align(Alignment.Center)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.items_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.items_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.items_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Confidence level badge with color coding.
 */
@Composable
internal fun ConfidenceBadge(confidenceLevel: ConfidenceLevel) {
    val (backgroundColor, textColor) =
        when (confidenceLevel) {
            ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            ConfidenceLevel.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = confidenceLevel.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Listing status badge with color coding.
 */
@Composable
internal fun ListingStatusBadge(status: ItemListingStatus) {
    val (backgroundColor, textColor, text) =
        when (status) {
            ItemListingStatus.LISTED_ACTIVE -> {
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    status.displayName,
                )
            }

            ItemListingStatus.LISTING_IN_PROGRESS -> {
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    status.displayName,
                )
            }

            ItemListingStatus.LISTING_FAILED -> {
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    status.displayName,
                )
            }

            ItemListingStatus.NOT_LISTED -> {
                return
            } // Don't show badge for not listed
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Classification status badge with color coding.
 */
@Composable
internal fun ClassificationStatusBadge(status: String) {
    val (backgroundColor, textColor, text) =
        when (status) {
            "PENDING" -> {
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    stringResource(R.string.items_status_classifying),
                )
            }

            "SUCCESS" -> {
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    stringResource(R.string.items_status_cloud),
                )
            }

            "FAILED" -> {
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    stringResource(R.string.items_status_failed),
                )
            }

            "NOT_STARTED" -> {
                return
            }

            // Don't show badge for not started
            else -> {
                return
            } // Unknown status
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Item condition badge with color coding.
 */
@Composable
internal fun ConditionBadge(condition: ItemCondition) {
    val (backgroundColor, textColor) =
        when (condition) {
            ItemCondition.NEW_SEALED -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            ItemCondition.NEW_WITH_TAGS -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            ItemCondition.NEW_WITHOUT_TAGS -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            ItemCondition.LIKE_NEW -> {
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }

            ItemCondition.GOOD -> {
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }

            ItemCondition.FAIR -> {
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }

            ItemCondition.POOR -> {
                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            }
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = ItemLocalizer.getConditionName(condition),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Enrichment status badge showing enriching/enriched state.
 */
@Composable
internal fun EnrichmentStatusBadge(status: com.scanium.shared.core.models.items.EnrichmentLayerStatus) {
    val (backgroundColor, textColor, text) =
        when {
            status.isEnriching -> {
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    stringResource(R.string.items_status_enriching),
                )
            }

            status.isComplete && status.hasAnyResults -> {
                Triple(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    stringResource(R.string.items_status_enriched),
                )
            }

            status.layerA == com.scanium.shared.core.models.items.LayerState.FAILED &&
                status.layerB == com.scanium.shared.core.models.items.LayerState.FAILED -> {
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    stringResource(R.string.items_status_failed),
                )
            }

            else -> {
                return
            } // Don't show badge for pending state
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Formats timestamp to readable string.
 */
internal fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
