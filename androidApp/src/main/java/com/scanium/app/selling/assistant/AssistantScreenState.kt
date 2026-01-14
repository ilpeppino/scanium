package com.scanium.app.selling.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scanium.app.model.SuggestedAttribute

@Stable
class AssistantScreenState {
    var inputText by mutableStateOf("")
    var pendingConflictAttribute by mutableStateOf<Pair<SuggestedAttribute, String>?>(null)
    var lastSpokenTimestamp by mutableStateOf<Long?>(null)
    var lastSoundedAssistantTimestamp by mutableStateOf<Long?>(null)
}

@Composable
fun rememberAssistantScreenState(): AssistantScreenState {
    return remember { AssistantScreenState() }
}
