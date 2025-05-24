package com.github.jhalak.spoe.protocol

import com.github.jhalak.spoe.SpoeAction
import com.github.jhalak.spoe.SpoeMessage

/**
 * SPOP Frame Types as defined in HAProxy SPOE specification
 */
enum class FrameType(val value: UByte) {
    HAPROXY_HELLO(1u),
    AGENT_HELLO(2u),
    NOTIFY(3u),
    ACK(4u),
    HAPROXY_DISCONNECT(5u),
    AGENT_DISCONNECT(6u);
    
    companion object {
        fun fromValue(value: UByte): FrameType? = values().find { it.value == value }
    }
}

/**
 * SPOP Frame base class
 */
sealed class SpopFrame {
    abstract val streamId: Int
    abstract val frameId: Int
    
    /**
     * HAProxy Hello frame - sent by HAProxy to initiate handshake
     */
    data class HaproxyHello(
        override val streamId: Int = 0,
        override val frameId: Int = 0,
        val supportedVersions: List<String>,
        val maxFrameSize: Int,
        val capabilities: Set<String>
    ) : SpopFrame()
    
    /**
     * Agent Hello frame - response from agent during handshake
     */
    data class AgentHello(
        override val streamId: Int = 0,
        override val frameId: Int = 0,
        val version: String,
        val maxFrameSize: Int,
        val capabilities: Set<String>
    ) : SpopFrame()
    
    /**
     * Notify frame - contains SPOE messages from HAProxy
     */
    data class Notify(
        override val streamId: Int,
        override val frameId: Int,
        val messages: List<SpoeMessage>
    ) : SpopFrame()
    
    /**
     * ACK frame - response from agent with actions
     */
    data class Ack(
        override val streamId: Int,
        override val frameId: Int,
        val actions: List<SpoeAction>
    ) : SpopFrame()
    
    /**
     * HAProxy Disconnect frame - sent by HAProxy when closing connection
     */
    data class HaproxyDisconnect(
        override val streamId: Int = 0,
        override val frameId: Int = 0,
        val statusCode: Int,
        val message: String
    ) : SpopFrame()
    
    /**
     * Agent Disconnect frame - sent by agent when closing connection
     */
    data class AgentDisconnect(
        override val streamId: Int = 0,
        override val frameId: Int = 0,
        val statusCode: Int,
        val message: String
    ) : SpopFrame()
}

/**
 * SPOP Frame flags
 */
object FrameFlags {
    const val FRAGMENTED: UByte = 0x01u
    const val ABORT: UByte = 0x02u
}

/**
 * SPOP Status codes
 */
object StatusCodes {
    const val OK = 0
    const val RETRY = 1
    const val STOP = 2
    const val ABORT = 3
}
