package com.example.meshtasticwear.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class NativeTtsManager(context: Context) : TtsManager {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        // Initializes the Android native text-to-speech engine
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.getDefault()
                val result = tts?.setLanguage(locale)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                }
            }
        }
    }

    override fun speak(text: String) {
        if (isInitialized) {
            // QUEUE_FLUSH discards pending audio queues and speaks immediately
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MeshtasticTTS")
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
