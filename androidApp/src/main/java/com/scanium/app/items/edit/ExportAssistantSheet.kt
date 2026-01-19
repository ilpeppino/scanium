package com.scanium.app.items.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.assistant.tts.TtsManager
import com.scanium.app.assistant.tts.buildSpeakableText
import com.scanium.app.data.SettingsRepository
import com.scanium.shared.core.models.assistant.ConfidenceTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Export Assistant bottom sheet for generating marketplace-ready listings.
 *
 * Features:
 * - Auto-generates title, description, and bullet points on open
 * - Shows loading state with "Drafting..." indicator
 * - Displays generated content with copy/apply actions
 * - Retry button for errors
 * - Confidence tier indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportAssistantSheet(
    viewModel: ExportAssistantViewModel,
    settingsRepository: SettingsRepository,
    ttsManager: TtsManager,
    onDismiss: () -> Unit,
    onApply: (title: String?, description: String?, bullets: List<String>) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // TTS settings
    val speakAnswersEnabled by settingsRepository.speakAnswersEnabledFlow.collectAsState(initial = false)

    // TtsManager automatically uses effectiveTtsLanguage from SettingsRepository
    // No need to manually manage language or lifecycle - it's a singleton

    // Stop TTS when state changes (e.g., regeneration)
    LaunchedEffect(state) {
        if (state.isLoading) {
            ttsManager.stop()
        }
    }

    // Auto-generate on first open if idle
    LaunchedEffect(Unit) {
        if (state is ExportAssistantState.Idle) {
            viewModel.generateExport()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ExportAssistantContent(
            state = state,
            speakAnswersEnabled = speakAnswersEnabled,
            isSpeaking = ttsManager.isSpeaking.value,
            onSpeakOrStop = { text ->
                if (ttsManager.isSpeaking.value) {
                    ttsManager.stop()
                } else {
                    ttsManager.speak(text)
                }
            },
            onGenerate = { viewModel.generateExport() },
            onRetry = { viewModel.retry() },
            onApplyResult = { title, description, bullets ->
                onApply(title, description, bullets)
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
            onDismiss = onDismiss,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * Pure UI content for the Export Assistant sheet.
 * Receives data and callbacks, no direct ViewModel usage.
 *
 * Made internal for testability.
 */
@Composable
internal fun ExportAssistantContent(
    state: ExportAssistantState,
    speakAnswersEnabled: Boolean,
    isSpeaking: Boolean,
    onSpeakOrStop: (text: String) -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onApplyResult: (title: String?, description: String?, bullets: List<String>) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        ExportAssistantHeader()

        HorizontalDivider()

        // Content based on state
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "export_state",
        ) { currentState ->
            when (currentState) {
                is ExportAssistantState.Idle -> {
                    // Show generate button
                    ExportIdleContent(
                        onGenerate = onGenerate,
                    )
                }

                is ExportAssistantState.Loading,
                is ExportAssistantState.Generating,
                -> {
                    ExportLoadingContent()
                }

                is ExportAssistantState.Success -> {
                    val titleLabel = stringResource(R.string.export_assistant_title_label)
                    val descriptionLabel = stringResource(R.string.export_assistant_description_label)
                    val bulletsLabel = stringResource(R.string.export_assistant_bullets_label)
                    val bulletSymbol = stringResource(R.string.common_bullet)

                    // Build speakable text for TTS
                    val speakableText =
                        remember(currentState) {
                            buildSpeakableText(
                                title = currentState.title,
                                description = currentState.description,
                                bullets = currentState.bullets,
                            )
                        }

                    ExportSuccessContent(
                        state = currentState,
                        speakAnswersEnabled = speakAnswersEnabled,
                        speakableText = speakableText,
                        isSpeaking = isSpeaking,
                        onToggleSpeech = { onSpeakOrStop(speakableText) },
                        onCopyAll = { copyAllToClipboard(context, currentState) },
                        onCopyTitle = {
                            currentState.title?.let {
                                copyToClipboard(context, titleLabel, it)
                            }
                        },
                        onCopyDescription = {
                            currentState.description?.let {
                                copyToClipboard(context, descriptionLabel, it)
                            }
                        },
                        onCopyBullets = {
                            copyToClipboard(
                                context,
                                bulletsLabel,
                                currentState.bullets.joinToString("\n") { "$bulletSymbol $it" },
                            )
                        },
                        onRegenerate = onGenerate,
                        onApply = {
                            onApplyResult(currentState.title, currentState.description, currentState.bullets)
                        },
                    )
                }

                is ExportAssistantState.Error -> {
                    ExportErrorContent(
                        message = currentState.message,
                        isRetryable = currentState.isRetryable,
                        onRetry = onRetry,
                        onDismiss = onDismiss,
                        onNavigateToSettings = onNavigateToSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportAssistantHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.export_assistant_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.export_assistant_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportIdleContent(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.export_assistant_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_assistant_generate_button))
        }
    }
}

@Composable
private fun ExportLoadingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
        )
        Text(
            text = stringResource(R.string.export_assistant_drafting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.export_assistant_drafting_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportSuccessContent(
    state: ExportAssistantState.Success,
    speakAnswersEnabled: Boolean,
    speakableText: String,
    isSpeaking: Boolean,
    onToggleSpeech: () -> Unit,
    onCopyAll: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyDescription: () -> Unit,
    onCopyBullets: () -> Unit,
    onRegenerate: () -> Unit,
    onApply: () -> Unit,
) {
    var showCopiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(2000)
            showCopiedFeedback = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header row with confidence tier and TTS button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Confidence tier indicator
            state.confidenceTier?.let { tier ->
                ConfidenceTierChip(tier = tier)
            } ?: Spacer(modifier = Modifier.width(0.dp))

            // TTS button (only shown if toggle is ON and content is available)
            if (speakAnswersEnabled && speakableText.isNotBlank()) {
                IconButton(
                    onClick = onToggleSpeech,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription =
                            if (isSpeaking) {
                                stringResource(R.string.common_stop_speaking)
                            } else {
                                stringResource(R.string.common_read_aloud)
                            },
                        tint =
                            if (isSpeaking) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }

        // Title section
        state.title?.let { title ->
            ExportContentCard(
                label = stringResource(R.string.export_assistant_title_label),
                content = title,
                onCopy = {
                    onCopyTitle()
                    showCopiedFeedback = true
                },
            )
        }

        // Description section
        state.description?.let { description ->
            ExportContentCard(
                label = stringResource(R.string.export_assistant_description_label),
                content = description,
                onCopy = {
                    onCopyDescription()
                    showCopiedFeedback = true
                },
                maxLines = 10,
            )
        }

        // Bullets section
        if (state.bullets.isNotEmpty()) {
            ExportBulletsCard(
                bullets = state.bullets,
                onCopy = {
                    onCopyBullets()
                    showCopiedFeedback = true
                },
            )
        }

        // Price Insights section (Phase 3)
        state.pricingInsights?.let { pricing ->
            PriceInsightsCard(pricingInsights = pricing)
        }

        // Copied feedback
        AnimatedVisibility(visible = showCopiedFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.common_copied_to_clipboard),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.common_regenerate))
            }

            FilledTonalButton(
                onClick = {
                    onCopyAll()
                    showCopiedFeedback = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.common_copy_all))
            }
        }

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_assistant_apply_to_item))
        }
    }
}

@Composable
internal fun ExportContentCard(
    label: String,
    content: String,
    onCopy: () -> Unit,
    maxLines: Int = 3,
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.common_copy_with_label, label),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max =
                                if (maxLines == Int.MAX_VALUE) {
                                    300.dp
                                } else {
                                    (maxLines * 20).dp.coerceAtMost(200.dp)
                                },
                        ).verticalScroll(scrollState),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun ExportBulletsCard(
    bullets: List<String>,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.export_assistant_highlights),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.export_assistant_copy_bullets),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            bullets.forEach { bullet ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.common_bullet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceTierChip(tier: ConfidenceTier) {
    val (text, color) =
        when (tier) {
            ConfidenceTier.HIGH -> stringResource(R.string.export_assistant_confidence_high) to MaterialTheme.colorScheme.primary
            ConfidenceTier.MED -> stringResource(R.string.export_assistant_confidence_medium) to MaterialTheme.colorScheme.tertiary
            ConfidenceTier.LOW -> stringResource(R.string.export_assistant_confidence_low) to MaterialTheme.colorScheme.error
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .padding(1.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(8.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun ExportErrorContent(
    message: String,
    isRetryable: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    // Check if error is about AI assistant being disabled
    val isAiDisabledError = message.contains("AI assistant is disabled", ignoreCase = true)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.export_assistant_generation_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Show "Go to Settings" button if AI is disabled
        if (isAiDisabledError) {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToSettings()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.assistant_auth_go_to_settings))
            }
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }

                if (isRetryable) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}

// ==================== Price Insights Card (Phase 3/5) ====================

@Composable
private fun PriceInsightsCard(pricingInsights: com.scanium.shared.core.models.assistant.PricingInsights) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Text(
                text = "Market price",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (pricingInsights.status.uppercase()) {
                "OK" -> {
                    pricingInsights.range?.let { range ->
                        // Price range with currency symbol
                        val currencySymbol = getCurrencySymbol(range.currency)
                        Text(
                            text = "$currencySymbol${range.low.toInt()}–$currencySymbol${range.high.toInt()}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        // Result count and confidence
                        val confidenceText =
                            when (pricingInsights.confidence) {
                                com.scanium.shared.core.models.assistant.PricingConfidence.HIGH -> " (high confidence)"
                                com.scanium.shared.core.models.assistant.PricingConfidence.MED -> ""
                                com.scanium.shared.core.models.assistant.PricingConfidence.LOW -> " (low confidence)"
                                null -> ""
                            }
                        Text(
                            text = "Based on ${pricingInsights.results.size} listing${if (pricingInsights.results.size != 1) "s" else ""}$confidenceText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Top results (up to 5)
                        if (pricingInsights.results.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Top results",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            pricingInsights.results.take(5).forEach { result ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                openUrl(context, result.url)
                                            }.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        // Show marketplace name from marketplacesUsed
                                        val marketplace =
                                            pricingInsights.marketplacesUsed
                                                .firstOrNull { it.id == result.sourceMarketplaceId }
                                        Text(
                                            text = marketplace?.name ?: result.sourceMarketplaceId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${getCurrencySymbol(result.price.currency)}${result.price.amount.toInt()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                "DISABLED" -> {
                    Text(
                        text = "Price insights are disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                "NOT_SUPPORTED" -> {
                    Text(
                        text = "Price insights not available for ${pricingInsights.countryCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                "NO_RESULTS" -> {
                    Text(
                        text = "No comparable listings found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                "TIMEOUT" -> {
                    Text(
                        text = "Request timed out",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                "ERROR" -> {
                    Text(
                        text = "Couldn't fetch prices right now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    Text(
                        text = "Loading price insights...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Get currency symbol for common currencies.
 * Falls back to currency code if symbol is unknown.
 */
private fun getCurrencySymbol(currencyCode: String): String =
    when (currencyCode.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "CHF" -> "CHF "
        "JPY" -> "¥"
        "CNY" -> "¥"
        else -> "$currencyCode "
    }

/**
 * Open URL in external browser.
 */
private fun openUrl(
    context: Context,
    url: String,
) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("ExportAssistant", "Failed to open URL: $url", e)
    }
}

// ==================== Clipboard Helpers ====================

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

private fun copyAllToClipboard(
    context: Context,
    state: ExportAssistantState.Success,
) {
    val sb = StringBuilder()

    state.title?.let {
        sb.appendLine(context.getString(R.string.export_assistant_clipboard_title_heading))
        sb.appendLine(it)
        sb.appendLine()
    }

    state.description?.let {
        sb.appendLine(context.getString(R.string.export_assistant_clipboard_description_heading))
        sb.appendLine(it)
        sb.appendLine()
    }

    if (state.bullets.isNotEmpty()) {
        sb.appendLine(context.getString(R.string.export_assistant_clipboard_highlights_heading))
        state.bullets.forEach { bullet ->
            sb.appendLine("${context.getString(R.string.common_bullet)} $bullet")
        }
    }

    copyToClipboard(context, context.getString(R.string.export_assistant_clipboard_label), sb.toString().trim())
}
