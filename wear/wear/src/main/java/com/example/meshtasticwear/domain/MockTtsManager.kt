package com.example.meshtasticwear.domain

class MockTtsManager : TtsManager {
    
    // Stores the last spoken string for assertion in BDD tests
    var lastSpokenText: String? = null
    var isStopped = false
    var isShutdown = false

    override fun speak(text: String) {
        lastSpokenText = text
        isStopped = false
    }

    override fun stop() {
        isStopped = true
    }

    override fun shutdown() {
        isShutdown = true
    }
}
