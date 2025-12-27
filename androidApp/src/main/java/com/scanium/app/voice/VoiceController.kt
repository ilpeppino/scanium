package com.scanium.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.scanium.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Controller for voice input (STT) and output (TTS) in the assistant.
 *
 * Privacy-first design:
 * - No always-on listening
 * - Explicit user gesture required to start recording
 * - Clear state indicators
 * - No audio storage or transcript logging
 */
class VoiceController(context: Context) {
    companion object {
        private const val TAG = "VoiceController"
        private const val TTS_UTTERANCE_ID = "assistant-response"
        private const val COULD_NOT_UNDERSTAND_MESSAGE = "Couldn't understand—try again"
        private const val VOICE_UNAVAILABLE_MESSAGE = "Voice input unavailable on this device"
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentLanguage: Locale = Locale.getDefault()

    private inline fun logDebug(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    // State management
    private val stateMachine = VoiceStateMachine()
    val voiceState: StateFlow<VoiceState> = stateMachine.state

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    val lastError: StateFlow<String?> = stateMachine.error

    /** Whether speech recognition is available on this device */
    val isSpeechAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** Whether TTS is ready to speak */
    val isTtsReady: Boolean get() = ttsReady

    // Callbacks
    private var onResultCallback: ((VoiceResult) -> Unit)? = null

    /**
     * Set the language for STT/TTS.
     * @param languageCode Language code (e.g., "EN", "NL", "DE")
     */
    fun setLanguage(languageCode: String) {
        currentLanguage = when (languageCode.uppercase()) {
            "EN" -> Locale.ENGLISH
            "NL" -> Locale("nl", "NL")
            "DE" -> Locale.GERMAN
            "FR" -> Locale.FRENCH
            "ES" -> Locale("es", "ES")
            "IT" -> Locale.ITALIAN
            else -> Locale.getDefault()
        }
        tts?.language = currentLanguage
        logDebug { "Language set to: $currentLanguage" }
    }

    /**
     * Initialize TTS engine.
     * Call this early to avoid delay when first speaking.
     */
    fun initializeTts(onReady: () -> Unit = {}) {
        if (tts != null) {
            onReady()
            return
        }

        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = currentLanguage
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        stateMachine.onSpeaking()
                    }

                    override fun onDone(utteranceId: String?) {
                        stateMachine.onIdle()
                    }

                    @Deprecated("Deprecated in API 21")
                    override fun onError(utteranceId: String?) {
                        stateMachine.onIdle()
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        stateMachine.onIdle()
                        Log.e(TAG, "TTS error code $errorCode for utterance: $utteranceId")
                    }
                })
                logDebug { "TTS initialized successfully" }
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Start listening for speech input.
     *
     * @param onResult Callback with the recognition result
     */
    fun startListening(onResult: (VoiceResult) -> Unit) {
        if (!isSpeechAvailable) {
            onResult(VoiceResult.Error(VOICE_UNAVAILABLE_MESSAGE, -1))
            return
        }

        if (voiceState.value == VoiceState.LISTENING) {
            logDebug { "Already listening, ignoring start request" }
            return
        }

        onResultCallback = onResult
        _partialTranscript.value = ""
        stateMachine.clearError()
        stateMachine.onStartListening()

        // Create recognizer on demand
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                logDebug { "Ready for speech" }
            }

            override fun onBeginningOfSpeech() {
                logDebug { "Beginning of speech detected" }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Could use this for visual feedback (mic level indicator)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer - we don't store this (privacy)
            }

            override fun onEndOfSpeech() {
                logDebug { "End of speech" }
                stateMachine.onTranscribing()
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e(TAG, "Recognition error: $errorMessage (code: $error)")
                stateMachine.onError(errorMessage)

                onResultCallback?.invoke(VoiceResult.Error(errorMessage, error))
                onResultCallback = null
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                val transcript = matches?.firstOrNull { it.isNotBlank() }
                val confidence = confidences?.firstOrNull() ?: 0.8f

                if (transcript != null) {
                    logDebug { "Recognition result length=${transcript.length} (confidence: $confidence)" }
                    stateMachine.onIdle()
                    onResultCallback?.invoke(VoiceResult.Success(transcript, confidence))
                } else {
                    Log.w(TAG, "No speech recognized")
                    stateMachine.onIdle()
                    onResultCallback?.invoke(VoiceResult.Error(COULD_NOT_UNDERSTAND_MESSAGE, 0))
                }
                onResultCallback = null
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partials?.firstOrNull()?.let { partial ->
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                logDebug { "Recognition event: $eventType" }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Don't show prompt - we handle UI ourselves
        }

        try {
            speechRecognizer?.startListening(intent)
            logDebug { "Started listening" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            stateMachine.onError("Failed to start speech recognition")
            onResultCallback?.invoke(VoiceResult.Error("Failed to start speech recognition", -2))
            onResultCallback = null
        }
    }

    /**
     * Stop listening and cancel any pending recognition.
     */
    fun stopListening() {
        if (voiceState.value == VoiceState.LISTENING || voiceState.value == VoiceState.TRANSCRIBING) {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            stateMachine.onIdle()
            onResultCallback?.invoke(VoiceResult.Cancelled)
            onResultCallback = null
            logDebug { "Stopped listening" }
        }
    }

    /**
     * Speak text using TTS.
     *
     * @param text Text to speak
     * @param onComplete Optional callback when speech is done
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!ttsReady) {
            initializeTts {
                doSpeak(text, onComplete)
            }
            return
        }
        doSpeak(text, onComplete)
    }

    private fun doSpeak(text: String, onComplete: (() -> Unit)?) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready, cannot speak")
            return
        }

        // Request audio focus
        val focusResult = audioManager.requestAudioFocus(
            { /* focus change listener */ },
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Could not get audio focus")
        }

        // Clean text for TTS (remove markdown, excessive punctuation)
        val cleanedText = cleanTextForTts(text)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, TTS_UTTERANCE_ID)
        logDebug { "Speaking ${cleanedText.length} chars" }
    }

    /**
     * Stop TTS playback.
     */
    fun stopSpeaking() {
        if (voiceState.value == VoiceState.SPEAKING) {
            tts?.stop()
            stateMachine.onIdle()
            audioManager.abandonAudioFocus(null)
            logDebug { "Stopped speaking" }
        }
    }

    /**
     * Check if currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Clean up resources.
     * Call this when the assistant screen is disposed.
     */
    fun shutdown() {
        stopListening()
        stopSpeaking()

        speechRecognizer?.destroy()
        speechRecognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false

        stateMachine.onIdle()
        logDebug { "Voice controller shutdown" }
    }

    /**
     * Clean text for TTS output.
     * Removes markdown formatting and other elements that shouldn't be spoken.
     */
    private fun cleanTextForTts(text: String): String {
        return text
            // Remove markdown bold/italic
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            // Remove markdown links
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
            // Remove code blocks
            .replace(Regex("`(.+?)`"), "$1")
            // Remove bullet points
            .replace(Regex("^[•\\-\\*]\\s*", RegexOption.MULTILINE), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Get human-readable error message for recognition error code.
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
            SpeechRecognizer.ERROR_NETWORK -> "Network error - check your connection"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> COULD_NOT_UNDERSTAND_MESSAGE
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech recognition error"
        }
    }
}
