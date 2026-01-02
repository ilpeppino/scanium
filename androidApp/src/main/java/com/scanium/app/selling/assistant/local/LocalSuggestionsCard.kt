package com.scanium.app.selling.assistant.local

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A card component that displays local suggestions when the online assistant is unavailable.
 *
 * Provides actionable suggestions based on item context:
 * - Missing info prompts with benefits
 * - Suggested questions
 * - Defects checklist
 * - Description template
 * - Photo suggestions
 *
 * Part of OFFLINE-ASSIST: Provides useful guidance when online assistant is unavailable.
 */
@Composable
fun LocalSuggestionsCard(
    suggestions: LocalSuggestions,
    modifier: Modifier = Modifier,
    onCopyText: (label: String, text: String) -> Unit = { _, _ -> },
    onApplyDescription: (description: String) -> Unit = {},
    onApplyTitle: (title: String) -> Unit = {},
    onQuestionSelected: (question: String) -> Unit = {},
) {
    var expandedSection by remember { mutableStateOf<String?>("missing") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Local Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Using local suggestions while assistant is unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Missing Info Section (always visible if there are items)
            if (suggestions.missingInfoPrompts.isNotEmpty()) {
                SuggestionSection(
                    title = "Missing Information",
                    icon = Icons.Default.Warning,
                    iconTint = MaterialTheme.colorScheme.error,
                    isExpanded = expandedSection == "missing",
                    onToggle = { expandedSection = if (expandedSection == "missing") null else "missing" },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.missingInfoPrompts.forEach { prompt ->
                            MissingInfoItem(prompt = prompt)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Suggested Title
            suggestions.suggestedTitle?.let { title ->
                SuggestionSection(
                    title = "Suggested Title",
                    icon = Icons.Default.Edit,
                    isExpanded = expandedSection == "title",
                    onToggle = { expandedSection = if (expandedSection == "title") null else "title" },
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onApplyTitle(title) },
                                    modifier = Modifier.semantics { contentDescription = "Use this title" },
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use title")
                                }
                                TextButton(
                                    onClick = { onCopyText("Title", title) },
                                    modifier = Modifier.semantics { contentDescription = "Copy title" },
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Description Template Section
            if (suggestions.suggestedDescriptionTemplate.isNotBlank()) {
                SuggestionSection(
                    title = "Description Template",
                    icon = Icons.Default.Edit,
                    isExpanded = expandedSection == "description",
                    onToggle = { expandedSection = if (expandedSection == "description") null else "description" },
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = suggestions.suggestedDescriptionTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onApplyDescription(suggestions.suggestedDescriptionTemplate) },
                                    modifier = Modifier.semantics { contentDescription = "Apply description template" },
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Apply")
                                }
                                TextButton(
                                    onClick = { onCopyText("Description", suggestions.suggestedDescriptionTemplate) },
                                    modifier = Modifier.semantics { contentDescription = "Copy description template" },
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Defects Checklist Section
            if (suggestions.suggestedDefectsChecklist.isNotEmpty()) {
                SuggestionSection(
                    title = "Defects Checklist",
                    icon = Icons.Default.CheckCircle,
                    isExpanded = expandedSection == "defects",
                    onToggle = { expandedSection = if (expandedSection == "defects") null else "defects" },
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            suggestions.suggestedDefectsChecklist.forEachIndexed { index, defect ->
                                Text(
                                    text = "• $defect",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (index < suggestions.suggestedDefectsChecklist.lastIndex) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    val checklistText = suggestions.suggestedDefectsChecklist
                                        .joinToString("\n") { "☐ $it" }
                                    onCopyText("Checklist", checklistText)
                                },
                                modifier = Modifier.semantics { contentDescription = "Copy defects checklist" },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy checklist")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Photo Suggestions Section
            if (suggestions.suggestedPhotos.isNotEmpty()) {
                SuggestionSection(
                    title = "Suggested Photos",
                    icon = Icons.Default.CameraAlt,
                    isExpanded = expandedSection == "photos",
                    onToggle = { expandedSection = if (expandedSection == "photos") null else "photos" },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.suggestedPhotos.forEachIndexed { index, photo ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = photo,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Questions Section (as chips for quick actions)
            if (suggestions.suggestedQuestions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Consider these questions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.suggestedQuestions.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { question ->
                                AssistChip(
                                    onClick = { onQuestionSelected(question) },
                                    label = {
                                        Text(
                                            text = question,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Fill remaining space if odd number of items
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MissingInfoItem(prompt: MissingInfoPrompt) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prompt.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = prompt.benefit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
