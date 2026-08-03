package com.example.meshtasticwear

import android.app.Application
import com.example.meshtasticwear.data.MeshConnection
import com.example.meshtasticwear.data.TcpMeshClient
import com.example.meshtasticwear.domain.TtsManager
import com.example.meshtasticwear.domain.NativeTtsManager

class MeshtasticApp : Application() {
    
    // Global dependencies exposed to allow injection in instrumented tests
    var ttsManager: TtsManager? = null
    var meshConnection: MeshConnection? = null
    var mockSayboardInstalled: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        // Default initializations for production (local offline)
        if (ttsManager == null) {
            ttsManager = NativeTtsManager(this)
        }
        if (meshConnection == null) {
            meshConnection = TcpMeshClient("10.0.2.2", 4403) // Default loopback IP of the Android emulator to the host machine
        }
    }
}
