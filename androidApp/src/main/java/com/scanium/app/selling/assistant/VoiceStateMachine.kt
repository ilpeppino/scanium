package com.scanium.app.selling.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple reducer for voice interaction state so it can be unit tested easily.
 */
class VoiceStateMachine {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onStartListening() {
        _error.value = null
        _state.value = VoiceState.LISTENING
    }

    fun onTranscribing() {
        _state.value = VoiceState.TRANSCRIBING
    }

    fun onSpeaking() {
        _state.value = VoiceState.SPEAKING
    }

    fun onIdle() {
        _state.value = VoiceState.IDLE
    }

    fun onError(message: String) {
        _error.value = message
        _state.value = VoiceState.ERROR
    }

    fun clearError() {
        _error.value = null
    }
}
