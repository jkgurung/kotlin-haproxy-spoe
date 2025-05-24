package com.github.jhalak.spoe

import com.github.jhalak.spoe.protocol.*
import kotlinx.coroutines.*
import okio.buffer
import okio.sink
import okio.source
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main SPOE Engine that handles HAProxy SPOE connections
 */
class SpoeEngine(
    private val port: Int,
    private val agent: SpoeAgent,
    private val config: SpoeConfig = SpoeConfig()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)
    private val codec = SpopCodec()
    
    /**
     * Start the SPOE server
     */
    suspend fun start() {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException("SPOE Engine is already running")
        }
        
        val serverSocket = ServerSocket(port)
        println("SPOE Engine started on port $port")
        
        try {
            while (running.get()) {
                try {
                    val clientSocket = serverSocket.accept()
                    scope.launch {
                        handleConnection(clientSocket)
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        println("Error accepting connection: ${e.message}")
                    }
                }
            }
        } finally {
            serverSocket.close()
            scope.cancel()
            running.set(false)
        }
    }
    
    /**
     * Stop the SPOE server
     */
    fun stop() {
        running.set(false)
        scope.cancel()
    }
    
    private suspend fun handleConnection(socket: Socket) {
        try {
            val source = socket.getInputStream().source().buffer()
            val sink = socket.getOutputStream().sink().buffer()
            
            // Perform handshake
            val negotiated = performHandshake(source, sink)
            if (!negotiated) {
                socket.close()
                return
            }
            
            // Process messages
            while (!socket.isClosed && running.get()) {
                try {
                    val frameData = readFrame(source)
                    val frame = codec.decode(frameData)
                    
                    when (frame) {
                        is SpopFrame.Notify -> {
                            val actions = processNotifyFrame(frame)
                            val ackFrame = SpopFrame.Ack(
                                streamId = frame.streamId,
                                frameId = frame.frameId,
                                actions = actions
                            )
                            val responseData = codec.encode(ackFrame)
                            writeFrame(sink, responseData)
                        }
                        
                        is SpopFrame.HaproxyDisconnect -> {
                            println("HAProxy requested disconnect: ${frame.message}")
                            break
                        }
                        
                        else -> {
                            println("Unexpected frame type: ${frame::class.simpleName}")
                        }
                    }
                } catch (e: Exception) {
                    println("Error processing frame: ${e.message}")
                    break
                }
            }
        } catch (e: Exception) {
            println("Connection error: ${e.message}")
        } finally {
            socket.close()
        }
    }
    
    private suspend fun performHandshake(source: okio.BufferedSource, sink: okio.BufferedSink): Boolean {
        return try {
            // Read HAPROXY-HELLO frame
            val helloFrameData = readFrame(source)
            val helloFrame = codec.decode(helloFrameData) as SpopFrame.HaproxyHello
            
            println("Received HAPROXY-HELLO from HAProxy")
            println("Supported versions: ${helloFrame.supportedVersions}")
            println("Max frame size: ${helloFrame.maxFrameSize}")
            println("Capabilities: ${helloFrame.capabilities}")
            
            // Choose compatible version (simplified - just pick the first supported)
            val version = helloFrame.supportedVersions.firstOrNull() ?: "2.0"
            
            // Determine supported capabilities
            val supportedCapabilities = mutableSetOf<String>()
            if ("pipelining" in helloFrame.capabilities) {
                supportedCapabilities.add("pipelining")
            }
            
            // Send AGENT-HELLO response
            val agentHello = SpopFrame.AgentHello(
                version = version,
                maxFrameSize = minOf(helloFrame.maxFrameSize, config.maxFrameSize),
                capabilities = supportedCapabilities
            )
            
            val responseData = codec.encode(agentHello)
            writeFrame(sink, responseData)
            
            println("Sent AGENT-HELLO response")
            println("Using version: $version")
            println("Max frame size: ${agentHello.maxFrameSize}")
            println("Capabilities: ${agentHello.capabilities}")
            
            true
        } catch (e: Exception) {
            println("Handshake failed: ${e.message}")
            false
        }
    }
    
    private suspend fun processNotifyFrame(frame: SpopFrame.Notify): List<SpoeAction> {
        val allActions = mutableListOf<SpoeAction>()
        
        for (message in frame.messages) {
            try {
                val actions = agent.processMessage(message)
                allActions.addAll(actions)
            } catch (e: Exception) {
                println("Error processing message '${message.name}': ${e.message}")
            }
        }
        
        return allActions
    }
    
    private fun readFrame(source: okio.BufferedSource): ByteArray {
        // Read frame length (4 bytes, big-endian)
        val frameLength = source.readInt()
        if (frameLength > config.maxFrameSize) {
            throw IllegalArgumentException("Frame size $frameLength exceeds maximum ${config.maxFrameSize}")
        }
        
        // Read frame data
        return source.readByteArray(frameLength.toLong())
    }
    
    private fun writeFrame(sink: okio.BufferedSink, frameData: ByteArray) {
        // Write frame length (4 bytes, big-endian)
        sink.writeInt(frameData.size)
        // Write frame data
        sink.write(frameData)
        sink.flush()
    }
}

/**
 * Configuration for SPOE Engine
 */
data class SpoeConfig(
    val maxFrameSize: Int = 16384,
    val timeout: Long = 30000, // 30 seconds
    val enablePipelining: Boolean = true
)

/**
 * Builder for SPOE Engine
 */
class SpoeEngineBuilder {
    private var port: Int = 12345
    private var agent: SpoeAgent? = null
    private var config: SpoeConfig = SpoeConfig()
    
    fun port(port: Int) = apply { this.port = port }
    
    fun agent(agent: SpoeAgent) = apply { this.agent = agent }
    
    fun config(config: SpoeConfig) = apply { this.config = config }
    
    fun maxFrameSize(size: Int) = apply { 
        this.config = this.config.copy(maxFrameSize = size) 
    }
    
    fun timeout(timeout: Long) = apply { 
        this.config = this.config.copy(timeout = timeout) 
    }
    
    fun enablePipelining(enable: Boolean) = apply { 
        this.config = this.config.copy(enablePipelining = enable) 
    }
    
    fun build(): SpoeEngine {
        val agent = this.agent ?: throw IllegalStateException("Agent must be set")
        return SpoeEngine(port, agent, config)
    }
}
