package com.scanium.app.selling.assistant.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.selling.assistant.AssistantDisplayModel

/**
 * Renders a structured listing display model with title, price, condition, highlights, and tags.
 *
 * Similar in pattern to VisionInsightsSection - conditional sections based on data presence.
 *
 * @param displayModel The structured display model to render
 * @param onCopyText Callback when user copies text (label, text)
 * @param modifier Modifier for the card
 */
@Composable
fun StructuredListingSection(
    displayModel: AssistantDisplayModel,
    onCopyText: ((label: String, text: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title section (always present)
            TitleSection(
                title = displayModel.title,
                onCopyText = onCopyText,
            )

            // Price section (if present)
            displayModel.priceSuggestion?.let { price ->
                Spacer(modifier = Modifier.height(4.dp))
                PriceSection(
                    price = price,
                    onCopyText = onCopyText,
                )
            }

            // Condition section (if present)
            displayModel.condition?.let { condition ->
                if (displayModel.priceSuggestion == null) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = condition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Highlights section (if present)
            if (displayModel.highlights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                HighlightsSection(highlights = displayModel.highlights)
            }

            // Tags section (if present)
            if (displayModel.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TagsSection(tags = displayModel.tags)
            }

            // Description section (if present, rendered last)
            displayModel.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Title section with bold, prominent display and copy button.
 */
@Composable
private fun TitleSection(
    title: String,
    onCopyText: ((label: String, text: String) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
        )
        if (onCopyText != null) {
            IconButton(
                onClick = { onCopyText("Title", title) },
                modifier = Modifier.then(Modifier.padding(0.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

/**
 * Price section with primary color emphasis and copy button.
 */
@Composable
private fun PriceSection(
    price: String,
    onCopyText: ((label: String, text: String) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = price,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (onCopyText != null) {
            IconButton(
                onClick = { onCopyText("Price", price) },
                modifier = Modifier.then(Modifier.padding(0.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy price",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

/**
 * Highlights section displayed as a bullet list.
 */
@Composable
private fun HighlightsSection(highlights: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Details:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        highlights.forEach { highlight ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = highlight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Tags section displayed as horizontal scrolling chips.
 */
@Composable
private fun TagsSection(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Tags:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    enabled = false,
                )
            }
        }
    }
}
