package com.scanium.app.selling.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.SuggestedAttribute
import com.scanium.app.selling.assistant.local.LocalSuggestionsCard

@Composable
fun AssistantChatList(
    state: AssistantUiState,
    latestAssistantTimestamp: Long?,
    onAction: (AssistantAction) -> Unit,
    onApplyVisionAttribute: (SuggestedAttribute) -> Unit,
    onVisionAttributeConflict: (SuggestedAttribute, String) -> Unit,
    getExistingAttribute: (String) -> com.scanium.shared.core.models.items.ItemAttribute?,
    onCopyText: (String, String) -> Unit,
    onApplyDescription: (String) -> Unit,
    onApplyTitle: (String) -> Unit,
    onQuestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.snapshots.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.snapshots.forEach { snapshot ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = snapshot.title ?: snapshot.category ?: "Item",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        AssistantModeIndicator(
            mode = state.assistantMode,
            failure = state.lastBackendFailure,
            isChecking = state.availability is AssistantAvailability.Checking,
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .semantics { traversalIndex = 0f },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.entries) { _, entry ->
                val isLatestAssistant =
                    entry.message.role == AssistantRole.ASSISTANT &&
                        entry.message.timestamp == latestAssistantTimestamp
                MessageBubble(
                    entry = entry,
                    modifier =
                        if (isLatestAssistant) {
                            Modifier.semantics { traversalIndex = 1f }
                        } else {
                            Modifier
                        },
                    actionTraversalIndex = if (isLatestAssistant) 2f else null,
                    onAction = onAction,
                    onApplyVisionAttribute = onApplyVisionAttribute,
                    onVisionAttributeConflict = onVisionAttributeConflict,
                    getExistingAttribute = getExistingAttribute,
                    visionInsightsEnabled = true,
                )
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            val availability = state.availability
            val localSuggestions = state.localSuggestions
            if (availability is AssistantAvailability.Unavailable &&
                availability.reason != UnavailableReason.LOADING &&
                localSuggestions != null
            ) {
                item {
                    LocalSuggestionsCard(
                        suggestions = localSuggestions,
                        modifier = Modifier.padding(vertical = 8.dp),
                        onCopyText = { label, text ->
                            onCopyText(label, text)
                        },
                        onApplyDescription = onApplyDescription,
                        onApplyTitle = onApplyTitle,
                        onQuestionSelected = onQuestionSelected,
                    )
                }
            }
        }
    }
}
