package com.scanium.app.selling.assistant

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class AssistantVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val isSpeechAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isSpeechAvailable) {
            onError("Speech recognition unavailable")
            return
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError("Speech recognition error")
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                val transcript = matches?.firstOrNull()
                if (transcript != null) {
                    onResult(transcript)
                } else {
                    onError("No speech recognized")
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask the assistant")
        }
        speechRecognizer.startListening(intent)
    }

    fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant-response")
                }
            }
            return
        }

        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant-response")
        }
    }

    fun shutdown() {
        speechRecognizer.destroy()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
