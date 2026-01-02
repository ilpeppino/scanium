package com.scanium.app.voice

/**
 * Voice state for the assistant.
 */
enum class VoiceState {
    /** No voice activity */
    IDLE,

    /** Recording user speech */
    LISTENING,

    /** Processing speech to text */
    TRANSCRIBING,

    /** TTS is speaking the response */
    SPEAKING,

    /** An error occurred */
    ERROR,
}

/**
 * Result of a voice recognition attempt.
 */
sealed class VoiceResult {
    data class Success(val transcript: String, val confidence: Float) : VoiceResult()

    data class Error(val message: String, val errorCode: Int) : VoiceResult()

    data object Cancelled : VoiceResult()
}
