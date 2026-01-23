package com.scanium.app.items.merging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Bottom sheet for reviewing merge suggestions.
 *
 * Shows each merge group with:
 * - Primary item (will be kept)
 * - Similar items (will be merged)
 * - Similarity score
 * - Accept/Reject buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeReviewSheet(
    groups: List<MergeGroup>,
    onAcceptGroup: (MergeGroup) -> Unit,
    onRejectGroup: (MergeGroup) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header
            Text(
                text = stringResource(R.string.smart_merge_review_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.smart_merge_review_subtitle, groups.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Groups list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(groups, key = { it.primaryItem.id }) { group ->
                    MergeGroupCard(
                        group = group,
                        onAccept = { onAcceptGroup(group) },
                        onReject = { onRejectGroup(group) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MergeGroupCard(
    group: MergeGroup,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Primary item info
            Text(
                text = stringResource(R.string.smart_merge_primary_item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = group.primaryItem.labelText?.takeIf { it.isNotEmpty() } ?: "Unlabeled",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = "Confidence: ${(group.primaryItem.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Similar items info
            Text(
                text = stringResource(R.string.smart_merge_similar_items, group.similarItems.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            group.similarItems.forEach { item ->
                Text(
                    text = "• ${item.labelText?.takeIf { it.isNotEmpty() } ?: "Unlabeled"} (${(item.confidence * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Similarity badge
            Text(
                text = stringResource(R.string.smart_merge_similarity, (group.averageSimilarity * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.smart_merge_reject))
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.smart_merge_accept))
                }
            }
        }
    }
}
