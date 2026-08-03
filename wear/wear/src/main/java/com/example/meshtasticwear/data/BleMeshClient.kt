package com.example.meshtasticwear.data

class BleMeshClient(private val simulateSuccess: Boolean = false) : MeshConnection {
    
    private var onMsgReceived: ((ByteArray) -> Unit)? = null
    private var onStatusChg: ((String) -> Unit)? = null

    override fun connect(onMessageReceived: (ByteArray) -> Unit, onStatusChanged: (String) -> Unit) {
        this.onMsgReceived = onMessageReceived
        this.onStatusChg = onStatusChanged
        
        onStatusChg?.invoke("BLE GATT: Scanning...")
        
        if (simulateSuccess) {
            onStatusChg?.invoke("Connected (BLE)")
        } else {
            // Stub: In real production, initialize BluetoothAdapter and connect:
            // Service UUID: 6ba1b218-15a8-461f-9fa8-5dcae273eafd
            // Characteristic TORADIO (Write): f75c76d2-129e-4dad-a1dd-7866124401e7
            // Characteristic FROMRADIO (Read): 2c55e69e-4993-11ed-b878-0242ac120002
            // Characteristic FROMNUM (Notify): ed9da18c-a800-4f66-a670-aa7547e34453
            onStatusChg?.invoke("BLE GATT: Dongle not found (Stub)")
        }
    }

    override fun send(data: ByteArray) {
        // Write the data buffer to the TORADIO write characteristic
    }

    override fun disconnect() {
        onStatusChg?.invoke("Disconnected")
    }
}
