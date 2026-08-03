package com.example.meshtasticwear.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.meshtasticwear.MeshtasticApp
import com.example.meshtasticwear.data.TcpMeshClient
import com.example.meshtasticwear.domain.NativeTtsManager
import java.util.Locale
import com.example.meshtasticwear.R

class MainActivity : ComponentActivity() {

    lateinit var viewModel: PttViewModel

    // Launcher for the Speech Recognition (STT) Intent
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendVoiceMessage(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieves dependencies from the application's Service Locator
        val app = application as MeshtasticApp
        val connection = app.meshConnection ?: TcpMeshClient("10.0.2.2", 4403)
        val tts = app.ttsManager ?: NativeTtsManager(this)

        // Generates a dynamic unique node ID based on UUID hash (saved in SharedPreferences) to support multiple emulators
        val prefs = getSharedPreferences("meshtastic_prefs", MODE_PRIVATE)
        var localNodeNum = prefs.getInt("local_node_num", 0)
        if (localNodeNum == 0) {
            localNodeNum = Math.abs(java.util.UUID.randomUUID().hashCode())
            prefs.edit().putInt("local_node_num", localNodeNum).apply()
        }

        // SOLID Refactoring: Passing context to PttViewModel
        viewModel = PttViewModel(connection, tts, applicationContext, localNodeNum = localNodeNum)

        // Checks if the offline keyboard Sayboard is installed
        checkSayboardInstallation()

        setContent {
            PttScreen(
                viewModel = viewModel,
                onTriggerSpeech = {
                    triggerSpeechInput()
                },
                onTriggerTextInput = { hint ->
                    // In production Wear OS, opens standard text input
                    // For testing/simplicity, we trigger speech/speech-mock input
                    triggerSpeechInput()
                }
            )
        }
    }

    // Intercepts watch physical side buttons (STEM_1 and STEM_2) for PTT
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2) {
            viewModel.startVoiceRecording()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2) {
            viewModel.stopVoiceRecordingAndTrigger {
                triggerSpeechInput()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun checkSayboardInstallation() {
        val app = application as MeshtasticApp
        val installed = app.mockSayboardInstalled ?: try {
            packageManager.getPackageInfo("com.elishaazaria.sayboard", PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        
        if (!installed) {
            // Enables the native Wear OS dialog display on ViewModel
            viewModel.showSayboardWarning.value = true
        }
    }

    private fun triggerSpeechInput() {
        val targetLocaleTag = resources.configuration.locales[0]?.toLanguageTag() ?: "pt-BR"
        android.util.Log.d("MeshtasticSTT", "Triggering Speech Input with locale tag: '$targetLocaleTag', default: '${java.util.Locale.getDefault()}'")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocaleTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocaleTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt))
            setPackage("com.elishaazaria.sayboard")
        }

        val app = application as MeshtasticApp
        val isTesting = app.ttsManager?.javaClass?.simpleName?.contains("Mock") == true

        if (isTesting) {
            // In tests, uses the classic Intent Activity so that Espresso Intents can stub/mock the result
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                intent.setPackage(null)
                try {
                    speechRecognizerLauncher.launch(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, getString(R.string.no_speech_engine), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // In production/real use, uses SpeechRecognizer in the background for automatic send upon silence
            runOnUiThread {
                try {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Toast.makeText(this@MainActivity, getString(R.string.listening), Toast.LENGTH_SHORT).show()
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            // If background listener fails (permissions or service unavailable), fallback to Intent Activity
                            intent.setPackage(null)
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, getString(R.string.speech_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spokenText = matches?.firstOrNull()
                            if (!spokenText.isNullOrBlank()) {
                                viewModel.sendVoiceMessage(spokenText)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    // Fallback if unable to instantiate the recognizer
                    speechRecognizerLauncher.launch(intent)
                }
            }
        }
    }
}
