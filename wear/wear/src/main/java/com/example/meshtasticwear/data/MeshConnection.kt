package com.example.meshtasticwear.data

interface MeshConnection {
    /**
     * Starts the physical connection with the local radio/simulator.
     * @param onMessageReceived Callback triggered when a complete Protobuf payload is received.
     * @param onStatusChanged Callback for state changes ("Connecting", "Connected", "Disconnected", etc).
     */
    fun connect(onMessageReceived: (ByteArray) -> Unit, onStatusChanged: (String) -> Unit)

    /**
     * Sends a complete Protobuf payload to the radio/simulator.
     */
    fun send(data: ByteArray)

    /**
     * Closes the physical connection and releases socket/port resources.
     */
    fun disconnect()
}
