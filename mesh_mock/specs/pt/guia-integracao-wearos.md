# Guia de Integração - Wear OS / Galaxy Watch 7

Este guia orienta os desenvolvedores a conectarem seus aplicativos do Galaxy Watch 7 (Wear OS) ao simulador Meshtastic executado localmente.

---

## 1. Conectando via TCP/IP (Wi-Fi Local)

Esta é a forma de integração mais recomendada e estável durante a fase de desenvolvimento local, pois dispensa pareamento Bluetooth direto do emulador ou do macOS.

### 1.1 Configuração da Rede no Galaxy Watch 7
Para que o relógio consiga se conectar ao simulador via Wi-Fi:
1. Certifique-se de que o **Galaxy Watch 7** e a máquina macOS rodando o simulador estão conectados à **mesma rede Wi-Fi local**.
2. Obtenha o IP local da sua máquina macOS (pode ser verificado nas configurações do macOS em Rede -> Wi-Fi -> Detalhes, ou via terminal executando `ipconfig getifaddr en0`). Exemplo: `192.168.1.15`.
3. No aplicativo Android / Kotlin rodando no relógio, configure a conexão do socket apontando para o IP do seu Mac e a porta padrão `4403`.

### 1.2 Implementação do Socket no Kotlin (Wear OS)
No Android/Kotlin, a conexão com o socket TCP e o tratamento do cabeçalho de framing do Meshtastic podem ser implementados da seguinte forma:

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
            
            // Inicia o handshake informando que deseja receber configurações
            sendWantConfig()
            
            // Inicia a escuta de pacotes recebidos do Mock Server
            listenLoop()
        }.start()
    }

    private fun sendWantConfig() {
        // Constrói o protobuf ToRadio com want_config_id = 1
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
                
                // Decodifica a mensagem FromRadio recebida
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
                println("Mensagem recebida do rádio: $messageText")
            }
        }
    }
}
```

---

## 2. Conectando via BLE GATT

Se você estiver aumentando o suporte a BLE nativo no relógio:
1. O simulador anunciará no macOS um dispositivo chamado `Meshtastic Mock Server` (ou o nome configurado).
2. O aplicativo Wear OS deve realizar o escaneamento BLE buscando pelo Service UUID `6ba1b218-15a8-461f-9fa8-5dcae273eafd` (ou `6ba1b080-b420-4be9-ae09-a94a325c3726`).
3. Ao conectar:
   - Defina o tamanho da MTU do BLE para `512` bytes no relógio (`requestMtu(512)`).
   - Ative as notificações na característica `FROMNUM` (`ed9da18c-a800-4f66-a670-aa7547e34453`).
   - Para iniciar a sincronização (Handshake), escreva o payload `ToRadio` (sem cabeçalho de 4 bytes) contendo `want_config_id` na característica `TORADIO` (`f75c76d2-129e-4dad-a1dd-7866124401e7`).
   - Quando receber uma notificação de alteração de valor na característica `FROMNUM`, realize uma leitura da característica `FROMRADIO` (`2c55e69e-4993-11ed-b878-0242ac120002`) para extrair os pacotes de resposta.

---

## 3. Estruturas Protobuf Esperadas

### 3.1 Mensagem Enviada pelo Wear OS ao Mock (`ToRadio`)
O payload binário enviado pelo relógio para enviar texto deve corresponder ao seguinte formato estrutural em JSON (que é serializado em binário Protobuf):

```json
{
  "packet": {
    "from": 123456, // O ID do nó do relógio
    "to": 4294967295, // 0xFFFFFFFF (Broadcast) ou o ID do nó de destino
    "channel": 0,
    "decoded": {
      "portnum": "TEXT_MESSAGE_APP", // Valor inteiro = 1
      "payload": "T2zDoSBNZXNoc3Rhc3RpYw==" // String em formato de bytes serializados (Base64 de "Olá Meshtastic")
    }
  }
}
```

### 3.2 Mensagem de Resposta Enviada pelo Mock (`FromRadio`)
O pacote de resposta gerado pelo Echo Bot e transmitido ao relógio possui a estrutura inversa:

```json
{
  "packet": {
    "from": 2345678, // ID do nó do Mock Server ("MCK1" short name; node num em decimal)
    "to": 123456, // ID do nó do relógio
    "channel": 0,
    "decoded": {
      "portnum": "TEXT_MESSAGE_APP",
      "payload": "UmVjZWJpZG8gdmlhIExvUmEgTW9jazogT2zDoSBNZXNoc3Rhc3RpYw==" // Base64 de "Received via LoRa Mock: Olá Meshtastic"
    }
  }
}
```
