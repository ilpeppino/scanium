package com.scanium.app.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.scanium.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TtsManager"

/**
 * Centralized TTS manager for the Scanium app.
 * Automatically adjusts TTS language based on effective TTS language from settings.
 *
 * Features:
 * - Single source of truth for TTS across the app
 * - Automatically syncs with effectiveTtsLanguage from SettingsRepository
 * - Graceful fallback when voice packages are missing
 * - Thread-safe and lifecycle-aware
 *
 * Usage:
 * ```
 * @Inject lateinit var ttsManager: TtsManager
 * ttsManager.speak("Hello world")
 * ```
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) {
        private var tts: TextToSpeech? = null
        private var isInitialized = false
        private var initializationFailed = false
        private var pendingText: String? = null
        private var currentLanguage: String? = null

        private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private val _isReady = MutableStateFlow(false)
        val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        private val _isSpeaking = MutableStateFlow(false)
        val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        private val _languageSupport = MutableStateFlow<LanguageSupport>(LanguageSupport.Unknown)
        val languageSupport: StateFlow<LanguageSupport> = _languageSupport.asStateFlow()

        init {
            initialize()
            observeSettings()
        }

        private fun initialize() {
            try {
                tts =
                    TextToSpeech(context.applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            isInitialized = true
                            _isReady.value = true
                            Log.d(TAG, "TTS initialized successfully")

                            // Set up utterance listener
                            tts?.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        _isSpeaking.value = true
                                    }

                                    override fun onDone(utteranceId: String?) {
                                        _isSpeaking.value = false
                                    }

                                    override fun onError(utteranceId: String?) {
                                        _isSpeaking.value = false
                                        Log.w(TAG, "TTS error for utterance: $utteranceId")
                                    }
                                },
                            )

                            // Apply current effective language from settings (will be set by observer)
                            // The language will be applied when the flow emits its first value

                            // Speak pending text if queued during initialization
                            pendingText?.let { text ->
                                speakNow(text)
                                pendingText = null
                            }
                        } else {
                            initializationFailed = true
                            _isReady.value = false
                            Log.w(TAG, "TTS initialization failed with status: $status")
                        }
                    }
            } catch (e: Exception) {
                initializationFailed = true
                _isReady.value = false
                Log.e(TAG, "Failed to create TextToSpeech instance", e)
            }
        }

        private fun observeSettings() {
            managerScope.launch {
                settingsRepository.effectiveTtsLanguageFlow.collect { languageTag ->
                    Log.d(TAG, "Effective TTS language changed to: $languageTag")
                    if (isInitialized && !initializationFailed) {
                        applyLanguage(languageTag)
                    }
                }
            }
        }

        /**
         * Apply the given language tag to TTS with graceful fallback.
         * Updates languageSupport state for UI diagnostics.
         */
        private fun applyLanguage(languageTag: String) {
            if (!isInitialized || initializationFailed) {
                Log.w(TAG, "Cannot apply language: TTS not initialized")
                _languageSupport.value = LanguageSupport.NotInitialized
                return
            }

            // Skip if language hasn't changed
            if (currentLanguage == languageTag) {
                Log.d(TAG, "Language already set to: $languageTag")
                return
            }

            try {
                val locale = Locale.forLanguageTag(languageTag)
                val result = tts?.setLanguage(locale)

                when (result) {
                    TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                    -> {
                        currentLanguage = languageTag
                        _languageSupport.value = LanguageSupport.Supported(languageTag)
                        Log.i(TAG, "TTS language set to: $languageTag")
                    }

                    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.w(TAG, "Language not supported: $languageTag, trying fallback")
                        // Try primary language as fallback
                        tryFallbackLanguage(languageTag)
                    }

                    else -> {
                        Log.w(TAG, "Unknown language result: $result for locale $languageTag")
                        _languageSupport.value = LanguageSupport.Unknown
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying TTS language: $languageTag", e)
                _languageSupport.value = LanguageSupport.Error(e.message ?: "Unknown error")
            }
        }

        /**
         * Try fallback languages: English, then device default.
         */
        private fun tryFallbackLanguage(requestedLanguage: String) {
            // Try English first
            val englishResult = tts?.setLanguage(Locale.ENGLISH)
            if (englishResult in
                listOf(
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                )
            ) {
                currentLanguage = "en"
                _languageSupport.value = LanguageSupport.FallbackUsed(requestedLanguage, "en")
                Log.i(TAG, "Using English as fallback for: $requestedLanguage")
                return
            }

            // Try device default as last resort
            val defaultResult = tts?.setLanguage(Locale.getDefault())
            if (defaultResult in
                listOf(
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                )
            ) {
                val defaultTag = Locale.getDefault().toLanguageTag()
                currentLanguage = defaultTag
                _languageSupport.value = LanguageSupport.FallbackUsed(requestedLanguage, defaultTag)
                Log.i(TAG, "Using device default ($defaultTag) as fallback for: $requestedLanguage")
            } else {
                _languageSupport.value = LanguageSupport.NotSupported(requestedLanguage)
                Log.e(TAG, "No supported voice found for: $requestedLanguage")
            }
        }

        /**
         * Speak the given text.
         * If TTS is not ready, queues the text to speak once initialized.
         *
         * @param text The text to speak (must be non-blank)
         * @param utteranceId Optional ID for tracking this utterance
         */
        fun speak(
            text: String,
            utteranceId: String = "ScaniumTTS",
        ) {
            if (text.isBlank()) {
                Log.d(TAG, "Ignoring empty text")
                return
            }

            when {
                initializationFailed -> {
                    Log.w(TAG, "Cannot speak: TTS initialization failed")
                }

                !isInitialized -> {
                    Log.d(TAG, "TTS not ready yet, queuing text")
                    pendingText = text
                }

                else -> {
                    speakNow(text, utteranceId)
                }
            }
        }

        private fun speakNow(
            text: String,
            utteranceId: String = "ScaniumTTS",
        ) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                Log.d(TAG, "Speaking text (${text.length} chars) with language: $currentLanguage")
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
            }
        }

        /**
         * Stop any ongoing speech immediately.
         */
        fun stop() {
            try {
                tts?.stop()
                _isSpeaking.value = false
                Log.d(TAG, "TTS stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping TTS", e)
            }
        }

        /**
         * Shut down the TTS engine. Should be called on app exit.
         * Note: Since this is a Singleton, typically not called during normal app lifecycle.
         */
        fun shutdown() {
            try {
                tts?.stop()
                tts?.shutdown()
                tts = null
                isInitialized = false
                _isReady.value = false
                _isSpeaking.value = false
                Log.d(TAG, "TTS shutdown complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS shutdown", e)
            }
        }
    }

/**
 * Language support status for diagnostic purposes.
 */
sealed class LanguageSupport {
    data object Unknown : LanguageSupport()

    data object NotInitialized : LanguageSupport()

    data class Supported(
        val language: String,
    ) : LanguageSupport()

    data class FallbackUsed(
        val requested: String,
        val fallback: String,
    ) : LanguageSupport()

    data class NotSupported(
        val language: String,
    ) : LanguageSupport()

    data class Error(
        val message: String,
    ) : LanguageSupport()
}
