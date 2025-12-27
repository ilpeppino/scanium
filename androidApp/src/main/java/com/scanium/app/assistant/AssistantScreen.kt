package com.scanium.app.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scanium.app.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.data.SettingsRepository
import com.scanium.app.voice.VoiceController
import com.scanium.app.voice.VoiceResult
import com.scanium.app.voice.VoiceState
import com.scanium.shared.core.models.assistant.AssistantAction
import com.scanium.shared.core.models.assistant.AssistantActionType
import com.scanium.shared.core.models.assistant.AssistantMessage
import com.scanium.shared.core.models.assistant.AssistantRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AssistantViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Voice controller and settings
    val settingsRepository = remember { SettingsRepository(context) }
    val voiceController = remember { VoiceController(context) }
    var lastSpokenTimestamp by remember { mutableStateOf<Long?>(null) }

    // Voice mode settings
    val voiceModeEnabled by settingsRepository.voiceModeEnabledFlow.collectAsState(initial = false)
    val speakAnswersEnabled by settingsRepository.speakAnswersEnabledFlow.collectAsState(initial = false)
    val autoSendTranscript by settingsRepository.autoSendTranscriptFlow.collectAsState(initial = false)
    val voiceLanguage by settingsRepository.voiceLanguageFlow.collectAsState(initial = "")
    val assistantLanguage by settingsRepository.assistantLanguageFlow.collectAsState(initial = "EN")

    // Voice state from controller
    val voiceState by voiceController.voiceState.collectAsState()
    val partialTranscript by voiceController.partialTranscript.collectAsState()
    val lastVoiceError by voiceController.lastError.collectAsState()
    val speechAvailable = voiceController.isSpeechAvailable

    // Update voice language when settings change
    LaunchedEffect(voiceLanguage, assistantLanguage) {
        val effectiveLanguage = voiceLanguage.ifEmpty { assistantLanguage }
        voiceController.setLanguage(effectiveLanguage)
    }

    // Initialize TTS if speak answers is enabled
    LaunchedEffect(speakAnswersEnabled) {
        if (speakAnswersEnabled) {
            voiceController.initializeTts()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isLoading, uiState.pendingActions.size) {
        if (uiState.messages.isNotEmpty()) {
            // Calculate total items including messages, loading indicator, and pending actions
            val totalItems = uiState.messages.size +
                (if (uiState.isLoading) 1 else 0) +
                (if (uiState.error != null) 1 else 0) +
                (if (uiState.pendingActions.isNotEmpty()) 1 else 0)

            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    // Auto-speak assistant responses when enabled
    LaunchedEffect(uiState.messages, speakAnswersEnabled) {
        if (!speakAnswersEnabled) return@LaunchedEffect
        val lastAssistant = uiState.messages.lastOrNull { it.role == AssistantRole.ASSISTANT }
        val timestamp = lastAssistant?.timestamp
        if (timestamp != null && timestamp != lastSpokenTimestamp) {
            voiceController.speak(lastAssistant.content)
            lastSpokenTimestamp = timestamp
        }
    }

    // Stop voice when leaving screen (dispose)
    DisposableEffect(Unit) {
        onDispose { voiceController.shutdown() }
    }

    // Stop voice recording when app goes to background (lifecycle-aware)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Stop any active voice recording when app goes to background
                    // This ensures no background mic usage
                    voiceController.stopListening()
                    voiceController.stopSpeaking()
                }
                else -> { /* No action needed for other lifecycle events */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle voice recognition result callback
    val handleVoiceResult: (VoiceResult) -> Unit = { result ->
        when (result) {
            is VoiceResult.Success -> {
                inputText = result.transcript
                if (autoSendTranscript && result.transcript.isNotBlank()) {
                    viewModel.sendMessage(result.transcript)
                    inputText = ""
                }
            }
            is VoiceResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(result.message) }
            }
            is VoiceResult.Cancelled -> {
                // User cancelled, no action needed
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening(handleVoiceResult)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show speaking indicator and stop button in top bar
                    if (voiceState == VoiceState.SPEAKING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speaking...", style = MaterialTheme.typography.labelMedium)
                            IconButton(
                                onClick = { voiceController.stopSpeaking() },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = "Stop reading aloud" }
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_stop_speaking))
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding() // Pushes content up when keyboard opens
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
                state = listState,
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

            // Voice listening indicator
            if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.TRANSCRIBING) {
                VoiceListeningIndicator(
                    state = voiceState,
                    partialTranscript = partialTranscript,
                    onStop = { voiceController.stopListening() }
                )
            }

            // Voice error banner
            if (voiceState == VoiceState.ERROR && lastVoiceError != null) {
                VoiceErrorBanner(
                    message = lastVoiceError!!,
                    retryEnabled = speechAvailable,
                    onRetry = {
                        if (speechAvailable) {
                            voiceController.startListening(handleVoiceResult)
                        }
                    },
                    onDismiss = { voiceController.stopListening() }
                )
            }

            // Voice unavailable banner
            if (voiceModeEnabled && !speechAvailable) {
                VoiceUnavailableBanner()
            }

            // Input Area with voice button
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
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice icon button - only show if voice mode is enabled
                            if (voiceModeEnabled) {
                                val isListening = voiceState == VoiceState.LISTENING
                                val isTranscribing = voiceState == VoiceState.TRANSCRIBING
                                val isActive = isListening || isTranscribing

                                // Animate mic button color when active
                                val micColor by animateColorAsState(
                                    targetValue = when {
                                        isListening -> MaterialTheme.colorScheme.error
                                        isTranscribing -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(300),
                                    label = "micColor"
                                )

                                IconButton(
                                    onClick = {
                                        when {
                                            !speechAvailable -> Unit
                                            isActive -> voiceController.stopListening()
                                            else -> {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    voiceController.startListening(handleVoiceResult)
                                                } else {
                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        }
                                    },
                                    enabled = speechAvailable,
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics {
                                            contentDescription = if (isActive) {
                                                "Stop voice input"
                                            } else {
                                                "Start voice input"
                                            }
                                        }
                                ) {
                                    val icon = when {
                                        !speechAvailable -> Icons.Default.MicOff
                                        isActive -> Icons.Default.Stop
                                        else -> Icons.Default.Mic
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = when {
                                            !speechAvailable -> stringResource(R.string.cd_voice_unavailable)
                                            isActive -> stringResource(R.string.cd_stop_speaking)
                                            else -> "Start voice input"
                                        },
                                        tint = micColor
                                    )
                                }
                            }

                            // Send icon button
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank() && !uiState.isLoading) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                enabled = inputText.isNotBlank() && !uiState.isLoading,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = "Send message" }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank() && !uiState.isLoading) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        }
                    },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !uiState.isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    })
                )
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

/**
 * Visual indicator shown when voice is actively listening or transcribing.
 */
@Composable
private fun VoiceListeningIndicator(
    state: VoiceState,
    partialTranscript: String,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                VoiceState.LISTENING -> MaterialTheme.colorScheme.errorContainer
                VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = when (state) {
                        VoiceState.LISTENING -> MaterialTheme.colorScheme.error
                        VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            VoiceState.LISTENING -> "Listening..."
                            VoiceState.TRANSCRIBING -> "Transcribing..."
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (state) {
                            VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer
                            VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = partialTranscript,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state) {
                                VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Stop voice input" }
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.cd_stop_speaking),
                    tint = when (state) {
                        VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer
                        VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceErrorBanner(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Tap retry or edit and send manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onRetry, enabled = retryEnabled) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun VoiceUnavailableBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = stringResource(R.string.cd_voice_unavailable),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = "Voice input unavailable on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "You can keep typing questions while we disable the mic button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
