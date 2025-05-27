package com.github.jhalak.spoe.protocol

import com.github.jhalak.spoe.*
import okio.*
import java.nio.charset.StandardCharsets

/**
 * SPOP binary protocol codec for encoding/decoding frames
 */
class SpopCodec {
    
    companion object {
        private const val MAX_FRAME_SIZE = 16384
        
        // Data type constants from SPOE specification
        private const val DATA_TYPE_NULL: UByte = 0u
        private const val DATA_TYPE_BOOL: UByte = 1u
        private const val DATA_TYPE_INT32: UByte = 2u
        private const val DATA_TYPE_UINT32: UByte = 3u
        private const val DATA_TYPE_INT64: UByte = 4u
        private const val DATA_TYPE_UINT64: UByte = 5u
        private const val DATA_TYPE_IPV4: UByte = 6u
        private const val DATA_TYPE_IPV6: UByte = 7u
        private const val DATA_TYPE_STR: UByte = 8u
        private const val DATA_TYPE_BIN: UByte = 9u
    }
    
    /**
     * Encode a SPOP frame to bytes
     */
    fun encode(frame: SpopFrame): ByteArray {
        val buffer = Buffer()
        
        when (frame) {
            is SpopFrame.AgentHello -> encodeAgentHello(buffer, frame)
            is SpopFrame.Ack -> encodeAck(buffer, frame)
            is SpopFrame.AgentDisconnect -> encodeAgentDisconnect(buffer, frame)
            else -> throw IllegalArgumentException("Cannot encode frame type: ${frame::class.simpleName}")
        }
        
        return buffer.readByteArray()
    }
    
    /**
     * Decode bytes to a SPOP frame
     */
    fun decode(data: ByteArray): SpopFrame {
        val buffer = Buffer().write(data)
        return decodeFrame(buffer)
    }
    
    private fun encodeAgentHello(buffer: BufferedSink, frame: SpopFrame.AgentHello) {
        buffer.writeByte(FrameType.AGENT_HELLO.value.toInt())
        buffer.writeByte(0) // flags
        writeVarInt(buffer, frame.streamId)
        writeVarInt(buffer, frame.frameId)
        
        // Encode capabilities and settings
        writeString(buffer, frame.version)
        writeVarInt(buffer, frame.maxFrameSize)
        
        // Write capabilities
        writeVarInt(buffer, frame.capabilities.size)
        frame.capabilities.forEach { capability ->
            writeString(buffer, capability)
        }
    }
    
    private fun encodeAck(buffer: BufferedSink, frame: SpopFrame.Ack) {
        buffer.writeByte(FrameType.ACK.value.toInt())
        buffer.writeByte(0) // flags
        writeVarInt(buffer, frame.streamId)
        writeVarInt(buffer, frame.frameId)
        
        // Encode actions
        writeVarInt(buffer, frame.actions.size)
        frame.actions.forEach { action ->
            encodeAction(buffer, action)
        }
    }
    
    private fun encodeAgentDisconnect(buffer: BufferedSink, frame: SpopFrame.AgentDisconnect) {
        buffer.writeByte(FrameType.AGENT_DISCONNECT.value.toInt())
        buffer.writeByte(0) // flags
        writeVarInt(buffer, frame.streamId)
        writeVarInt(buffer, frame.frameId)
        writeVarInt(buffer, frame.statusCode)
        writeString(buffer, frame.message)
    }
    
    private fun decodeFrame(buffer: BufferedSource): SpopFrame {
        val frameType = FrameType.fromValue(buffer.readByte().toUByte())
            ?: throw IllegalArgumentException("Unknown frame type")
        val streamId = readVarInt(buffer)
        val frameId = readVarInt(buffer)
        
        return when (frameType) {
            FrameType.HAPROXY_HELLO -> decodeHaproxyHello(buffer, streamId, frameId)
            FrameType.NOTIFY -> decodeNotify(buffer, streamId, frameId)
            FrameType.HAPROXY_DISCONNECT -> decodeHaproxyDisconnect(buffer, streamId, frameId)
            else -> throw IllegalArgumentException("Cannot decode frame type: $frameType")
        }
    }
    
    private fun decodeHaproxyHello(buffer: BufferedSource, streamId: Int, frameId: Int): SpopFrame.HaproxyHello {
        val supportedVersions = mutableListOf<String>()
        val capabilities = mutableSetOf<String>()
        var maxFrameSize = MAX_FRAME_SIZE
        
        // Read key-value pairs
        while (!buffer.exhausted()) {
            val key = readString(buffer)
            when (key) {
                "supported-versions" -> {
                    val count = readVarInt(buffer)
                    repeat(count) {
                        supportedVersions.add(readString(buffer))
                    }
                }
                "max-frame-size" -> {
                    maxFrameSize = readVarInt(buffer)
                }
                "capabilities" -> {
                    val count = readVarInt(buffer)
                    repeat(count) {
                        capabilities.add(readString(buffer))
                    }
                }
            }
        }
        
        return SpopFrame.HaproxyHello(streamId, frameId, supportedVersions, maxFrameSize, capabilities)
    }
    
    private fun decodeNotify(buffer: BufferedSource, streamId: Int, frameId: Int): SpopFrame.Notify {
        val messages = mutableListOf<SpoeMessage>()
        val messageCount = readVarInt(buffer)
        
        repeat(messageCount) {
            val messageName = readString(buffer)
            val argCount = readVarInt(buffer)
            val args = mutableMapOf<String, SpoeValue>()
            
            repeat(argCount) {
                val argName = readString(buffer)
                val argValue = readSpoeValue(buffer)
                args[argName] = argValue
            }
            
            messages.add(SpoeMessage(messageName, args))
        }
        
        return SpopFrame.Notify(streamId, frameId, messages)
    }
    
    private fun decodeHaproxyDisconnect(buffer: BufferedSource, streamId: Int, frameId: Int): SpopFrame.HaproxyDisconnect {
        val statusCode = readVarInt(buffer)
        val message = readString(buffer)
        return SpopFrame.HaproxyDisconnect(streamId, frameId, statusCode, message)
    }
    
    private fun encodeAction(buffer: BufferedSink, action: SpoeAction) {
        when (action) {
            is SpoeAction.SetVariable -> {
                buffer.writeByte(1) // SET_VAR action type
                buffer.writeByte(action.scope.value.toInt())
                writeString(buffer, action.name)
                writeSpoeValue(buffer, action.value)
            }
            is SpoeAction.UnsetVariable -> {
                buffer.writeByte(2) // UNSET_VAR action type
                buffer.writeByte(action.scope.value.toInt())
                writeString(buffer, action.name)
            }
        }
    }
    
    private fun writeSpoeValue(buffer: BufferedSink, value: SpoeValue) {
        when (value) {
            is SpoeValue.Null -> buffer.writeByte(DATA_TYPE_NULL.toInt())
            is SpoeValue.Boolean -> {
                buffer.writeByte(DATA_TYPE_BOOL.toInt())
                buffer.writeByte(if (value.value) 1 else 0)
            }
            is SpoeValue.Int32 -> {
                buffer.writeByte(DATA_TYPE_INT32.toInt())
                writeVarInt(buffer, value.value)
            }
            is SpoeValue.UInt32 -> {
                buffer.writeByte(DATA_TYPE_UINT32.toInt())
                writeVarUInt(buffer, value.value)
            }
            is SpoeValue.Int64 -> {
                buffer.writeByte(DATA_TYPE_INT64.toInt())
                writeVarLong(buffer, value.value)
            }
            is SpoeValue.UInt64 -> {
                buffer.writeByte(DATA_TYPE_UINT64.toInt())
                writeVarULong(buffer, value.value)
            }
            is SpoeValue.IPv4 -> {
                buffer.writeByte(DATA_TYPE_IPV4.toInt())
                buffer.write(value.address)
            }
            is SpoeValue.IPv6 -> {
                buffer.writeByte(DATA_TYPE_IPV6.toInt())
                buffer.write(value.address)
            }
            is SpoeValue.String -> {
                buffer.writeByte(DATA_TYPE_STR.toInt())
                writeString(buffer, value.value)
            }
            is SpoeValue.Binary -> {
                buffer.writeByte(DATA_TYPE_BIN.toInt())
                writeVarInt(buffer, value.data.size)
                buffer.write(value.data)
            }
        }
    }
    
    private fun readSpoeValue(buffer: BufferedSource): SpoeValue {
        val dataType = buffer.readByte().toUByte()
        return when (dataType) {
            DATA_TYPE_NULL -> SpoeValue.Null
            DATA_TYPE_BOOL -> SpoeValue.Boolean(buffer.readByte() != 0.toByte())
            DATA_TYPE_INT32 -> SpoeValue.Int32(readVarInt(buffer))
            DATA_TYPE_UINT32 -> SpoeValue.UInt32(readVarUInt(buffer))
            DATA_TYPE_INT64 -> SpoeValue.Int64(readVarLong(buffer))
            DATA_TYPE_UINT64 -> SpoeValue.UInt64(readVarULong(buffer))
            DATA_TYPE_IPV4 -> SpoeValue.IPv4(buffer.readByteArray(4))
            DATA_TYPE_IPV6 -> SpoeValue.IPv6(buffer.readByteArray(16))
            DATA_TYPE_STR -> SpoeValue.String(readString(buffer))
            DATA_TYPE_BIN -> {
                val length = readVarInt(buffer)
                SpoeValue.Binary(buffer.readByteArray(length.toLong()))
            }
            else -> throw IllegalArgumentException("Unknown data type: $dataType")
        }
    }
    
    // Variable-length integer encoding/decoding (LEB128)
    private fun writeVarInt(buffer: BufferedSink, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            buffer.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        buffer.writeByte(v and 0x7F)
    }
    
    private fun readVarInt(buffer: BufferedSource): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = buffer.readByte().toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return result
    }
    
    private fun writeVarUInt(buffer: BufferedSink, value: UInt) = writeVarInt(buffer, value.toInt())
    private fun readVarUInt(buffer: BufferedSource): UInt = readVarInt(buffer).toUInt()
    
    private fun writeVarLong(buffer: BufferedSink, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            buffer.writeByte(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
        buffer.writeByte((v and 0x7FL).toInt())
    }
    
    private fun readVarLong(buffer: BufferedSource): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = buffer.readByte().toLong() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0L) break
            shift += 7
        }
        return result
    }
    
    private fun writeVarULong(buffer: BufferedSink, value: ULong) = writeVarLong(buffer, value.toLong())
    private fun readVarULong(buffer: BufferedSource): ULong = readVarLong(buffer).toULong()
    
    private fun writeString(buffer: BufferedSink, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(buffer, bytes.size)
        buffer.write(bytes)
    }
    
    private fun readString(buffer: BufferedSource): String {
        val length = readVarInt(buffer)
        val bytes = buffer.readByteArray(length.toLong())
        return String(bytes, StandardCharsets.UTF_8)
    }
}
