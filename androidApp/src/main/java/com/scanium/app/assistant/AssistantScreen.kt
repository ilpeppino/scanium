package com.scanium.app.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.shared.core.models.assistant.AssistantAction
import com.scanium.shared.core.models.assistant.AssistantActionType
import com.scanium.shared.core.models.assistant.AssistantMessage
import com.scanium.shared.core.models.assistant.AssistantRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AssistantViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Context Chips
            if (uiState.contextItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.contextItems.forEach { item ->
                        AssistChip(
                            onClick = {},
                            label = { Text(item.title ?: "Item") }
                        )
                    }
                }
                HorizontalDivider()
            }

            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatMessageItem(message)
                }
                
                if (uiState.isLoading) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    }
                }
                
                if (uiState.error != null) {
                    item {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Pending Actions
                if (uiState.pendingActions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.pendingActions.forEach { action ->
                                ActionCard(action) {
                                    viewModel.handleAction(action)
                                }
                            }
                        }
                    }
                }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your items...") },
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank() && !uiState.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: AssistantMessage) {
    val isUser = message.role == AssistantRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .padding(12.dp)
        ) {
            Text(text = message.content, color = textColor)
        }
        Text(
            text = if (isUser) "You" else "Assistant",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ActionCard(action: AssistantAction, onApply: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when (action.type) {
                    AssistantActionType.APPLY_DRAFT_UPDATE -> "Suggestion: Update Draft"
                    AssistantActionType.COPY_TEXT -> "Suggestion: Copy Text"
                    else -> "Action Available"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            if (action.type == AssistantActionType.APPLY_DRAFT_UPDATE) {
                action.payload["title"]?.let { Text("Title: $it", style = MaterialTheme.typography.bodySmall) }
                action.payload["description"]?.let { Text("Description: $it", style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            }

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Apply")
            }
        }
    }
}
