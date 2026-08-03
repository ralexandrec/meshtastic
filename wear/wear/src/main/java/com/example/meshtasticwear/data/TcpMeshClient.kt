package com.example.meshtasticwear.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TcpMeshClient(
    private val ip: String,
    private val port: Int
) : MeshConnection {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionJob: Job? = null

    private var onMsgReceived: ((ByteArray) -> Unit)? = null
    private var onStatusChg: ((String) -> Unit)? = null

    override fun connect(onMessageReceived: (ByteArray) -> Unit, onStatusChanged: (String) -> Unit) {
        this.onMsgReceived = onMessageReceived
        this.onStatusChg = onStatusChanged

        if (isRunning) return
        isRunning = true

        connectionJob = scope.launch {
            onStatusChg?.invoke("Connecting...")

            val connectedSocket = tryEstablishConnection()
            if (connectedSocket == null || !isActive || !isRunning) {
                onStatusChg?.invoke("Disconnected")
                cleanUp()
                return@launch
            }

            socket = connectedSocket
            outputStream = connectedSocket.getOutputStream()
            inputStream = connectedSocket.getInputStream()

            onStatusChg?.invoke("Connected")
            readLoop(connectedSocket.getInputStream())
        }
    }

    /**
     * Tries to establish a socket connection using exponential backoff retry.
     * Evaluates candidate target hosts (configured IP, emulator loopback, localhost).
     */
    private suspend fun tryEstablishConnection(): Socket? = withContext(Dispatchers.IO) {
        val candidateHosts = listOf(ip, "10.0.2.2", "127.0.0.1").distinct()
        var currentDelayMs = 500L
        val maxDelayMs = 3000L
        val maxAttempts = 10

        for (attempt in 1..maxAttempts) {
            if (!isRunning || !isActive) return@withContext null

            for (host in candidateHosts) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 2000)
                    return@withContext s
                } catch (_: Exception) {
                    // Try next candidate host
                }
            }

            delay(currentDelayMs)
            currentDelayMs = (currentDelayMs * 15 / 10).coerceAtMost(maxDelayMs)
        }
        null
    }

    private suspend fun readLoop(stream: InputStream) = withContext(Dispatchers.IO) {
        val headerBuffer = ByteArray(4)

        try {
            while (isRunning && isActive) {
                var readBytes = 0
                while (readBytes < 4) {
                    val r = stream.read(headerBuffer, readBytes, 4 - readBytes)
                    if (r == -1) throw java.io.EOFException("Socket closed by remote peer")
                    readBytes += r
                }

                // Check magic bytes (0x94, 0xC3)
                if (headerBuffer[0] != 0x94.toByte() || headerBuffer[1] != 0xC3.toByte()) {
                    continue
                }

                val size = ByteBuffer.wrap(headerBuffer, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                val payload = ByteArray(size)
                var payloadReadBytes = 0
                while (payloadReadBytes < size) {
                    val r = stream.read(payload, payloadReadBytes, size - payloadReadBytes)
                    if (r == -1) throw java.io.EOFException("Socket closed during payload read")
                    payloadReadBytes += r
                }

                onMsgReceived?.invoke(payload)
            }
        } catch (e: Exception) {
            onStatusChg?.invoke("Error: ${e.message}")
        } finally {
            cleanUp()
            onStatusChg?.invoke("Disconnected")
        }
    }

    override fun send(data: ByteArray) {
        scope.launch {
            try {
                val out = outputStream ?: return@launch
                val header = ByteBuffer.allocate(4).apply {
                    order(ByteOrder.BIG_ENDIAN)
                    put(0x94.toByte())
                    put(0xC3.toByte())
                    putShort(data.size.toShort())
                }.array()

                synchronized(out) {
                    out.write(header)
                    out.write(data)
                    out.flush()
                }
            } catch (_: Exception) {
                // Send errors handled cleanly
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        connectionJob?.cancel()
        onStatusChg = null
        onMsgReceived = null
        cleanUp()
    }

    private fun cleanUp() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
    }
}
