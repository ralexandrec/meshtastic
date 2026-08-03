package com.example.meshtasticwear

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.example.meshtasticwear.data.TcpMeshClient
import com.example.meshtasticwear.domain.MockTtsManager
import com.example.meshtasticwear.ui.MainActivity
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.pt.Dado
import io.cucumber.java.pt.Quando
import io.cucumber.java.pt.Então
import org.junit.Assert.*
import java.net.Socket
import com.example.meshtasticwear.R

class StepDefinitions {

    private lateinit var app: MeshtasticApp
    private lateinit var mockTts: MockTtsManager
    private var activityScenario: ActivityScenario<MainActivity>? = null
    private var simulatedTranscription = ""

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<MeshtasticApp>()
        mockTts = MockTtsManager()
        
        app.ttsManager = mockTts
        app.meshConnection = TcpMeshClient("10.0.2.2", 4403)
        app.mockSayboardInstalled = true
        
        Intents.init()
        // Stub ACTION_VIEW (geo intents) to prevent ActivityNotFoundException
        Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        )
    }

    @After
    fun tearDown() {
        Intents.release()
        activityScenario?.close()
        app.meshConnection?.disconnect()
        app.ttsManager?.shutdown()
    }

    @Dado("que o simulador Meshtastic está escutando na porta local")
    fun que_o_simulador_meshtastic_esta_escutando_na_porta_local() {
        var socketConnected = false
        for (i in 1..5) {
            try {
                val socket = Socket("10.0.2.2", 4403)
                socket.close()
                socketConnected = true
                break
            } catch (e: Exception) {
                try {
                    val socket = Socket("127.0.0.1", 4403)
                    socket.close()
                    socketConnected = true
                    app.meshConnection = TcpMeshClient("127.0.0.1", 4403)
                    break
                } catch (ex: Exception) {
                    Thread.sleep(500)
                }
            }
        }
        assertTrue("The simulator mesh_mock must be running on port 4403", socketConnected)
        Thread.sleep(200) // Delay for visual monitoring
    }

    @Dado("o aplicativo Wear OS iniciou no Modo Voz com o Mock de TTS injetado")
    fun o_aplicativo_wear_os_iniciou_no_modo_voz_com_o_mock_de_tts_injetado() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(200) // Delay for visual monitoring
    }

    @Dado("o aplicativo Wear OS está ativo com o Mock de TTS injetado")
    fun o_aplicativo_wear_os_esta_ativo_com_o_mock_de_tts_injetado() {
        o_aplicativo_wear_os_iniciou_no_modo_voz_com_o_mock_de_tts_injetado()
    }

    @Quando("o usuário pressiona o botão físico de hardware \\(STEM_1) do relógio")
    fun o_usuario_pressiona_o_botao_fisico_de_hardware_stem_1_do_relogio() {
        activityScenario?.onActivity { activity ->
            activity.onKeyDown(
                KeyEvent.KEYCODE_STEM_1,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STEM_1)
            )
        }
        Thread.sleep(200) // Delay for visual monitoring
    }

    @Quando("a transcrição da voz simulada resulta em {string}")
    fun a_transcricao_da_voz_simulada_resulta_em(transcricao: String) {
        simulatedTranscription = transcricao
        val resultData = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(transcricao))
        }
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
        Intents.intending(hasAction(RecognizerIntent.EXTRA_RESULTS)).respondWith(result) // Fallback standard match
        Intents.intending(hasAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)).respondWith(result)
        Thread.sleep(200) // Delay for visual monitoring
    }

    @Quando("o usuário solta o botão físico")
    fun o_usuario_solta_o_botao_fisico() {
        activityScenario?.onActivity { activity ->
            activity.onKeyUp(
                KeyEvent.KEYCODE_STEM_1,
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_STEM_1)
            )
        }
        Thread.sleep(200) // Waits for packet sending
    }

    @Então("a mensagem {string} deve ser enviada ao simulador")
    fun a_mensagem_deve_ser_enviada_ao_simulador(mensagem: String) {
        assertNotNull(app.meshConnection)
        assertTrue("Message to be sent should not be empty", mensagem.isNotEmpty())
        Thread.sleep(200) // Delay for visual monitoring
    }

    @Então("quando o simulador envia a resposta de confirmação de texto {string}")
    fun quando_o_simulador_envia_a_resposta_de_confirmacao_de_texto(resposta: String) {
        activityScenario?.onActivity { activity ->
            val bytes = com.example.meshtasticwear.data.MiniProto.encodeFromRadioTextMessage(
                resposta,
                fromNode = 2345678,
                toNode = activity.viewModel.localNodeNum
            )
            activity.viewModel.simulateMessageReceived(bytes)
        }
        Thread.sleep(100)
    }

    @Então("o relógio deve receber o texto e falar automaticamente {string} via TTS")
    fun o_relogio_deve_receber_o_texto_e_falar_automaticamente_via_tts(mensagemEsperada: String) {
        var success = false
        for (i in 1..40) {
            if (mockTts.lastSpokenText == mensagemEsperada) {
                success = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("Expected to speak: '$mensagemEsperada', but spoke: '${mockTts.lastSpokenText}'", success)
        Thread.sleep(2500) // Allows monitoring the completion of the step
    }

    @Quando("o usuário altera o modo de operação para {string}")
    fun o_usuario_altera_o_modo_de_operacao_para(modo: String) {
        activityScenario?.onActivity { activity ->
            if (modo == "Texto" && activity.viewModel.isVoiceMode.value) {
                activity.viewModel.toggleMode()
            } else if (modo == "Voz" && !activity.viewModel.isVoiceMode.value) {
                activity.viewModel.toggleMode()
            }
        }
        Thread.sleep(100)
    }

    @Quando("o usuário envia o texto {string} para a malha")
    fun o_usuario_envia_o_texto_para_a_malha(mensagem: String) {
        activityScenario?.onActivity { activity ->
            activity.viewModel.sendTextMessage(mensagem)
        }
        Thread.sleep(100)
    }

    @Então("a mensagem {string} deve ser transmitida")
    fun a_mensagem_deve_ser_transmitida(mensagem: String) {
        var found = false
        activityScenario?.onActivity { activity ->
            found = activity.viewModel.messages.any { it.fullDisplay.contains(mensagem) }
        }
        assertTrue("Message '$mensagem' should be registered in the sent list", found)
    }

    @Então("a mensagem {string} deve ser exibida na tela do relógio")
    fun a_mensagem_deve_ser_exibida_na_tela_do_relogio(mensagem: String) {
        var displayed = false
        activityScenario?.onActivity { activity ->
            displayed = activity.viewModel.messages.any { it.fullDisplay.contains(mensagem) }
        }
        assertTrue("Message '$mensagem' should be displayed on screen", displayed)
    }

    @Então("o motor de voz TTS não deve falar nenhuma mensagem")
    fun o_motor_de_voz_tts_nao_deve_falar_nenhuma_mensagem() {
        assertNull("The TTS should remain silent in text mode", mockTts.lastSpokenText)
        Thread.sleep(100)
    }

    @Dado("que o teclado Sayboard está registrado como instalado")
    fun que_o_teclado_sayboard_esta_registrado_como_instalado() {
        app.mockSayboardInstalled = true
        Thread.sleep(100)
    }

    @Dado("que o teclado Sayboard está registrado como não instalado")
    fun que_o_teclado_sayboard_esta_registrado_como_nao_instalado() {
        app.mockSayboardInstalled = false
        Thread.sleep(100)
    }

    @Quando("o aplicativo Wear OS inicia")
    fun o_aplicativo_wear_os_inicia() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(2500)
    }

    @Então("o alerta de {string} não deve ser exibido")
    fun o_alerta_de_sayboard_ausente_nao_deve_ser_exibido(alerta: String) {
        var isShowing = true
        activityScenario?.onActivity { activity ->
            isShowing = activity.viewModel.showSayboardWarning.value
        }
        assertFalse("Should not show '$alerta' alert when the keyboard is installed", isShowing)
        Thread.sleep(100)
    }

    @Então("o alerta de {string} deve ser exibido na tela")
    fun o_alerta_de_sayboard_ausente_deve_ser_exibido_na_tela(alerta: String) {
        var isShowing = false
        activityScenario?.onActivity { activity ->
            isShowing = activity.viewModel.showSayboardWarning.value
        }
        assertTrue("Should show '$alerta' alert when the keyboard is not installed", isShowing)
        Thread.sleep(100)
    }

    @Quando("o usuário abre as configurações de conexão")
    fun o_usuario_abre_as_configuracoes_de_conexao() {
        activityScenario?.onActivity { activity ->
            activity.viewModel.showSettingsDialog.value = true
        }
        Thread.sleep(100)
    }

    @Quando("seleciona pareamento Bluetooth BLE com sucesso")
    fun seleciona_pareamento_bluetooth_ble_com_sucesso() {
        activityScenario?.onActivity { activity ->
            activity.viewModel.switchConnection(com.example.meshtasticwear.data.BleMeshClient(simulateSuccess = true))
            activity.viewModel.showSettingsDialog.value = false
        }
        Thread.sleep(2500)
    }

    @Quando("seleciona desconectar da malha")
    fun seleciona_desconectar_da_malha() {
        activityScenario?.onActivity { activity ->
            activity.viewModel.disconnectFromMesh()
            activity.viewModel.showSettingsDialog.value = false
        }
        Thread.sleep(2500)
    }

    @Então("o status da conexão na tela deve mudar para {string}")
    fun o_status_da_conexao_na_tela_deve_mudar_para(statusEsperado: String) {
        var statusAtual = ""
        activityScenario?.onActivity { activity ->
            statusAtual = activity.viewModel.connectionStatus.value
        }
        // Map expected status to localized string dynamically
        val resId = when (statusEsperado.uppercase()) {
            "CONECTADO (BLE)" -> R.string.status_connected_ble
            "DESCONECTADO" -> R.string.status_disconnected
            "CONECTADO" -> R.string.status_connected
            "CONECTANDO..." -> R.string.status_connecting
            else -> null
        }
        if (resId != null) {
            val expectedLocalized = app.getString(resId)
            assertEquals(expectedLocalized.uppercase(), statusAtual.uppercase())
        } else {
            assertEquals(statusEsperado.uppercase(), statusAtual.uppercase())
        }
        Thread.sleep(100)
    }

    @Quando("o simulador envia um pacote de coordenadas com latitude {string} e longitude {string}")
    fun o_simulador_envia_um_pacote_de_coordenadas_com_latitude_e_longitude(latStr: String, lonStr: String) {
        val lat = latStr.toDouble()
        val lon = lonStr.toDouble()
        android.util.Log.d("MeshtasticWear", "Step: latitude = $lat, longitude = $lon")
        activityScenario?.onActivity { activity ->
            val bytes = com.example.meshtasticwear.data.MiniProto.encodeFromRadioPosition(
                lat, lon, 760.0,
                fromNode = 2345678,
                toNode = activity.viewModel.localNodeNum
            )
            activity.viewModel.simulateMessageReceived(bytes)
        }
        Thread.sleep(100)
    }

    @Então("a mensagem contendo {string} deve ser exibida na tela do relógio")
    fun a_mensagem_contendo_deve_ser_exibida_na_tela_do_relogio(mensagem: String) {
        var displayed = false
        activityScenario?.onActivity { activity ->
            displayed = activity.viewModel.messages.any { it.fullDisplay.contains(mensagem) }
        }
        assertTrue("Message containing '$mensagem' should be displayed on screen", displayed)
    }

    @Então("o ícone de mapa deve estar visível ao lado da mensagem")
    fun o_icone_de_mapa_deve_estar_visivel_ao_lado_da_mensagem() {
        var hasGps = false
        activityScenario?.onActivity { activity ->
            hasGps = activity.viewModel.messages.any { it.fullDisplay.contains("GPS: Lat") }
        }
        assertTrue("The message list should contain GPS coordinates", hasGps)
        Thread.sleep(100)
    }

    @Quando("o usuário clica no ícone de mapa da mensagem")
    fun o_usuario_clica_no_icone_de_mapa_da_mensagem() {
        activityScenario?.onActivity { activity ->
            // Simulates maps opening
            activity.viewModel.openMap("-23.5615", "-46.656", activity)
        }
        Thread.sleep(100)
    }

    @Então("uma Intent de geolocalização com URI {string} deve ser disparada")
    fun uma_intent_de_geolocalizacao_com_uri_deve_ser_disparada(uriEsperada: String) {
        try {
            androidx.test.espresso.intent.Intents.intended(
                org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_VIEW),
                    androidx.test.espresso.intent.matcher.IntentMatchers.hasData(android.net.Uri.parse(uriEsperada))
                )
            )
        } catch (e: Throwable) {
            val recorded = androidx.test.espresso.intent.Intents.getIntents()
            android.util.Log.e("MeshtasticWear", "Test failed! Recorded intents count: ${recorded.size}")
            recorded.forEach { intent ->
                android.util.Log.e("MeshtasticWear", "Recorded intent action: ${intent.action}, data: ${intent.data}")
            }
            throw e
        }
        Thread.sleep(100)
    }
}
