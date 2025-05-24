package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Example IP reputation SPOE agent
 */
class IpReputationAgent : SpoeAgent {
    
    override suspend fun processMessage(message: SpoeMessage): List<SpoeAction> {
        return when (message.name) {
            "check-client-ip" -> handleIpCheck(message)
            else -> {
                println("Unknown message: ${message.name}")
                emptyList()
            }
        }
    }
    
    private suspend fun handleIpCheck(message: SpoeMessage): List<SpoeAction> {
        // Extract IP address from message arguments
        val ip = when (val srcArg = message.args["src"]) {
            is SpoeValue.String -> srcArg.value
            is SpoeValue.IPv4 -> formatIPv4(srcArg.address)
            is SpoeValue.IPv6 -> formatIPv6(srcArg.address)
            else -> {
                println("No valid IP address found in message")
                return emptyList()
            }
        }
        
        println("Checking IP reputation for: $ip")
        
        // Simulate async IP reputation lookup
        val score = checkIpReputation(ip)
        
        println("IP $ip scored: $score")
        
        return listOf(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "ip_score",
                value = SpoeValue.Int32(score)
            )
        )
    }
    
    private suspend fun checkIpReputation(ip: String): Int {
        // Simulate network lookup delay
        delay(10)
        
        return when {
            ip.startsWith("192.168.") -> 100  // Private network, safe
            ip.startsWith("10.") -> 95        // Private network
            ip.startsWith("172.") -> 90       // Private network
            ip.startsWith("127.") -> 100      // Loopback
            ip == "192.168.50.1" -> 20        // Known bad IP from example
            ip.contains("suspicious") -> 5     // Suspicious domain/IP
            else -> (50..80).random()          // Random score for demo
        }
    }
    
    private fun formatIPv4(bytes: ByteArray): String {
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }
    
    private fun formatIPv6(bytes: ByteArray): String {
        return bytes.asSequence()
            .chunked(2)
            .map { chunk -> 
                ((chunk[0].toInt() and 0xFF) shl 8) or (chunk[1].toInt() and 0xFF)
            }
            .joinToString(":") { "%x".format(it) }
    }
}

/**
 * Example main function to run the IP reputation agent
 */
fun main() {
    val engine = SpoeEngineBuilder()
        .port(12345)
        .agent(IpReputationAgent())
        .maxFrameSize(16384)
        .timeout(30000)
        .build()
    
    println("Starting IP Reputation SPOE Agent on port 12345...")
    println("Connect HAProxy with the following SPOE configuration:")
    println()
    println("[ip-reputation]")
    println("spoe-agent iprep-agent")
    println("    messages check-client-ip")
    println("    option var-prefix iprep")
    println("    timeout hello 30s")
    println("    timeout idle 30s")
    println("    timeout processing 15s")
    println("    use-backend iprep-backend")
    println()
    println("spoe-message check-client-ip")
    println("    args src=src")
    println("    event on-client-session")
    println()
    
    runBlocking {
        try {
            engine.start()
        } catch (e: Exception) {
            println("Engine error: ${e.message}")
        }
    }
}
