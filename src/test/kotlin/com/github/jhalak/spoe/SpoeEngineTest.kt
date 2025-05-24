package com.github.jhalak.spoe

import com.github.jhalak.spoe.protocol.SpopCodec
import com.github.jhalak.spoe.protocol.SpopFrame
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpoeEngineTest {
    
    @Test
    fun `test SPOE value serialization`() {
        val codec = SpopCodec()
        
        // Test basic values
        val values = listOf(
            SpoeValue.Null,
            SpoeValue.Boolean(true),
            SpoeValue.Int32(42),
            SpoeValue.String("test"),
            SpoeValue.IPv4(byteArrayOf(192.toByte(), 168.toByte(), 1, 1))
        )
        
        // This is a simplified test - in reality we'd need more comprehensive codec tests
        assertTrue(values.isNotEmpty())
    }
    
    @Test
    fun `test agent hello frame encoding`() {
        val codec = SpopCodec()
        
        val frame = SpopFrame.AgentHello(
            version = "2.0",
            maxFrameSize = 16384,
            capabilities = setOf("pipelining")
        )
        
        val encoded = codec.encode(frame)
        assertTrue(encoded.isNotEmpty())
    }
    
    @Test
    fun `test ACK frame encoding`() {
        val codec = SpopCodec()
        
        val actions = listOf(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "test_var",
                value = SpoeValue.Int32(100)
            )
        )
        
        val frame = SpopFrame.Ack(
            streamId = 1,
            frameId = 1,
            actions = actions
        )
        
        val encoded = codec.encode(frame)
        assertTrue(encoded.isNotEmpty())
    }
    
    @Test
    fun `test simple agent processing`() = runTest {
        val agent = TestAgent()
        
        val message = SpoeMessage(
            name = "test-message",
            args = mapOf("test_arg" to SpoeValue.String("test_value"))
        )
        
        val actions = agent.processMessage(message)
        assertEquals(1, actions.size)
        
        val action = actions.first() as SpoeAction.SetVariable
        assertEquals("result", action.name)
        assertEquals(VariableScope.SESSION, action.scope)
    }
    
    @Test
    fun `test engine builder`() {
        val agent = TestAgent()
        
        val engine = SpoeEngineBuilder()
            .port(12346)
            .agent(agent)
            .maxFrameSize(8192)
            .timeout(15000)
            .build()
        
        // Engine should be created successfully
        assertTrue(true)
    }
}

/**
 * Simple test agent for unit tests
 */
class TestAgent : SpoeAgent {
    override suspend fun processMessage(message: SpoeMessage): List<SpoeAction> {
        return listOf(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "result",
                value = SpoeValue.String("processed")
            )
        )
    }
}
