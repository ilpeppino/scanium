package com.scanium.app.quality.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.quality.AttributeCompletenessEvaluator

/**
 * Displays completeness score as a progress indicator with status.
 */
@Composable
fun CompletenessIndicator(
    score: Int,
    isReady: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val progress by animateFloatAsState(
        targetValue = score / 100f,
        label = "progress",
    )
    val progressColor by animateColorAsState(
        targetValue = when {
            score >= AttributeCompletenessEvaluator.READY_THRESHOLD -> Color(0xFF4CAF50) // Green
            score >= 50 -> Color(0xFFFFC107) // Amber
            else -> Color(0xFFFF5722) // Deep Orange
        },
        label = "progressColor",
    )

    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Completeness",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = progressColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = if (isReady) "Ready" else "Needs more info",
                        modifier = Modifier.size(16.dp),
                        tint = progressColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * Compact completeness badge for list items.
 */
@Composable
fun CompletenessBadge(
    score: Int,
    isReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isReady -> Color(0xFF4CAF50).copy(alpha = 0.15f)
            score >= 50 -> Color(0xFFFFC107).copy(alpha = 0.15f)
            else -> Color(0xFFFF5722).copy(alpha = 0.15f)
        },
        label = "backgroundColor",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isReady -> Color(0xFF2E7D32)
            score >= 50 -> Color(0xFFF57F17)
            else -> Color(0xFFBF360C)
        },
        label = "textColor",
    )

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isReady) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = textColor,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "$score%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
    }
}

/**
 * Card showing missing attributes with photo guidance.
 */
@Composable
fun MissingAttributesCard(
    missingAttributes: List<AttributeCompletenessEvaluator.MissingAttribute>,
    onAddPhotoClick: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (missingAttributes.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFC107),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Missing Information",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            missingAttributes.take(3).forEach { attr ->
                MissingAttributeRow(
                    attribute = attr,
                    onPhotoHintClick = if (attr.photoHint != null && onAddPhotoClick != null) {
                        { onAddPhotoClick(attr.photoHint) }
                    } else {
                        null
                    },
                )
            }

            if (missingAttributes.size > 3) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${missingAttributes.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MissingAttributeRow(
    attribute: AttributeCompletenessEvaluator.MissingAttribute,
    onPhotoHintClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            // Importance indicator
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = when {
                            attribute.importance >= 8 -> Color(0xFFFF5722)
                            attribute.importance >= 5 -> Color(0xFFFFC107)
                            else -> Color(0xFF9E9E9E)
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = attribute.displayName,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (onPhotoHintClick != null) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Add photo for ${attribute.displayName}",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Photo guidance card with next recommended shot.
 */
@Composable
fun PhotoGuidanceCard(
    guidance: String,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
        onClick = onTakePhoto,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add Photo to Improve",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
