package com.example.meshtasticwear.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.meshtasticwear.data.MeshConnection
import com.example.meshtasticwear.data.MiniProto
import com.example.meshtasticwear.domain.TtsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.meshtasticwear.R

class PttViewModel(
    private val initialConnection: MeshConnection,
    private val ttsManager: TtsManager,
    private val context: Context,
    val localNodeNum: Int = 111111,
    private val remoteNodeNum: Int = 2345678
) : ViewModel() {

    var activeConnection = initialConnection
    var connectionStatus = mutableStateOf(context.getString(R.string.status_disconnected))
    var isConnected = mutableStateOf(false)
    
    // Toggle between Voice Mode (Push-To-Talk + TTS) and Text Mode (Chat with Typing)
    var isVoiceMode = mutableStateOf(true) 
    
    // Audio recording state
    var isRecording = mutableStateOf(false) 
    
    // History of decoded messages from the simulator
    val messages = mutableStateListOf<UiMessage>()
    
    // Reactive control for displaying the Sayboard warning Dialog
    var showSayboardWarning = mutableStateOf(false)

    // Reactive control for displaying the connection configuration dialog
    var showSettingsDialog = mutableStateOf(false)

    // Reactive control for displaying the battery button
    var showBattery = mutableStateOf(false)

    init {
        connectToMesh()
    }

    fun connectToMesh() {
        activeConnection.connect(
            onMessageReceived = { bytes ->
                handleIncomingBytes(bytes)
            },
            onStatusChanged = { status ->
                val localizedStatus = when {
                    status.startsWith("Connected (BLE)", ignoreCase = true) -> context.getString(R.string.status_connected_ble)
                    status.startsWith("Connected", ignoreCase = true) -> context.getString(R.string.status_connected)
                    status.startsWith("Connecting", ignoreCase = true) -> context.getString(R.string.status_connecting)
                    status.startsWith("Disconnected", ignoreCase = true) -> context.getString(R.string.status_disconnected)
                    status.startsWith("BLE GATT: Scanning", ignoreCase = true) -> context.getString(R.string.status_ble_scanning)
                    status.startsWith("BLE GATT: Dongle", ignoreCase = true) -> context.getString(R.string.status_ble_not_found)
                    status.startsWith("Error:", ignoreCase = true) -> {
                        val msg = status.substringAfter("Error:").trim()
                        context.getString(R.string.status_error, msg)
                    }
                    else -> status
                }
                connectionStatus.value = localizedStatus
                isConnected.value = (status == "Connected" || status == "Connected (BLE)" || status.startsWith("Connected", ignoreCase = true))
            }
        )
    }

    fun disconnectFromMesh() {
        activeConnection.disconnect()
        isConnected.value = false
        connectionStatus.value = context.getString(R.string.status_disconnected)
    }

    fun switchConnection(newConnection: MeshConnection) {
        disconnectFromMesh()
        activeConnection = newConnection
        connectToMesh()
    }

    fun simulateMessageReceived(bytes: ByteArray) {
        handleIncomingBytes(bytes)
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val parser = MiniProto.FromRadioParser(bytes)
        if (parser.hasPacket) {
            val senderId = "!%08x".format(parser.packetFrom)
            val text = parser.packetText
            val fullDisplay = "$senderId: $text"
            
            // Regex logic moved from the Composable (SRP)
            val gpsMatch = """GPS: Lat ([-+]?\d+\.\d+), Lon ([-+]?\d+\.\d+)""".toRegex().find(text)
            val uiMsg = if (gpsMatch != null) {
                UiMessage(
                    sender = senderId,
                    text = text,
                    fullDisplay = fullDisplay,
                    isGps = true,
                    latitude = gpsMatch.groupValues[1],
                    longitude = gpsMatch.groupValues[2]
                )
            } else {
                UiMessage(
                    sender = senderId,
                    text = text,
                    fullDisplay = fullDisplay
                )
            }
            messages.add(uiMsg)
            
            // If in Voice Mode, synthesizes offline aloud (except if it is GPS)
            if (isVoiceMode.value && !text.contains("GPS:")) {
                ttsManager.speak(text)
            }
        }
    }

    fun startVoiceRecording() {
        if (!isRecording.value) {
            isRecording.value = true
        }
    }

    fun stopVoiceRecordingAndTrigger(triggerSpeechRecognition: () -> Unit) {
        if (isRecording.value) {
            isRecording.value = false
            triggerSpeechRecognition()
        }
    }

    fun sendVoiceMessage(text: String) {
        if (text.isNotBlank()) {
            val bytes = MiniProto.encodeToRadioTextMessage(text, localNodeNum, remoteNodeNum)
            activeConnection.send(bytes)
            val formatted = context.getString(R.string.you_voice_format, text)
            messages.add(UiMessage(context.getString(R.string.sender_you_voice), text, formatted))
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isNotBlank()) {
            val bytes = MiniProto.encodeToRadioTextMessage(text, localNodeNum, remoteNodeNum)
            activeConnection.send(bytes)
            val formatted = context.getString(R.string.you_format, text)
            messages.add(UiMessage(context.getString(R.string.sender_you), text, formatted))
        }
    }

    fun sendLocation(latitude: Double, longitude: Double, altitude: Double) {
        val bytes = MiniProto.encodeToRadioPosition(latitude, longitude, altitude, localNodeNum, remoteNodeNum)
        activeConnection.send(bytes)
        val formatted = context.getString(R.string.gps_sent_format, latitude.toString(), longitude.toString())
        messages.add(UiMessage(context.getString(R.string.sender_you), formatted, formatted, isGps = true, latitude = latitude.toString(), longitude = longitude.toString()))
    }

    fun sendBatteryStatus(level: Int) {
        val bytes = MiniProto.encodeToRadioBattery(level, localNodeNum, remoteNodeNum)
        activeConnection.send(bytes)
        val formatted = context.getString(R.string.battery_sent_format, level)
        messages.add(UiMessage(context.getString(R.string.sender_you), formatted, formatted))
    }

    fun openMap(lat: String, lon: String, context: Context) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun toggleMode() {
        isVoiceMode.value = !isVoiceMode.value
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        activeConnection.disconnect()
        ttsManager.shutdown()
    }
}
