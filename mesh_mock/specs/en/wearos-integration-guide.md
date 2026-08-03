# Integration Guide - Wear OS / Galaxy Watch 7

This guide helps developers connect their Galaxy Watch 7 (Wear OS) applications to the locally running Meshtastic simulator.

---

## 1. Connecting via TCP/IP (Local Wi-Fi)

This is the most recommended and stable integration method during the local development phase, as it eliminates the need for direct Bluetooth pairing from the emulator or macOS.

### 1.1 Network Configuration on Galaxy Watch 7
For the watch to connect to the simulator via Wi-Fi:
1. Make sure that the **Galaxy Watch 7** and the macOS machine running the simulator are connected to the **same local Wi-Fi network**.
2. Get the local IP of your macOS machine (can be checked in macOS settings under Network -> Wi-Fi -> Details, or via terminal by running `ipconfig getifaddr en0`). Example: `192.168.1.15`.
3. In the Android / Kotlin application running on the watch, configure the socket connection to point to your Mac's IP and default port `4403`.

### 1.2 Socket Implementation in Kotlin (Wear OS)
In Android/Kotlin, the TCP socket connection and the handling of the Meshtastic framing header can be implemented as follows:

```kotlin
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MeshtasticClient(private val ip: String, private val port: Int) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    fun connect() {
        Thread {
            socket = Socket(ip, port)
            outputStream = socket?.getOutputStream()
            inputStream = socket?.getInputStream()
            
            // Starts the handshake indicating it wants to receive configurations
            sendWantConfig()
            
            // Starts listening to packets received from the Mock Server
            listenLoop()
        }.start()
    }

    private fun sendWantConfig() {
        // Builds the ToRadio protobuf with want_config_id = 1
        val toRadio = mesh.Mesh.ToRadio.newBuilder()
            .setWantConfigId(1)
            .build()
        sendPacket(toRadio.toByteArray())
    }

    private fun sendPacket(payload: ByteArray) {
        val header = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            put(0x94.toByte())
            put(0xC3.toByte())
            putShort(payload.size.toShort())
        }.array()

        outputStream?.write(header)
        outputStream?.write(payload)
        outputStream?.flush()
    }

    private fun listenLoop() {
        val stream = inputStream ?: return
        val header = ByteArray(4)
        while (true) {
            var read = stream.read(header, 0, 4)
            if (read == -1) break
            
            if (header[0] == 0x94.toByte() && header[1] == 0xC3.toByte()) {
                val size = ByteBuffer.wrap(header, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                val payload = ByteArray(size)
                var bytesRead = 0
                while (bytesRead < size) {
                    val r = stream.read(payload, bytesRead, size - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }
                
                // Decodes the received FromRadio message
                val fromRadio = mesh.Mesh.FromRadio.parseFrom(payload)
                handleIncomingMessage(fromRadio)
            }
        }
    }

    private fun handleIncomingMessage(fromRadio: mesh.Mesh.FromRadio) {
        if (fromRadio.hasPacket()) {
            val packet = fromRadio.packet
            if (packet.decoded.portnumValue == 1) { // TEXT_MESSAGE_APP
                val messageText = packet.decoded.payload.toStringUtf8()
                println("Message received from radio: $messageText")
            }
        }
    }
}
```

---

## 2. Connecting via BLE GATT

If you are developing native BLE support on the watch:
1. The simulator will advertise a device called `Meshtastic Mock Server` (or the configured name) on macOS.
2. The Wear OS application must scan for BLE devices searching for Service UUID `6ba1b218-15a8-461f-9fa8-5dcae273eafd` (or `6ba1b080-b420-4be9-ae09-a94a325c3726`).
3. Upon connection:
   - Set the BLE MTU size to `512` bytes on the watch (`requestMtu(512)`).
   - Enable notifications on the `FROMNUM` characteristic (`ed9da18c-a800-4f66-a670-aa7547e34453`).
   - To start synchronization (Handshake), write the `ToRadio` payload (without the 4-byte header) containing `want_config_id` to the `TORADIO` characteristic (`f75c76d2-129e-4dad-a1dd-7866124401e7`).
   - When a value change notification is received on the `FROMNUM` characteristic, read the `FROMRADIO` characteristic (`2c55e69e-4993-11ed-b878-0242ac120002`) to extract the response packets.

---

## 3. Expected Protobuf Structures

### 3.1 Message Sent by Wear OS to the Mock (`ToRadio`)
The binary payload sent by the watch to send text should match the following JSON structural format (which is serialized into Protobuf binary):

```json
{
  "packet": {
    "from": 123456, // The watch's node ID
    "to": 4294967295, // 0xFFFFFFFF (Broadcast) or the destination node ID
    "channel": 0,
    "decoded": {
      "portnum": "TEXT_MESSAGE_APP", // Integer value = 1
      "payload": "T2zDoSBNZXNoc3Rhc3RpYw==" // Base64 serialized bytes of "Olá Meshtastic"
    }
  }
}
```

### 3.2 Response Message Sent by the Mock (`FromRadio`)
The response packet generated by the Echo Bot and transmitted to the watch has the reverse structure:

```json
{
  "packet": {
    "from": 2345678, // Mock Server node ID ("MCK1" short name; node num in decimal)
    "to": 123456, // The watch's node ID
    "channel": 0,
    "decoded": {
      "portnum": "TEXT_MESSAGE_APP",
      "payload": "UmVjZWJpZG8gdmlhIExvUmEgTW9jazogT2zDoSBNZXNoc3Rhc3RpYw==" // Base64 serialized bytes of "Received via LoRa Mock: Olá Meshtastic"
    }
  }
}
```
