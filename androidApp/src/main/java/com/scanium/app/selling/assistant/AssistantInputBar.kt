package com.scanium.app.selling.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun AssistantInputBar(
    inputText: String,
    inputPlaceholder: String,
    inputEnabled: Boolean,
    voiceModeEnabled: Boolean,
    voiceState: VoiceState,
    speechAvailable: Boolean,
    onInputChange: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = inputText,
        onValueChange = { if (inputEnabled) onInputChange(it) },
        enabled = inputEnabled,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { traversalIndex = 3f }
                .navigationBarsPadding()
                .imePadding(),
        placeholder = {
            Text(
                text = inputPlaceholder,
                color = if (!inputEnabled) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
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
                if (voiceModeEnabled) {
                    val isListening = voiceState == VoiceState.LISTENING
                    val isTranscribing = voiceState == VoiceState.TRANSCRIBING
                    val isActive = isListening || isTranscribing

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
                        onClick = onVoiceToggle,
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

                val canSend = inputText.isNotBlank() && inputEnabled
                IconButton(
                    onClick = { if (canSend) onSend() },
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
                onSend = { if (inputText.isNotBlank() && inputEnabled) onSend() },
            ),
    )
}
