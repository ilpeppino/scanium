package com.scanium.app.selling.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.R
import com.scanium.app.audio.AppSound
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.di.AssistantViewModelFactoryEntryPoint
import com.scanium.app.items.ItemAttributeLocalizer
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.listing.ListingDraftFormatter
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.EvidenceBullet
import com.scanium.app.selling.assistant.components.VisionConflictDialog
import com.scanium.app.selling.assistant.components.VisionInsightsSection
import com.scanium.app.selling.assistant.local.LocalSuggestionsCard
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.model.SuggestedAttribute
import com.scanium.app.selling.util.ListingClipboardHelper
import com.scanium.app.selling.util.ListingShareHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Screen for AI-powered listing assistance.
 *
 * Part of ARCH-001/DX-003: Updated to use Hilt's assisted injection for ViewModel creation,
 * reducing boilerplate by accessing the factory through EntryPoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    itemIds: List<String>,
    onBack: () -> Unit,
    onOpenPostingAssist: (List<String>, Int) -> Unit,
    itemsViewModel: ItemsViewModel,
    draftStore: ListingDraftStore,
) {
    val context = LocalContext.current
    val settingsRepository = remember { com.scanium.app.data.SettingsRepository(context) }
    val assistedFactory =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AssistantViewModelFactoryEntryPoint::class.java,
            ).assistantViewModelFactory()
        }
    val viewModel: AssistantViewModel =
        viewModel(
            factory =
                AssistantViewModel.provideFactory(
                    assistedFactory = assistedFactory,
                    itemIds = itemIds,
                    itemsViewModel = itemsViewModel,
                ),
        )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val soundManager = LocalSoundManager.current
    var inputText by remember { mutableStateOf("") }
    // Vision conflict dialog state: Pair of (suggested attribute, existing value)
    var pendingConflictAttribute by remember { mutableStateOf<Pair<SuggestedAttribute, String>?>(null) }
    val voiceController = remember { AssistantVoiceController(context) }
    var lastSpokenTimestamp by remember { mutableStateOf<Long?>(null) }
    var lastSoundedAssistantTimestamp by remember { mutableStateOf<Long?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    // Voice mode settings
    val voiceModeEnabled by settingsRepository.voiceModeEnabledFlow.collectAsState(initial = false)
    val speakAnswersEnabled by settingsRepository.speakAnswersEnabledFlow.collectAsState(initial = false)
    val autoSendTranscript by settingsRepository.autoSendTranscriptFlow.collectAsState(initial = false)
    val voiceLanguage by settingsRepository.voiceLanguageFlow.collectAsState(initial = "")
    val assistantLanguage by settingsRepository.assistantLanguageFlow.collectAsState(initial = "EN")
    val assistantHapticsEnabled by settingsRepository.assistantHapticsEnabledFlow.collectAsState(initial = false)

    // Unified Settings: Use effective TTS language
    val effectiveTtsLanguage by settingsRepository.effectiveTtsLanguageFlow.collectAsState(initial = "EN")

    // Voice state from controller
    val voiceState by voiceController.voiceState.collectAsState()
    val partialTranscript by voiceController.partialTranscript.collectAsState()
    val latestAssistantTimestamp by remember(state.entries) {
        derivedStateOf {
            state.entries.lastOrNull { it.message.role == AssistantRole.ASSISTANT }
                ?.message?.timestamp
        }
    }
    val lastVoiceError by voiceController.lastError.collectAsState()
    val speechAvailable = voiceController.isSpeechAvailable

    // Update voice language when effective TTS language changes (Unified Settings)
    LaunchedEffect(effectiveTtsLanguage) {
        voiceController.setLanguage(effectiveTtsLanguage)
    }

    // Initialize TTS if speak answers is enabled
    LaunchedEffect(speakAnswersEnabled) {
        if (speakAnswersEnabled) {
            voiceController.initializeTts()
        }
    }

    // Stop voice and cancel warm-up when leaving screen (dispose)
    DisposableEffect(Unit) {
        onDispose {
            voiceController.shutdown()
            viewModel.cancelWarmUp()
        }
    }

    // Stop voice recording when app goes to background (lifecycle-aware)
    // Also refresh availability on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        // Stop any active voice recording when app goes to background
                        // This ensures no background mic usage
                        voiceController.stopListening()
                        voiceController.stopSpeaking()
                        // Cancel warm-up to avoid background network activity
                        viewModel.cancelWarmUp()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        // Re-evaluate assistant availability when returning to screen
                        // This handles cases like network reconnection while app was backgrounded
                        viewModel.refreshAvailability()
                        // Run preflight check on resume (uses cache if recent)
                        viewModel.runPreflight(forceRefresh = false)
                    }
                    else -> { /* No action needed for other lifecycle events */ }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            if (event is AssistantUiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Auto-speak assistant responses when enabled
    LaunchedEffect(state.entries, speakAnswersEnabled) {
        if (!speakAnswersEnabled) return@LaunchedEffect
        val lastAssistant = state.entries.lastOrNull { it.message.role == AssistantRole.ASSISTANT }
        val timestamp = lastAssistant?.message?.timestamp
        if (timestamp != null && timestamp != lastSpokenTimestamp) {
            voiceController.speak(lastAssistant.message.content)
            lastSpokenTimestamp = timestamp
        }
    }

    LaunchedEffect(state.entries, inputText, voiceState) {
        val lastAssistant = state.entries.lastOrNull { it.message.role == AssistantRole.ASSISTANT }
        val timestamp = lastAssistant?.message?.timestamp
        val shouldPlay =
            timestamp != null &&
                timestamp != lastSoundedAssistantTimestamp &&
                inputText.isBlank() &&
                voiceState != VoiceState.LISTENING &&
                voiceState != VoiceState.TRANSCRIBING
        if (shouldPlay) {
            soundManager.play(AppSound.RECEIVED)
            lastSoundedAssistantTimestamp = timestamp
        }
    }

    // Handle voice recognition result callback
    val handleVoiceResult: (VoiceResult) -> Unit = { result ->
        when (result) {
            is VoiceResult.Success -> {
                inputText = result.transcript
                if (autoSendTranscript && result.transcript.isNotBlank()) {
                    soundManager.play(AppSound.SEND)
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

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                voiceController.startListening(handleVoiceResult)
            } else {
                scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
            }
        }

    // Vision Conflict Dialog
    pendingConflictAttribute?.let { (attr, existingValue) ->
        VisionConflictDialog(
            attribute = attr,
            existingValue = existingValue,
            alternativeKey = viewModel.getAlternativeKey(attr.key),
            onReplace = {
                viewModel.applyVisionAttribute(attr)
                pendingConflictAttribute = null
            },
            onAddAlternative = {
                val altKey = viewModel.getAlternativeKey(attr.key)
                viewModel.applyVisionAttribute(attr, alternativeKey = altKey)
                pendingConflictAttribute = null
            },
            onDismiss = { pendingConflictAttribute = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Assistant") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (voiceState == VoiceState.SPEAKING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speaking...", style = MaterialTheme.typography.labelMedium)
                            IconButton(
                                onClick = { voiceController.stopSpeaking() },
                                modifier =
                                    Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics { contentDescription = "Stop reading aloud" },
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_stop_speaking))
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                            onAction = { action ->
                                handleAssistantAction(
                                    action = action,
                                    state = state,
                                    viewModel = viewModel,
                                    hapticFeedback = hapticFeedback,
                                    hapticsEnabled = assistantHapticsEnabled,
                                    onOpenPostingAssist = onOpenPostingAssist,
                                    onShare = { itemId ->
                                        scope.launch {
                                            val draft =
                                                draftStore.getByItemId(itemId)
                                                    ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                                                        ?.let { ListingDraftBuilder.build(it) }
                                            if (draft == null) {
                                                snackbarHostState.showSnackbar("No draft to share")
                                                return@launch
                                            }
                                            val profile =
                                                state.profile.takeIf { it.id == draft.profile }
                                                    ?: ExportProfiles.generic()

                                            // Localize condition
                                            val localizedCondition = draft.fields[DraftFieldKey.CONDITION]?.value?.let {
                                                ItemAttributeLocalizer.localizeCondition(context, it)
                                            }
                                            val exportDraft = if (localizedCondition != null) {
                                                val newFields = draft.fields.toMutableMap()
                                                newFields[DraftFieldKey.CONDITION] = newFields[DraftFieldKey.CONDITION]?.copy(value = localizedCondition)
                                                    ?: DraftField(value = localizedCondition, confidence = 1.0f, source = DraftProvenance.USER_EDITED)
                                                draft.copy(fields = newFields)
                                            } else draft

                                            val export = ListingDraftFormatter.format(exportDraft, profile)
                                            val currentItem = itemsViewModel.items.value.firstOrNull { it.id == draft.itemId }
                                            val shareImages =
                                                draft.photos.map { it.image }.ifEmpty {
                                                    listOfNotNull(currentItem?.thumbnailRef ?: currentItem?.thumbnail)
                                                }
                                            val imageUris =
                                                ListingShareHelper.writeShareImages(
                                                    context = context,
                                                    itemId = draft.itemId,
                                                    images = shareImages,
                                                )
                                            val intent =
                                                ListingShareHelper.buildShareIntent(
                                                    contentResolver = context.contentResolver,
                                                    text = export.shareText,
                                                    imageUris = imageUris,
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
                                    },
                                )
                            },
                            // Vision Insights integration
                            onApplyVisionAttribute = { attr ->
                                viewModel.applyVisionAttribute(attr)
                            },
                            onVisionAttributeConflict = { attr, existingValue ->
                                pendingConflictAttribute = attr to existingValue
                            },
                            getExistingAttribute = { key ->
                                viewModel.getExistingAttribute(key)
                            },
                            visionInsightsEnabled = true,
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Show local suggestions when assistant is unavailable
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
                                    ListingClipboardHelper.copy(context, label, text)
                                    scope.launch { snackbarHostState.showSnackbar("$label copied") }
                                },
                                onApplyDescription = { description ->
                                    viewModel.applyLocalSuggestedDescription(description)
                                },
                                onApplyTitle = { title ->
                                    viewModel.applyLocalSuggestedTitle(title)
                                },
                                onQuestionSelected = { question ->
                                    // When assistant is available again, send the question
                                    // For now, just copy it to input
                                    inputText = question
                                },
                            )
                        }
                    }
                }
            }

            // Show availability banner when assistant is unavailable (not just after a failed message)
            val availabilityBanner = state.availability
            if (availabilityBanner is AssistantAvailability.Unavailable && availabilityBanner.reason != UnavailableReason.LOADING) {
                AssistantUnavailableBanner(
                    availability = availabilityBanner,
                    failure = state.lastBackendFailure,
                    onRetry = {
                        // Run preflight check first, then retry message if available
                        viewModel.runPreflight(forceRefresh = true)
                    },
                    onDismiss = {
                        // Just clear the failure state without retrying
                        viewModel.clearFailureState()
                    },
                )
            }

            // Show non-blocking preflight warning banner (input still enabled)
            // Only show if availability is Available but there's a preflight issue
            val preflightWarning = state.preflightWarning
            if (preflightWarning != null && availabilityBanner is AssistantAvailability.Available) {
                PreflightWarningBanner(
                    warning = preflightWarning,
                    onDismiss = {
                        viewModel.clearPreflightWarning()
                    },
                )
            }

            // Rich progress indicator with animated transitions
            ProgressIndicatorSection(
                progress = state.progress,
                onRetry = { viewModel.retryLastMessage() },
            )

            // Voice listening indicator
            if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.TRANSCRIBING) {
                VoiceListeningIndicator(
                    state = voiceState,
                    partialTranscript = partialTranscript,
                    onStop = { voiceController.stopListening() },
                )
            }

            if (voiceState == VoiceState.ERROR && lastVoiceError != null) {
                VoiceErrorBanner(
                    message = lastVoiceError!!,
                    retryEnabled = speechAvailable,
                    onRetry = {
                        if (speechAvailable) {
                            voiceController.startListening(handleVoiceResult)
                        }
                    },
                    onDismiss = { voiceController.stopListening() },
                )
            }

            if (voiceModeEnabled && !speechAvailable) {
                VoiceUnavailableBanner()
            }

            // Smart suggested questions (context-aware)
            // Disabled when assistant is unavailable
            SmartSuggestionsRow(
                suggestions =
                    state.suggestedQuestions.ifEmpty {
                        listOf("Suggest a better title", "What details should I add?", "Estimate price range")
                    },
                enabled = state.isInputEnabled,
                onActionSelected = { actionText ->
                    if (state.isInputEnabled) {
                        inputText = actionText
                        soundManager.play(AppSound.SEND)
                        viewModel.sendMessage(actionText)
                    }
                },
            )

            // ChatGPT-like input field with embedded icons
            // Disabled when assistant is unavailable (offline, error, loading)
            val inputEnabled = state.isInputEnabled
            TextField(
                value = inputText,
                onValueChange = { if (inputEnabled) inputText = it },
                enabled = inputEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics { traversalIndex = 3f }
                        .navigationBarsPadding()
                        .imePadding(),
                placeholder = {
                    Text(
                        text = state.inputPlaceholder,
                        color = if (!inputEnabled) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Voice icon button - only show if voice mode is enabled
                        if (voiceModeEnabled) {
                            val isListening = voiceState == VoiceState.LISTENING
                            val isTranscribing = voiceState == VoiceState.TRANSCRIBING
                            val isActive = isListening || isTranscribing

                            // Animate mic button color when active
                            val micColor by animateColorAsState(
                                targetValue =
                                    when {
                                        isListening -> MaterialTheme.colorScheme.error
                                        isTranscribing -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                animationSpec = tween(300),
                                label = "micColor",
                            )

                            IconButton(
                                onClick = {
                                    when {
                                        !speechAvailable -> Unit
                                        isActive -> voiceController.stopListening()
                                        else -> {
                                            val hasPermission =
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO,
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
                                modifier =
                                    Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics {
                                            contentDescription =
                                                if (isActive) {
                                                    "Stop voice input"
                                                } else {
                                                    "Start voice input"
                                                }
                                        },
                            ) {
                                val icon =
                                    when {
                                        !speechAvailable -> Icons.Default.MicOff
                                        isActive -> Icons.Default.Stop
                                        else -> Icons.Default.Mic
                                    }
                                val description =
                                    when {
                                        !speechAvailable -> "Voice input unavailable"
                                        isActive -> "Stop listening"
                                        else -> "Voice input"
                                    }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = description,
                                    tint = micColor,
                                )
                            }
                        }

                        // Send icon button - disabled when assistant unavailable
                        val canSend = inputText.isNotBlank() && inputEnabled
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    val text = inputText
                                    inputText = ""
                                    if (assistantHapticsEnabled) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    soundManager.play(AppSound.SEND)
                                    viewModel.sendMessage(text)
                                }
                            },
                            enabled = canSend,
                            modifier =
                                Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
                                        contentDescription = if (inputEnabled) "Send message" else "Send unavailable"
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint =
                                    if (canSend) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        }
                    }
                },
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            // Only allow send if input is enabled (assistant available)
                            if (inputText.isNotBlank() && inputEnabled) {
                                val text = inputText
                                inputText = ""
                                if (assistantHapticsEnabled) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                soundManager.play(AppSound.SEND)
                                viewModel.sendMessage(text)
                            }
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    entry: AssistantChatEntry,
    modifier: Modifier = Modifier,
    actionTraversalIndex: Float? = null,
    onAction: (AssistantAction) -> Unit,
    // Vision insights callbacks
    onApplyVisionAttribute: ((SuggestedAttribute) -> Unit)? = null,
    onVisionAttributeConflict: ((SuggestedAttribute, String) -> Unit)? = null,
    getExistingAttribute: ((String) -> com.scanium.shared.core.models.items.ItemAttribute?)? = null,
    visionInsightsEnabled: Boolean = false,
) {
    // Only animate entrance for the assistant's latest message or user message
    // We rely on the parent LazyColumn's state for "newness", but here we just animate appearance
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + 
                expandVertically(expandFrom = Alignment.Bottom) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
        modifier = modifier.fillMaxWidth()
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
                onDismiss = { pendingConfirmAction = null },
            )
        }

        Column(horizontalAlignment = alignment, modifier = Modifier.fillMaxWidth()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = background),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Confidence tier label (if present)
                    entry.confidenceTier?.let { tier ->
                        ConfidenceLabel(tier)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = entry.message.content,
                        style = MaterialTheme.typography.bodyMedium,
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

            // Vision Insights section (for assistant messages with suggested attributes)
            if (!isUser &&
                visionInsightsEnabled &&
                entry.suggestedAttributes.isNotEmpty() &&
                onApplyVisionAttribute != null &&
                onVisionAttributeConflict != null &&
                getExistingAttribute != null
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                VisionInsightsSection(
                    suggestedAttributes = entry.suggestedAttributes,
                    onApplyAttribute = onApplyVisionAttribute,
                    onAttributeConflict = onVisionAttributeConflict,
                    getExistingAttribute = getExistingAttribute,
                    enabled = true,
                )
            }

            if (entry.actions.isNotEmpty() && !isUser) {
                Row(
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState())
                            .then(
                                if (actionTraversalIndex != null) {
                                    Modifier.semantics { traversalIndex = actionTraversalIndex }
                                } else {
                                    Modifier
                                },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.actions.forEach { action ->
                        if (action.requiresConfirmation) {
                            OutlinedButton(
                                onClick = { pendingConfirmAction = action },
                                modifier =
                                    Modifier
                                        .sizeIn(minHeight = 48.dp)
                                        .semantics { contentDescription = actionContentDescription(action) },
                            ) {
                                Text(text = actionLabel(action), textAlign = TextAlign.Center)
                            }
                        } else {
                            Button(
                                onClick = { onAction(action) },
                                modifier =
                                    Modifier
                                        .sizeIn(minHeight = 48.dp)
                                        .semantics { contentDescription = actionContentDescription(action) },
                            ) {
                                Text(text = actionLabel(action), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceLabel(tier: ConfidenceTier) {
    val (label, color) =
        when (tier) {
            ConfidenceTier.HIGH -> "High confidence" to MaterialTheme.colorScheme.primary
            ConfidenceTier.MED -> "Likely" to MaterialTheme.colorScheme.tertiary
            ConfidenceTier.LOW -> "Uncertain" to MaterialTheme.colorScheme.error
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun EvidenceSection(evidence: List<EvidenceBullet>) {
    Column {
        Text(
            text = "Based on:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        evidence.forEach { bullet ->
            Text(
                text = "• ${bullet.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SuggestedPhotoHint(suggestion: String) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun ConfirmActionDialog(
    action: AssistantAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
        },
    )
}

/**
 * Rich progress indicator section with animated transitions.
 * Shows detailed progress states during assistant request lifecycle.
 */
@VisibleForTesting
@Composable
internal fun ProgressIndicatorSection(
    progress: AssistantRequestProgress,
    onRetry: () -> Unit,
) {
    // Use Crossfade for smooth transitions between progress states
    Crossfade(
        targetState = progress,
        animationSpec = tween(durationMillis = 200),
        label = "progress_crossfade",
    ) { currentProgress ->
        when (currentProgress) {
            is AssistantRequestProgress.Idle,
            is AssistantRequestProgress.Done -> {
                // Empty spacer to maintain layout stability
                Spacer(modifier = Modifier.height(0.dp))
            }
            is AssistantRequestProgress.Sending -> {
                ProgressStageRow(
                    label = "Sending...",
                    showSpinner = true,
                    showProgressBar = true,
                )
            }
            is AssistantRequestProgress.Thinking -> {
                ProgressStageRow(
                    label = "Thinking...",
                    showSpinner = true,
                    showProgressBar = true,
                )
            }
            is AssistantRequestProgress.ExtractingVision -> {
                val imageText = if (currentProgress.imageCount > 1) {
                    "Analyzing ${currentProgress.imageCount} images..."
                } else {
                    "Analyzing image..."
                }
                ProgressStageRow(
                    label = imageText,
                    showSpinner = true,
                    showProgressBar = true,
                )
            }
            is AssistantRequestProgress.Drafting -> {
                ProgressStageRow(
                    label = "Drafting answer...",
                    showSpinner = true,
                    showProgressBar = true,
                )
            }
            is AssistantRequestProgress.Finalizing -> {
                ProgressStageRow(
                    label = "Finalizing...",
                    showSpinner = true,
                    showProgressBar = true,
                )
            }
            is AssistantRequestProgress.ErrorTemporary -> {
                if (currentProgress.retryable) {
                    RetryBanner(onRetry = onRetry)
                } else {
                    ProgressErrorBanner(message = currentProgress.message)
                }
            }
            is AssistantRequestProgress.ErrorAuth -> {
                ProgressErrorBanner(message = currentProgress.message)
            }
            is AssistantRequestProgress.ErrorValidation -> {
                ProgressErrorBanner(message = currentProgress.message)
            }
        }
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dotSize = 6.dp
    val spaceBetween = 4.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spaceBetween)
    ) {
        val dots = listOf(0, 150, 300)
        dots.forEach { delay ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -6f, // Bounce up by 6 pixels
                animationSpec = infiniteRepeatable(
                    animation = tween(300, delayMillis = delay, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_offset"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset { IntOffset(0, offset.toInt()) }
                    .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

/**
 * A single-line progress row with optional spinner and progress bar.
 * Maintains stable height to prevent layout jumps.
 */
@Composable
private fun ProgressStageRow(
    label: String,
    showSpinner: Boolean,
    showProgressBar: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(24.dp), // Increased height for bounce
        ) {
            if (showSpinner) {
                if (label.contains("Thinking") || label.contains("Drafting")) {
                    TypingIndicator(modifier = Modifier.padding(end = 4.dp))
                } else {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (showProgressBar) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

/**
 * Error banner for non-retryable errors.
 */
@Composable
private fun ProgressErrorBanner(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun LoadingStageIndicator(
    stage: String,
    showProgress: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showProgress) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.height(16.dp).width(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Request failed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun AssistantModeIndicator(
    mode: AssistantMode,
    failure: AssistantBackendFailure? = null,
    isChecking: Boolean = false,
) {
    val isOnline = mode == AssistantMode.ONLINE && !isChecking

    val (label, backgroundColor, contentColor) =
        when {
            isChecking ->
                Triple(
                    "Checking assistant...",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            isOnline ->
                Triple(
                    "Online assistant",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            mode == AssistantMode.OFFLINE ->
                Triple(
                    "Limited offline assistance",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            else ->
                Triple(
                    "Limited offline assistance",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
        }

    Row(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics { traversalIndex = -1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Status dot indicator or progress indicator when checking
        if (isChecking) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(8.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(8.dp),
            ) {
                drawCircle(
                    color =
                        if (isOnline) {
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFFFF9800)
                        },
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AssistantModeBanner(
    mode: AssistantMode,
    failure: AssistantBackendFailure?,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    // Use explicit error info when available
    val errorInfo = AssistantErrorDisplay.getErrorInfo(failure)

    val title =
        when {
            errorInfo != null -> errorInfo.title
            mode == AssistantMode.OFFLINE -> "Limited offline assistance"
            mode == AssistantMode.LIMITED -> "Limited offline assistance"
            else -> "Online assistant"
        }

    val detail =
        when {
            errorInfo != null -> errorInfo.explanation
            mode == AssistantMode.OFFLINE -> "You're offline. Only local suggestions based on your draft are available. No external lookups will be performed."
            mode == AssistantMode.LIMITED -> "The online assistant is temporarily unavailable. Only local suggestions based on your draft are shown."
            else -> ""
        }

    val actionHint = errorInfo?.actionHint

    // Determine container color based on error severity
    val containerColor =
        when {
            failure?.category == AssistantBackendErrorCategory.POLICY &&
                failure.type in
                listOf(
                    AssistantBackendErrorType.UNAUTHORIZED,
                    AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED,
                    AssistantBackendErrorType.VALIDATION_ERROR,
                )
            -> MaterialTheme.colorScheme.errorContainer
            failure != null -> MaterialTheme.colorScheme.tertiaryContainer
            mode == AssistantMode.OFFLINE -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }

    val contentColor =
        when {
            failure?.category == AssistantBackendErrorCategory.POLICY &&
                failure.type in
                listOf(
                    AssistantBackendErrorType.UNAUTHORIZED,
                    AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED,
                    AssistantBackendErrorType.VALIDATION_ERROR,
                )
            -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }

    val showRetryButton = errorInfo?.showRetry ?: (mode != AssistantMode.ONLINE)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            // Error type label for debugging
            failure?.let {
                Text(
                    text = AssistantErrorDisplay.getStatusLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
            )

            // Show action hint if available
            actionHint?.let { hint ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }

            // Show rate limit countdown if applicable
            failure?.retryAfterSeconds?.let { seconds ->
                if (seconds > 0 && failure.type == AssistantBackendErrorType.RATE_LIMITED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Retry available in ${seconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }

            if (showRetryButton) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier.semantics { contentDescription = "Retry online" },
                ) {
                    Text("Retry online")
                }
            }
        }
    }
}

/**
 * Banner shown when assistant is unavailable.
 * Provides clear messaging and recovery actions.
 */
@Composable
private fun AssistantUnavailableBanner(
    availability: AssistantAvailability.Unavailable,
    failure: AssistantBackendFailure?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, detail, showRetry) = when (availability.reason) {
        UnavailableReason.OFFLINE -> Triple(
            "You're offline",
            "Connect to the internet to use the AI assistant. Local suggestions are still available below.",
            true,
        )
        UnavailableReason.RATE_LIMITED -> {
            val retryText = availability.retryAfterSeconds?.let { " (wait ${it}s)" } ?: ""
            Triple(
                "Rate limit reached$retryText",
                "You've sent too many requests. Local suggestions are available while you wait.",
                true,
            )
        }
        UnavailableReason.UNAUTHORIZED -> Triple(
            "Authorization required",
            "Check your account status or sign in again. Local suggestions are available.",
            false,
        )
        UnavailableReason.NOT_CONFIGURED -> Triple(
            "Assistant not configured",
            "The AI assistant is not available in this build. Local suggestions are available.",
            false,
        )
        UnavailableReason.ENDPOINT_NOT_FOUND -> Triple(
            "Endpoint not found",
            "Preflight endpoint not found (check base URL / tunnel route). This is a configuration error.",
            false,
        )
        UnavailableReason.VALIDATION_ERROR -> Triple(
            "Request error",
            "There was a problem with the request. Try rephrasing your question.",
            false,
        )
        UnavailableReason.BACKEND_ERROR -> Triple(
            "Assistant temporarily unavailable",
            "The AI assistant is experiencing issues. Local suggestions are available.",
            true,
        )
        UnavailableReason.LOADING -> Triple(
            "Processing...",
            "Please wait while we process your request.",
            false,
        )
    }

    val containerColor = when (availability.reason) {
        UnavailableReason.UNAUTHORIZED,
        UnavailableReason.NOT_CONFIGURED,
        UnavailableReason.ENDPOINT_NOT_FOUND,
        UnavailableReason.VALIDATION_ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val contentColor = when (availability.reason) {
        UnavailableReason.UNAUTHORIZED,
        UnavailableReason.NOT_CONFIGURED,
        UnavailableReason.ENDPOINT_NOT_FOUND,
        UnavailableReason.VALIDATION_ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Status label for debugging
            failure?.let {
                Text(
                    text = AssistantErrorDisplay.getStatusLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showRetry && availability.canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.semantics { contentDescription = "Retry online assistant" },
                    ) {
                        Text("Retry")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Continue with local suggestions" },
                ) {
                    Text(if (showRetry && availability.canRetry) "Use local" else "OK")
                }
            }
        }
    }
}

/**
 * Non-blocking warning banner for preflight issues.
 *
 * Unlike [AssistantUnavailableBanner], this does NOT disable input.
 * User can still type and attempt to send messages.
 * Shown for CLIENT_ERROR, UNAUTHORIZED, etc. when the actual chat may still work.
 */
@Composable
private fun PreflightWarningBanner(
    warning: PreflightWarning,
    onDismiss: () -> Unit,
) {
    val (title, detail) = when (warning.status) {
        PreflightStatus.CLIENT_ERROR -> Pair(
            "Preflight check failed",
            "There was an issue with the health check, but you can still try sending messages. (${warning.reasonCode})",
        )
        PreflightStatus.UNAUTHORIZED -> Pair(
            "API key may be invalid",
            "Check your API key in Developer Settings. You can still try sending messages. (${warning.reasonCode})",
        )
        PreflightStatus.TEMPORARILY_UNAVAILABLE -> Pair(
            "Backend may be temporarily unavailable",
            "The server reported an issue, but you can still try sending messages. (${warning.reasonCode})",
        )
        PreflightStatus.RATE_LIMITED -> Pair(
            "Rate limit warning",
            "Preflight was rate limited, but your message may still go through. (${warning.reasonCode})",
        )
        else -> Pair(
            "Connection warning",
            "There was an issue checking connectivity. You can still try sending messages. (${warning.reasonCode})",
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun SmartSuggestionsRow(
    suggestions: List<String>,
    enabled: Boolean = true,
    onActionSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { if (enabled) onActionSelected(suggestion) },
                enabled = enabled,
                label = {
                    Text(
                        text = suggestion,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    onActionSelected: (String) -> Unit,
) {
    AssistChip(onClick = { onActionSelected(label) }, label = { Text(label) })
}

private fun handleAssistantAction(
    action: AssistantAction,
    state: AssistantUiState,
    viewModel: AssistantViewModel,
    hapticFeedback: HapticFeedback,
    hapticsEnabled: Boolean,
    onOpenPostingAssist: (List<String>, Int) -> Unit,
    onShare: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onSuggestNextPhoto: (String) -> Unit = {},
) {
    if (hapticsEnabled && action.type in
        setOf(
            AssistantActionType.APPLY_DRAFT_UPDATE,
            AssistantActionType.ADD_ATTRIBUTES,
            AssistantActionType.COPY_TEXT,
        )
    ) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
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

private fun actionContentDescription(action: AssistantAction): String {
    return when (action.type) {
        AssistantActionType.APPLY_DRAFT_UPDATE -> {
            when {
                action.payload.containsKey("title") -> "Apply title"
                action.payload.containsKey("description") -> "Apply description"
                action.payload.containsKey("price") -> "Apply price"
                else -> "Apply draft update"
            }
        }
        AssistantActionType.ADD_ATTRIBUTES -> "Apply attributes"
        AssistantActionType.COPY_TEXT -> {
            val label = action.payload["label"] ?: "text"
            "Copy $label"
        }
        AssistantActionType.OPEN_POSTING_ASSIST -> "Open posting assist"
        AssistantActionType.OPEN_SHARE -> "Share draft"
        AssistantActionType.OPEN_URL -> "Open link"
        AssistantActionType.SUGGEST_NEXT_PHOTO -> "Take next photo"
    }
}

/**
 * Visual indicator shown when voice is actively listening or transcribing.
 */
@Composable
private fun VoiceListeningIndicator(
    state: VoiceState,
    partialTranscript: String,
    onStop: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when (state) {
                        VoiceState.LISTENING -> MaterialTheme.colorScheme.errorContainer
                        VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Pulsing indicator
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color =
                        when (state) {
                            VoiceState.LISTENING -> MaterialTheme.colorScheme.error
                            VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            when (state) {
                                VoiceState.LISTENING -> "Listening..."
                                VoiceState.TRANSCRIBING -> "Transcribing..."
                                else -> ""
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (state) {
                                VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer
                                VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = partialTranscript,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when (state) {
                                    VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(
                onClick = onStop,
                modifier =
                    Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Stop voice input" },
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.cd_stop_speaking),
                    tint =
                        when (state) {
                            VoiceState.LISTENING -> MaterialTheme.colorScheme.onErrorContainer
                            VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Tap retry or edit and send manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = stringResource(R.string.cd_voice_unavailable),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = "Voice input unavailable on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "You can keep typing questions while we disable the mic button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}
