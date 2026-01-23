package com.scanium.app.classification.hypothesis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.model.ImageRef

/**
 * Bottom sheet showing 3 ranked classification hypotheses.
 * User must explicitly tap to confirm a hypothesis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HypothesisSelectionSheet(
    result: MultiHypothesisResult,
    itemId: String,
    imageHash: String,
    thumbnailRef: ImageRef?,
    onHypothesisConfirmed: (ClassificationHypothesis) -> Unit,
    onNoneOfThese: (String, String?, Float?) -> Unit, // (imageHash, predictedCategory, confidence)
    onAddPhoto: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scrollable content: header + hypotheses
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: "Most likely this is:"
                Text(
                    text = stringResource(R.string.hypothesis_header),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Display WYSIWYG thumbnail (matches what user saw in camera overlay)
                thumbnailRef?.toImageBitmap()?.let { imageBitmap ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Detected object thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                HorizontalDivider()

                // Top 3 hypotheses (cards with tap to confirm)
                result.hypotheses.take(3).forEachIndexed { index, hypothesis ->
                    HypothesisCard(
                        hypothesis = hypothesis,
                        rank = index + 1,
                        onClick = { onHypothesisConfirmed(hypothesis) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Fixed bottom section: Always visible "None of these" + optional refinement CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "None of these" option - ALWAYS VISIBLE
                OutlinedButton(
                    onClick = {
                        val topHypothesis = result.hypotheses.firstOrNull()
                        onNoneOfThese(
                            imageHash,
                            topHypothesis?.categoryName,
                            topHypothesis?.confidence
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hypothesis_none_of_these))
                }

                // "Add another photo to refine" (conditional)
                if (result.needsRefinement) {
                    Button(
                        onClick = onAddPhoto,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.hypothesis_add_photo_to_refine))
                    }
                }
            }
        }
    }
}

/**
 * Card showing a single hypothesis with rank badge, category name, and explanation.
 */
@Composable
private fun HypothesisCard(
    hypothesis: ClassificationHypothesis,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rank + Category name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Rank badge (1, 2, 3) - Neutral color to avoid "pre-selected" feeling
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$rank",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Category name
                Text(
                    text = hypothesis.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Explanation in italic
            Text(
                text = hypothesis.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
