package com.scanium.app.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * Bottom sheet displaying full item details including all extracted attributes.
 *
 * Features:
 * - Large thumbnail image
 * - Category, label, and price information
 * - Full list of extracted attributes with confidence indicators
 * - Edit button for each attribute
 * - "Generate Listing" action button
 * - "Refresh Attributes" button to re-run classification
 *
 * @param item The scanned item to display
 * @param onDismiss Callback when sheet is dismissed
 * @param onAttributeEdit Callback when user wants to edit an attribute
 * @param onGenerateListing Callback when user taps "Generate Listing"
 * @param onRefreshAttributes Callback when user wants to re-run classification
 * @param sheetState State for the modal bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: ScannedItem,
    onDismiss: () -> Unit,
    onAttributeEdit: (String, ItemAttribute) -> Unit,
    onGenerateListing: (() -> Unit)? = null,
    onRefreshAttributes: (() -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with thumbnail and basic info
            ItemDetailHeader(item = item)

            HorizontalDivider()

            // Barcode section (if available) with AI assistant button (dev only)
            item.barcodeValue?.let { barcode ->
                BarcodeSection(
                    barcodeValue = barcode,
                    onAskAi =
                        if (FeatureFlags.allowAiAssistant && onGenerateListing != null) {
                            onGenerateListing
                        } else {
                            null
                        },
                )

                HorizontalDivider()
            }

            // Attributes section
            if (item.attributes.isNotEmpty()) {
                AttributesSection(
                    attributes = item.attributes,
                    detectedAttributes = item.detectedAttributes,
                    onEditAttribute = onAttributeEdit,
                )
            } else {
                Text(
                    text = stringResource(R.string.items_no_attributes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Vision details section (OCR, colors, candidates)
            if (!item.visionAttributes.isEmpty) {
                HorizontalDivider()
                VisionDetailsSection(
                    visionAttributes = item.visionAttributes,
                    attributes = item.attributes,
                    onAttributeEdit = onAttributeEdit,
                )
            }

            // Action buttons
            if (item.attributes.isNotEmpty() || onRefreshAttributes != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Generate listing button (if attributes available)
                // Only show here if barcode section didn't already show the AI button
                if (item.attributes.isNotEmpty() && onGenerateListing != null &&
                    FeatureFlags.allowAiAssistant && item.barcodeValue == null
                ) {
                    Button(
                        onClick = onGenerateListing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.items_generate_listing))
                    }
                }

                // Refresh attributes button
                if (onRefreshAttributes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRefreshAttributes,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                    ) {
                        Text(stringResource(R.string.items_refresh_attributes))
                    }
                }
            }
        }
    }
}
