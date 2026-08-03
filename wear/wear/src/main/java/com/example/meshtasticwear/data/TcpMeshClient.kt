package com.example.meshtasticwear.data

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class TcpMeshClient(private val ip: String, private val port: Int) : MeshConnection {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false
    private var onMsgReceived: ((ByteArray) -> Unit)? = null
    private var onStatusChg: ((String) -> Unit)? = null

    override fun connect(onMessageReceived: (ByteArray) -> Unit, onStatusChanged: (String) -> Unit) {
        this.onMsgReceived = onMessageReceived
        this.onStatusChg = onStatusChanged
        
        if (isRunning) return
        isRunning = true
        
        thread(start = true, name = "TCPMeshReadThread") {
            onStatusChg?.invoke("Connecting...")
            try {
                socket = Socket(ip, port)
                outputStream = socket?.getOutputStream()
                inputStream = socket?.getInputStream()
                onStatusChg?.invoke("Connected")
                
                val stream = inputStream ?: return@thread
                val headerBuffer = ByteArray(4)
                
                while (isRunning) {
                    // Read 4 header bytes
                    var readBytes = 0
                    while (readBytes < 4) {
                        val r = stream.read(headerBuffer, readBytes, 4 - readBytes)
                        if (r == -1) throw java.io.EOFException("Socket closed")
                        readBytes += r
                    }
                    
                    if (headerBuffer[0] != 0x94.toByte() || headerBuffer[1] != 0xC3.toByte()) {
                        continue // Ignore invalid magic bytes
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
    }

    override fun send(data: ByteArray) {
        thread(start = true) {
            try {
                val out = outputStream ?: return@thread
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
            } catch (e: Exception) {
                // Send error handled silently
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        onStatusChg = null
        onMsgReceived = null
        cleanUp()
    }

    private fun cleanUp() {
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
    }
}
