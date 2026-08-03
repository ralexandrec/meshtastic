package com.example.meshtasticwear.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

object MiniProto {

    fun writeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0L) {
                out.write(v.toInt())
                break
            } else {
                out.write((v.toInt() and 0x7F) or 0x80)
                v = v ushr 7
            }
        }
        return out.toByteArray()
    }

    fun readVarint(stream: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = stream.read()
            if (b == -1) throw EOFException()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        throw IllegalArgumentException("Varint too long")
    }

    fun writeFixed32(value: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (value and 0xFF).toByte()
        b[1] = ((value ushr 8) and 0xFF).toByte()
        b[2] = ((value ushr 16) and 0xFF).toByte()
        b[3] = ((value ushr 24) and 0xFF).toByte()
        return b
    }

    fun writeFixed32Field(fieldNumber: Int, value: Int): ByteArray {
        val tag = (fieldNumber shl 3) or 5
        return writeVarint(tag.toLong()) + writeFixed32(value)
    }

    fun readFixed32(stream: InputStream): Long {
        val b = ByteArray(4)
        var readBytes = 0
        while (readBytes < 4) {
            val r = stream.read(b, readBytes, 4 - readBytes)
            if (r == -1) throw EOFException()
            readBytes += r
        }
        return (((b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16)).toLong() or
                ((b[3].toInt() and 0xFF).toLong() shl 24)) and 0xFFFFFFFFL
    }

    fun writeVarintField(fieldNumber: Int, value: Long): ByteArray {
        val tag = (fieldNumber shl 3) or 0
        return writeVarint(tag.toLong()) + writeVarint(value)
    }

    fun writeBytesField(fieldNumber: Int, value: ByteArray): ByteArray {
        val tag = (fieldNumber shl 3) or 2
        return writeVarint(tag.toLong()) + writeVarint(value.size.toLong()) + value
    }

    fun writeStringField(fieldNumber: Int, value: String): ByteArray {
        return writeBytesField(fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

    fun encodeZigZag(value: Int): Long {
        return ((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL
    }

    fun decodeZigZag(z: Long): Long {
        return (z ushr 1) xor -(z and 1)
    }

    fun skipField(stream: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(stream)
            1 -> {
                val b = ByteArray(8)
                stream.read(b)
            }
            2 -> {
                val length = readVarint(stream).toInt()
                val b = ByteArray(length)
                stream.read(b)
            }
            5 -> {
                val b = ByteArray(4)
                stream.read(b)
            }
            else -> throw IllegalArgumentException("Unknown wire type: $wireType")
        }
    }

    // --- ENCODERS ---

    fun encodeToRadioWantConfig(configId: Int): ByteArray {
        // ToRadio: want_config_id = tag 2 (varint)
        return writeVarintField(2, configId.toLong())
    }

    fun encodeToRadioTextMessage(text: String, fromNode: Int, toNode: Int): ByteArray {
        // 1. Data (portnum = 1, payload = bytes)
        val dataBytes = writeVarintField(1, 1L) + writeBytesField(2, text.toByteArray(Charsets.UTF_8))
        
        // 2. MeshPacket (from = 1 (fixed32), to = 2 (fixed32), channel = 3, decoded = 4, id = 6 (fixed32))
        val packetBytes = writeFixed32Field(1, fromNode) +
                writeFixed32Field(2, toNode) +
                writeVarintField(3, 0L) +
                writeBytesField(4, dataBytes) +
                writeFixed32Field(6, ((System.currentTimeMillis() / 1000) and 0xFFFFFFFFL).toInt())

        // 3. ToRadio (packet = 1)
        return writeBytesField(1, packetBytes)
    }

    fun encodeFromRadioTextMessage(text: String, fromNode: Int, toNode: Int): ByteArray {
        val dataBytes = writeVarintField(1, 1L) + writeBytesField(2, text.toByteArray(Charsets.UTF_8))
        val packetBytes = writeFixed32Field(1, fromNode) +
                writeFixed32Field(2, toNode) +
                writeVarintField(3, 0L) +
                writeBytesField(4, dataBytes) +
                writeFixed32Field(6, ((System.currentTimeMillis() / 1000) and 0xFFFFFFFFL).toInt())
        // FromRadio has packet field at tag 2
        return writeBytesField(2, packetBytes)
    }

    fun encodeToRadioPosition(lat: Double, lon: Double, alt: Double, fromNode: Int, toNode: Int): ByteArray {
        val latI = (lat * 1e7).toInt()
        val lonI = (lon * 1e7).toInt()
        
        // Position (latitude_i = 1 (sint32), longitude_i = 2 (sint32), altitude = 3 (int32))
        val positionBytes = writeVarintField(1, encodeZigZag(latI)) +
                writeVarintField(2, encodeZigZag(lonI)) +
                writeVarintField(3, alt.toLong())

        // Data (portnum = 3 (POSITION_APP), payload = positionBytes)
        val dataBytes = writeVarintField(1, 3L) + writeBytesField(2, positionBytes)

        // MeshPacket
        val packetBytes = writeFixed32Field(1, fromNode) +
                writeFixed32Field(2, toNode) +
                writeVarintField(3, 0L) +
                writeBytesField(4, dataBytes) +
                writeFixed32Field(6, ((System.currentTimeMillis() / 1000) and 0xFFFFFFFFL).toInt())

        // ToRadio
        return writeBytesField(1, packetBytes)
    }

    fun encodeFromRadioPosition(lat: Double, lon: Double, alt: Double, fromNode: Int, toNode: Int): ByteArray {
        val latI = (lat * 1e7).toInt()
        val lonI = (lon * 1e7).toInt()
        
        val positionBytes = writeVarintField(1, encodeZigZag(latI)) +
                writeVarintField(2, encodeZigZag(lonI)) +
                writeVarintField(3, alt.toLong())

        val dataBytes = writeVarintField(1, 3L) + writeBytesField(2, positionBytes)

        val packetBytes = writeFixed32Field(1, fromNode) +
                writeFixed32Field(2, toNode) +
                writeVarintField(3, 0L) +
                writeBytesField(4, dataBytes) +
                writeFixed32Field(6, ((System.currentTimeMillis() / 1000) and 0xFFFFFFFFL).toInt())

        // FromRadio has packet field at tag 2
        return writeBytesField(2, packetBytes)
    }

    fun encodeToRadioBattery(batteryLevel: Int, fromNode: Int, toNode: Int): ByteArray {
        // DeviceMetrics (battery_level = 1 (uint32))
        val deviceMetricsBytes = writeVarintField(1, batteryLevel.toLong())

        // Telemetry (device_metrics = 2)
        val telemetryBytes = writeBytesField(2, deviceMetricsBytes)

        // Data (portnum = 4 (TELEMETRY_APP), payload = telemetryBytes)
        val dataBytes = writeVarintField(1, 4L) + writeBytesField(2, telemetryBytes)

        // MeshPacket
        val packetBytes = writeFixed32Field(1, fromNode) +
                writeFixed32Field(2, toNode) +
                writeVarintField(3, 0L) +
                writeBytesField(4, dataBytes) +
                writeFixed32Field(6, ((System.currentTimeMillis() / 1000) and 0xFFFFFFFFL).toInt())

        // ToRadio
        return writeBytesField(1, packetBytes)
    }

    // --- DECODER ---

    class FromRadioParser(data: ByteArray) {
        var hasPacket = false
        var packetFrom = 0L
        var packetTo = 0L
        var packetText = ""
        var configCompleteId = 0L

        init {
            parse(data)
        }

        private fun parse(data: ByteArray) {
            val stream = ByteArrayInputStream(data)
            try {
                while (stream.available() > 0) {
                    val tag = readVarint(stream).toInt()
                    val fieldNumber = tag ushr 3
                    val wireType = tag and 0x07
                    
                    when (fieldNumber) {
                        2 -> { // packet
                            val length = readVarint(stream).toInt()
                            val packetBytes = ByteArray(length)
                            stream.read(packetBytes)
                            parseMeshPacket(packetBytes)
                        }
                        7 -> { // config_complete_id
                            configCompleteId = readVarint(stream)
                        }
                        else -> skipField(stream, wireType)
                    }
                }
            } catch (e: Exception) {}
        }

        private fun parseMeshPacket(bytes: ByteArray) {
            val stream = ByteArrayInputStream(bytes)
            var decodedBytes: ByteArray? = null
            try {
                while (stream.available() > 0) {
                    val tag = readVarint(stream).toInt()
                    val fieldNumber = tag ushr 3
                    val wireType = tag and 0x07
                    when (fieldNumber) {
                        1 -> packetFrom = readFixed32(stream)
                        2 -> packetTo = readFixed32(stream)
                        4 -> { // decoded
                            val length = readVarint(stream).toInt()
                            decodedBytes = ByteArray(length)
                            stream.read(decodedBytes)
                        }
                        else -> skipField(stream, wireType)
                    }
                }
                if (decodedBytes != null) {
                    parseData(decodedBytes)
                }
            } catch (e: Exception) {}
        }

        private fun parseData(bytes: ByteArray) {
            val stream = ByteArrayInputStream(bytes)
            var portnum = 0L
            var payloadBytes: ByteArray? = null
            try {
                while (stream.available() > 0) {
                    val tag = readVarint(stream).toInt()
                    val fieldNumber = tag ushr 3
                    val wireType = tag and 0x07
                    when (fieldNumber) {
                        1 -> portnum = readVarint(stream)
                        2 -> { // payload
                            val length = readVarint(stream).toInt()
                            payloadBytes = ByteArray(length)
                            stream.read(payloadBytes)
                        }
                        else -> skipField(stream, wireType)
                    }
                }
                if (portnum == 1L && payloadBytes != null) { // TEXT_MESSAGE_APP
                    packetText = String(payloadBytes, Charsets.UTF_8)
                    hasPacket = true
                } else if (portnum == 3L && payloadBytes != null) { // POSITION_APP
                    var latVal = 0L
                    var lonVal = 0L
                    var altVal = 0L
                    try {
                        val posStream = ByteArrayInputStream(payloadBytes)
                        while (posStream.available() > 0) {
                            val posTag = readVarint(posStream).toInt()
                            val posFieldNumber = posTag ushr 3
                            val posWireType = posTag and 0x07
                            when (posFieldNumber) {
                                1 -> latVal = decodeZigZag(readVarint(posStream))
                                2 -> lonVal = decodeZigZag(readVarint(posStream))
                                3 -> altVal = readVarint(posStream)
                                else -> skipField(posStream, posWireType)
                            }
                        }
                        val latD = latVal / 1e7
                        val lonD = lonVal / 1e7
                        packetText = "GPS: Lat $latD, Lon $lonD, Alt ${altVal}m"
                        hasPacket = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (portnum == 4L && payloadBytes != null) { // TELEMETRY_APP
                    var batteryLevel = -1L
                    try {
                        val telStream = ByteArrayInputStream(payloadBytes)
                        while (telStream.available() > 0) {
                            val telTag = readVarint(telStream).toInt()
                            val telFieldNumber = telTag ushr 3
                            val telWireType = telTag and 0x07
                            when (telFieldNumber) {
                                2 -> { // device_metrics
                                    val length = readVarint(telStream).toInt()
                                    val metricsBytes = ByteArray(length)
                                    telStream.read(metricsBytes)
                                    
                                    val metStream = ByteArrayInputStream(metricsBytes)
                                    while (metStream.available() > 0) {
                                        val metTag = readVarint(metStream).toInt()
                                        val metFieldNumber = metTag ushr 3
                                        val metWireType = metTag and 0x07
                                        when (metFieldNumber) {
                                            1 -> batteryLevel = readVarint(metStream)
                                            else -> skipField(metStream, metWireType)
                                        }
                                    }
                                }
                                else -> skipField(telStream, telWireType)
                            }
                        }
                        if (batteryLevel != -1L) {
                            packetText = "Telemetry: Battery ${batteryLevel}%"
                            hasPacket = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
