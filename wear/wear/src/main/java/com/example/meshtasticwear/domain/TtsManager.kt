package com.example.meshtasticwear.domain

interface TtsManager {
    /**
     * Speaks the given text synchronously/asynchronously on the local speech engine.
     */
    fun speak(text: String)

    /**
     * Stops any active audio playback.
     */
    fun stop()

    /**
     * Releases resources allocated by the audio engine.
     */
    fun shutdown()
}
