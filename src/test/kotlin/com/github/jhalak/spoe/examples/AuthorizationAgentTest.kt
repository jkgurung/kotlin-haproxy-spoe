package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizationAgentTest {
    
    private val agent = AuthorizationAgent()
    
    @Test
    fun `test valid API key authentication`() = runBlocking {
        val message = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_x_api_key" to SpoeValue.String("api-key-12345"),
                "src" to SpoeValue.String("192.168.1.100"),
                "http_user_agent" to SpoeValue.String("TestClient/1.0"),
                "path" to SpoeValue.String("/api/users")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Verify API key validation
        val apiKeyValid = actions.find { 
            it is SpoeAction.SetVariable && it.name == "api_key_valid" 
        } as? SpoeAction.SetVariable
        assertTrue((apiKeyValid?.value as? SpoeValue.Boolean)?.value == true)
        
        // Verify auth method is set
        val authMethod = actions.find { 
            it is SpoeAction.SetVariable && it.name == "auth_method" 
        } as? SpoeAction.SetVariable
        assertEquals("api_key", (authMethod?.value as? SpoeValue.String)?.value)
        
        // Verify rate limit is set (premium key should get 1000)
        val rateLimit = actions.find { 
            it is SpoeAction.SetVariable && it.name == "rate_limit" 
        } as? SpoeAction.SetVariable
        assertEquals(1000, (rateLimit?.value as? SpoeValue.Int32)?.value)
    }
    
    @Test
    fun `test invalid API key`() = runBlocking {
        val message = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_x_api_key" to SpoeValue.String("invalid-key"),
                "src" to SpoeValue.String("192.168.1.100"),
                "http_user_agent" to SpoeValue.String("TestClient/1.0"),
                "path" to SpoeValue.String("/api/users")
            )
        )
        
        val actions = agent.processMessage(message)
        
        val apiKeyValid = actions.find { 
            it is SpoeAction.SetVariable && it.name == "api_key_valid" 
        } as? SpoeAction.SetVariable
        assertFalse((apiKeyValid?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test JWT token authentication`() = runBlocking {
        val message = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_authorization" to SpoeValue.String("Bearer app1.user123.signature"),
                "src" to SpoeValue.String("10.0.0.50"),
                "http_user_agent" to SpoeValue.String("WebApp/2.0"),
                "path" to SpoeValue.String("/dashboard")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Verify JWT validation
        val jwtValid = actions.find { 
            it is SpoeAction.SetVariable && it.name == "jwt_valid" 
        } as? SpoeAction.SetVariable
        assertTrue((jwtValid?.value as? SpoeValue.Boolean)?.value == true)
        
        // Verify user ID is set
        val userId = actions.find { 
            it is SpoeAction.SetVariable && it.name == "user_id" 
        } as? SpoeAction.SetVariable
        assertEquals("user123", (userId?.value as? SpoeValue.String)?.value)
        
        // Verify user role is set
        val userRole = actions.find { 
            it is SpoeAction.SetVariable && it.name == "user_role" 
        } as? SpoeAction.SetVariable
        assertEquals("admin", (userRole?.value as? SpoeValue.String)?.value)
        
        // Verify path authorization (admin can access everything)
        val pathAuthorized = actions.find { 
            it is SpoeAction.SetVariable && it.name == "path_authorized" 
        } as? SpoeAction.SetVariable
        assertTrue((pathAuthorized?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test role-based path access control`() = runBlocking {
        // Test user trying to access admin path
        val message = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_authorization" to SpoeValue.String("Bearer app1.user456.signature"),
                "path" to SpoeValue.String("/admin/settings")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // user456 has "user" role and should not access admin paths
        val pathAuthorized = actions.find { 
            it is SpoeAction.SetVariable && it.name == "path_authorized" 
        } as? SpoeAction.SetVariable
        assertFalse((pathAuthorized?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test API access control`() = runBlocking {
        val message = SpoeMessage(
            name = "check-api-access",
            args = mapOf(
                "http_x_api_key" to SpoeValue.String("api-key-67890"), // Limited access key
                "method" to SpoeValue.String("POST"),
                "path" to SpoeValue.String("/api/v1/users")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Limited key should not allow POST operations
        val apiAccessGranted = actions.find { 
            it is SpoeAction.SetVariable && it.name == "api_access_granted" 
        } as? SpoeAction.SetVariable
        assertFalse((apiAccessGranted?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test session validation with CSRF`() = runBlocking {
        val message = SpoeMessage(
            name = "validate-user-session",
            args = mapOf(
                "http_x_session_id" to SpoeValue.String("abcdef1234567890abcdef1234567890"),
                "http_x_csrf_token" to SpoeValue.String("csrf_token_12345"),
                "method" to SpoeValue.String("POST")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Verify session is valid
        val sessionValid = actions.find { 
            it is SpoeAction.SetVariable && it.name == "session_valid" 
        } as? SpoeAction.SetVariable
        assertTrue((sessionValid?.value as? SpoeValue.Boolean)?.value == true)
        
        // Verify CSRF token is valid for POST request
        val csrfValid = actions.find { 
            it is SpoeAction.SetVariable && it.name == "csrf_valid" 
        } as? SpoeAction.SetVariable
        assertTrue((csrfValid?.value as? SpoeValue.Boolean)?.value == true)
    }
    
    @Test
    fun `test no authentication required`() = runBlocking {
        val message = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.1"),
                "http_user_agent" to SpoeValue.String("Mozilla/5.0"),
                "path" to SpoeValue.String("/public/info")
            )
        )
        
        val actions = agent.processMessage(message)
        
        // Should require authentication
        val authRequired = actions.find { 
            it is SpoeAction.SetVariable && it.name == "auth_required" 
        } as? SpoeAction.SetVariable
        assertTrue((authRequired?.value as? SpoeValue.Boolean)?.value == true)
    }
}