package com.scanium.app.selling.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.listing.ListingDraftFormatter
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.EvidenceBullet
import com.scanium.app.selling.export.AssetExportProfileRepository
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.selling.util.ListingClipboardHelper
import com.scanium.app.selling.util.ListingShareHelper
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    itemIds: List<String>,
    onBack: () -> Unit,
    onOpenPostingAssist: (List<String>, Int) -> Unit,
    itemsViewModel: ItemsViewModel,
    draftStore: ListingDraftStore
) {
    val context = LocalContext.current
    val profileRepository = remember { AssetExportProfileRepository(context) }
    val profilePreferences = remember { ExportProfilePreferences(context) }
    val assistantRepository = remember { AssistantRepositoryFactory().create() }
    val viewModel: AssistantViewModel = viewModel(
        factory = AssistantViewModel.factory(
            itemIds = itemIds,
            itemsViewModel = itemsViewModel,
            draftStore = draftStore,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = assistantRepository
        )
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }
    var readAloudEnabled by remember { mutableStateOf(false) }
    val voiceController = remember { AssistantVoiceController(context) }
    var lastSpokenTimestamp by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        onDispose { voiceController.shutdown() }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            if (event is AssistantUiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(state.entries, readAloudEnabled) {
        if (!readAloudEnabled) return@LaunchedEffect
        val lastAssistant = state.entries.lastOrNull { it.message.role == AssistantRole.ASSISTANT }
        val timestamp = lastAssistant?.message?.timestamp
        if (timestamp != null && timestamp != lastSpokenTimestamp) {
            voiceController.speak(lastAssistant.message.content)
            lastSpokenTimestamp = timestamp
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening(
                onResult = { recognized -> inputText = recognized },
                onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
            )
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Read aloud", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = readAloudEnabled,
                            onCheckedChange = { readAloudEnabled = it }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (state.snapshots.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.snapshots.forEach { snapshot ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = snapshot.title ?: snapshot.category ?: "Item",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.entries) { entry ->
                        MessageBubble(entry = entry, onAction = { action ->
                            handleAssistantAction(
                                action = action,
                                state = state,
                                viewModel = viewModel,
                                onOpenPostingAssist = onOpenPostingAssist,
                                onShare = { itemId ->
                                    scope.launch {
                                        val draft = draftStore.getByItemId(itemId)
                                            ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                                                ?.let { ListingDraftBuilder.build(it) }
                                        if (draft == null) {
                                            snackbarHostState.showSnackbar("No draft to share")
                                            return@launch
                                        }
                                        val profile = state.profile.takeIf { it.id == draft.profile }
                                            ?: ExportProfiles.generic()
                                        val export = ListingDraftFormatter.format(draft, profile)
                                        val currentItem = itemsViewModel.items.value.firstOrNull { it.id == draft.itemId }
                                        val shareImages = draft.photos.map { it.image }.ifEmpty {
                                            listOfNotNull(currentItem?.thumbnailRef ?: currentItem?.thumbnail)
                                        }
                                        val imageUris = ListingShareHelper.writeShareImages(
                                            context = context,
                                            itemId = draft.itemId,
                                            images = shareImages
                                        )
                                        val intent = ListingShareHelper.buildShareIntent(
                                            contentResolver = context.contentResolver,
                                            text = export.shareText,
                                            imageUris = imageUris
                                        )
                                        val chooser = Intent.createChooser(intent, "Share listing")
                                        context.startActivity(chooser)
                                    }
                                },
                                onOpenUrl = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onCopyText = { label, text ->
                                    ListingClipboardHelper.copy(context, label, text)
                                    scope.launch { snackbarHostState.showSnackbar("$label copied") }
                                }
                            )
                        })
                    }
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Loading stage indicator
            when (state.loadingStage) {
                LoadingStage.VISION_PROCESSING -> {
                    LoadingStageIndicator(
                        stage = "Analyzing images...",
                        showProgress = true
                    )
                }
                LoadingStage.LLM_PROCESSING -> {
                    LoadingStageIndicator(
                        stage = "Drafting answer...",
                        showProgress = true
                    )
                }
                LoadingStage.ERROR -> {
                    RetryBanner(
                        onRetry = { viewModel.retryLastMessage() }
                    )
                }
                else -> {
                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Smart suggested questions (context-aware)
            SmartSuggestionsRow(
                suggestions = state.suggestedQuestions.ifEmpty {
                    listOf("Suggest a better title", "What details should I add?", "Estimate price range")
                },
                onActionSelected = { actionText ->
                    inputText = actionText
                    viewModel.sendMessage(actionText)
                }
            )

            // ChatGPT-like input field with embedded icons
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                placeholder = { Text("Ask about listing improvements...") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Voice icon button
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    voiceController.startListening(
                                        onResult = { recognized -> inputText = recognized },
                                        onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                                    )
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input"
                            )
                        }

                        // Send icon button
                        IconButton(
                            onClick = {
                                val text = inputText
                                inputText = ""
                                viewModel.sendMessage(text)
                            },
                            enabled = inputText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send message",
                                tint = if (inputText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                },
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val text = inputText
                            inputText = ""
                            viewModel.sendMessage(text)
                        }
                    }
                )
            )
        }
    }
}

@Composable
private fun MessageBubble(
    entry: AssistantChatEntry,
    onAction: (AssistantAction) -> Unit
) {
    val isUser = entry.message.role == AssistantRole.USER
    val background = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start
    var pendingConfirmAction by remember { mutableStateOf<AssistantAction?>(null) }

    // Confirmation dialog
    pendingConfirmAction?.let { action ->
        ConfirmActionDialog(
            action = action,
            onConfirm = {
                onAction(action)
                pendingConfirmAction = null
            },
            onDismiss = { pendingConfirmAction = null }
        )
    }

    Column(horizontalAlignment = alignment, modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = background)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Confidence tier label (if present)
                entry.confidenceTier?.let { tier ->
                    ConfidenceLabel(tier)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = entry.message.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Evidence section (if present)
                if (entry.evidence.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EvidenceSection(evidence = entry.evidence)
                }

                // Suggested next photo (if present)
                entry.suggestedNextPhoto?.let { suggestion ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SuggestedPhotoHint(suggestion = suggestion)
                }
            }
        }

        if (entry.actions.isNotEmpty() && !isUser) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entry.actions.forEach { action ->
                    if (action.requiresConfirmation) {
                        OutlinedButton(onClick = { pendingConfirmAction = action }) {
                            Text(text = actionLabel(action), textAlign = TextAlign.Center)
                        }
                    } else {
                        Button(onClick = { onAction(action) }) {
                            Text(text = actionLabel(action), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceLabel(tier: ConfidenceTier) {
    val (label, color) = when (tier) {
        ConfidenceTier.HIGH -> "High confidence" to MaterialTheme.colorScheme.primary
        ConfidenceTier.MED -> "Likely" to MaterialTheme.colorScheme.tertiary
        ConfidenceTier.LOW -> "Uncertain" to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun EvidenceSection(evidence: List<EvidenceBullet>) {
    Column {
        Text(
            text = "Based on:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        evidence.forEach { bullet ->
            Text(
                text = "• ${bullet.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SuggestedPhotoHint(suggestion: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun ConfirmActionDialog(
    action: AssistantAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm action") },
        text = {
            Text("This suggestion has lower confidence. Do you want to apply it anyway?")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadingStageIndicator(stage: String, showProgress: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showProgress) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.height(16.dp).width(16.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showProgress) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RetryBanner(onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Request failed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun SmartSuggestionsRow(
    suggestions: List<String>,
    onActionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onActionSelected(suggestion) },
                label = { Text(suggestion) }
            )
        }
    }
}

@Composable
private fun QuickActionChip(label: String, onActionSelected: (String) -> Unit) {
    AssistChip(onClick = { onActionSelected(label) }, label = { Text(label) })
}

private fun handleAssistantAction(
    action: AssistantAction,
    state: AssistantUiState,
    viewModel: AssistantViewModel,
    onOpenPostingAssist: (List<String>, Int) -> Unit,
    onShare: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onSuggestNextPhoto: (String) -> Unit = {}
) {
    when (action.type) {
        AssistantActionType.APPLY_DRAFT_UPDATE -> viewModel.applyDraftUpdate(action)
        AssistantActionType.ADD_ATTRIBUTES -> viewModel.addAttributes(action)
        AssistantActionType.COPY_TEXT -> {
            val label = action.payload["label"] ?: "Text"
            val text = action.payload["text"] ?: return
            onCopyText(label, text)
        }
        AssistantActionType.OPEN_POSTING_ASSIST -> {
            val itemId = action.payload["itemId"]
            val index = itemId?.let { state.itemIds.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            onOpenPostingAssist(state.itemIds, index)
        }
        AssistantActionType.OPEN_SHARE -> {
            val itemId = action.payload["itemId"] ?: state.itemIds.firstOrNull() ?: return
            onShare(itemId)
        }
        AssistantActionType.OPEN_URL -> {
            val url = action.payload["url"] ?: return
            onOpenUrl(url)
        }
        AssistantActionType.SUGGEST_NEXT_PHOTO -> {
            val suggestion = action.payload["suggestion"] ?: return
            onSuggestNextPhoto(suggestion)
        }
    }
}

private fun actionLabel(action: AssistantAction): String {
    // Use provided label if available
    action.label?.let { return it }

    return when (action.type) {
        AssistantActionType.ADD_ATTRIBUTES -> "Add attributes"
        AssistantActionType.APPLY_DRAFT_UPDATE -> "Apply update"
        AssistantActionType.COPY_TEXT -> action.payload["label"]?.let { "Copy $it" } ?: "Copy text"
        AssistantActionType.OPEN_POSTING_ASSIST -> "Open Posting Assist"
        AssistantActionType.OPEN_SHARE -> "Share draft"
        AssistantActionType.OPEN_URL -> "Open link"
        AssistantActionType.SUGGEST_NEXT_PHOTO -> "Take photo"
    }
}
