package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WafAgentTest {
    
    private val agent = WafAgent()
    
    @Test
    fun `test SQL injection detection`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.1"),
                "http_user_agent" to SpoeValue.String("Mozilla/5.0"),
                "uri" to SpoeValue.String("/search?q='; DROP TABLE users; --"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Should detect SQL injection and block
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertTrue((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        // Should have high threat level
        val threatLevel = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threat_level" 
        } as? SpoeAction.SetVariable
        val level = (threatLevel?.value as? SpoeValue.Int32)?.value ?: 0
        assertTrue(level >= 70)
        
        // Should contain sql_injection threat
        val threats = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threats" 
        } as? SpoeAction.SetVariable
        val threatsStr = (threats?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(threatsStr.contains("sql_injection"))
    }
    
    @Test
    fun `test XSS detection`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("198.51.100.1"),
                "http_user_agent" to SpoeValue.String("Chrome/90.0"),
                "uri" to SpoeValue.String("/comment?text=<script>alert('xss')</script>"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertTrue((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        val threats = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threats" 
        } as? SpoeAction.SetVariable
        val threatsStr = (threats?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(threatsStr.contains("xss_attempt"))
    }
    
    @Test
    fun `test suspicious user agent blocking`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("192.0.2.1"),
                "http_user_agent" to SpoeValue.String("sqlmap/1.0"),
                "uri" to SpoeValue.String("/"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertTrue((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        val blockReason = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block_reason" 
        } as? SpoeAction.SetVariable
        val reason = (blockReason?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(reason.contains("Suspicious user agent"))
    }
    
    @Test
    fun `test legitimate request passes`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("192.168.1.100"),
                "http_user_agent" to SpoeValue.String("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                "uri" to SpoeValue.String("/products?category=electronics"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertFalse((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        val threatLevel = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threat_level" 
        } as? SpoeAction.SetVariable
        val level = (threatLevel?.value as? SpoeValue.Int32)?.value ?: 0
        assertTrue(level < 70)
    }
    
    @Test
    fun `test rate limiting`() = runBlocking {
        val clientIp = "203.0.113.50"
        
        // First request should be fine
        val message1 = SpoeMessage(
            name = "check-rate-limit",
            args = mapOf(
                "src" to SpoeValue.String(clientIp),
                "http_user_agent" to SpoeValue.String("TestClient/1.0"),
                "path" to SpoeValue.String("/login")
            )
        )
        
        val actions1 = agent.processMessage(message1)
        val rateLimitExceeded1 = actions1.find { 
            it is SpoeAction.SetVariable && it.name == "rate_limit_exceeded" 
        } as? SpoeAction.SetVariable
        assertFalse((rateLimitExceeded1?.value as? SpoeValue.Boolean)?.value == true)
        
        // Simulate many requests to exceed login rate limit (10 requests)
        repeat(15) {
            agent.processMessage(message1)
        }
        
        // This request should be rate limited
        val actionsLimited = agent.processMessage(message1)
        val rateLimitExceeded = actionsLimited.find { 
            it is SpoeAction.SetVariable && it.name == "rate_limit_exceeded" 
        } as? SpoeAction.SetVariable
        assertTrue((rateLimitExceeded?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test content security with malicious payload`() = runBlocking {
        val message = SpoeMessage(
            name = "check-content-security",
            args = mapOf(
                "http_content_type" to SpoeValue.String("application/json"),
                "body" to SpoeValue.String("""{"user": "admin", "password": "'; DROP TABLE users; --"}"""),
                "method" to SpoeValue.String("POST")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val contentBlocked = actions.find { 
            it is SpoeAction.SetVariable && it.name == "content_blocked" 
        } as? SpoeAction.SetVariable
        assertTrue((contentBlocked?.value as? SpoeValue.Boolean)?.value == true)
        
        val contentThreats = actions.find { 
            it is SpoeAction.SetVariable && it.name == "content_threats" 
        } as? SpoeAction.SetVariable
        val threatsStr = (contentThreats?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(threatsStr.contains("sql_injection_body"))
    }
    
    @Test
    fun `test oversized request blocking`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("198.51.100.100"),
                "http_user_agent" to SpoeValue.String("TestClient/1.0"),
                "uri" to SpoeValue.String("/upload"),
                "method" to SpoeValue.String("POST"),
                "http_content_length" to SpoeValue.String("20971520") // 20MB
            )
        )
        
        val actions = agent.processMessage(message)
        
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertTrue((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        val threats = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threats" 
        } as? SpoeAction.SetVariable
        val threatsStr = (threats?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(threatsStr.contains("oversized_request"))
    }
    
    @Test
    fun `test directory traversal detection`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.200"),
                "http_user_agent" to SpoeValue.String("TestClient/1.0"),
                "uri" to SpoeValue.String("/files?path=../../../etc/passwd"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val shouldBlock = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_block" 
        } as? SpoeAction.SetVariable
        assertTrue((shouldBlock?.value as? SpoeValue.Boolean)?.value == true)
        
        val threats = actions.find { 
            it is SpoeAction.SetVariable && it.name == "waf_threats" 
        } as? SpoeAction.SetVariable
        val threatsStr = (threats?.value as? SpoeValue.String)?.value ?: ""
        assertTrue(threatsStr.contains("directory_traversal"))
    }
    
    @Test
    fun `test reputation scoring`() = runBlocking {
        val message = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("192.168.1.50"), // Private IP
                "http_user_agent" to SpoeValue.String("Mozilla/5.0 Chrome/90.0"), // Good user agent
                "uri" to SpoeValue.String("/products"),
                "method" to SpoeValue.String("GET"),
                "http_content_length" to SpoeValue.String("0")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val clientReputation = actions.find { 
            it is SpoeAction.SetVariable && it.name == "client_reputation" 
        } as? SpoeAction.SetVariable
        val reputation = (clientReputation?.value as? SpoeValue.Int32)?.value ?: 0
        
        // Private IP + good user agent should have high reputation
        assertTrue(reputation >= 70)
    }
}