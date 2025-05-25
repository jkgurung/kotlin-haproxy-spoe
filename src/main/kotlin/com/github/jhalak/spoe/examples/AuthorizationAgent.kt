package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*

/**
 * Example SPOE agent that performs authentication and authorization checks
 * based on various HTTP headers including Authorization, API keys, and custom tokens.
 *
 * This agent demonstrates:
 * - JWT token validation
 * - API key authentication
 * - Role-based authorization
 * - Rate limiting based on user identity
 * - Setting multiple response variables
 */
class AuthorizationAgent : SpoeAgent {
    
    private val validApiKeys = setOf(
        "api-key-12345",
        "api-key-67890",
        "api-key-abcdef"
    )
    
    private val jwtSecrets = mapOf(
        "app1" to "secret123",
        "app2" to "secret456"
    )
    
    private val userRoles = mapOf(
        "user123" to "admin",
        "user456" to "user",
        "user789" to "readonly"
    )
    
    override suspend fun processMessage(message: SpoeMessage): List<SpoeAction> {
        return when (message.name) {
            "check-authorization" -> handleAuthorization(message)
            "check-api-access" -> handleApiAccess(message)
            "validate-user-session" -> handleUserSession(message)
            else -> emptyList()
        }
    }
    
    private suspend fun handleAuthorization(message: SpoeMessage): List<SpoeAction> {
        val authHeader = (message.args["http_authorization"] as? SpoeValue.String)?.value
        val apiKeyHeader = (message.args["http_x_api_key"] as? SpoeValue.String)?.value
        val userAgent = (message.args["http_user_agent"] as? SpoeValue.String)?.value ?: "unknown"
        val clientIp = (message.args["src"] as? SpoeValue.String)?.value ?: "unknown"
        val requestPath = (message.args["path"] as? SpoeValue.String)?.value ?: "/"
        
        val actions = mutableListOf<SpoeAction>()
        
        // Check API key authentication
        if (apiKeyHeader != null) {
            val isValidApiKey = validApiKeys.contains(apiKeyHeader)
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "api_key_valid",
                    value = SpoeValue.Boolean(isValidApiKey)
                )
            )
            
            if (isValidApiKey) {
                actions.add(
                    SpoeAction.SetVariable(
                        scope = VariableScope.TRANSACTION,
                        name = "auth_method",
                        value = SpoeValue.String("api_key")
                    )
                )
                
                // Set rate limit based on API key
                val rateLimit = when (apiKeyHeader) {
                    "api-key-12345" -> 1000 // Premium key
                    "api-key-67890" -> 500  // Standard key
                    else -> 100             // Basic key
                }
                
                actions.add(
                    SpoeAction.SetVariable(
                        scope = VariableScope.SESSION,
                        name = "rate_limit",
                        value = SpoeValue.Int32(rateLimit)
                    )
                )
            }
        }
        
        // Check JWT token authentication
        else if (authHeader?.startsWith("Bearer ") == true) {
            val token = authHeader.substring(7)
            val authResult = validateJwtToken(token)
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "jwt_valid",
                    value = SpoeValue.Boolean(authResult.isValid)
                )
            )
            
            if (authResult.isValid) {
                actions.add(
                    SpoeAction.SetVariable(
                        scope = VariableScope.TRANSACTION,
                        name = "auth_method",
                        value = SpoeValue.String("jwt")
                    )
                )
                
                authResult.userId?.let { userId ->
                    actions.add(
                        SpoeAction.SetVariable(
                            scope = VariableScope.SESSION,
                            name = "user_id",
                            value = SpoeValue.String(userId)
                        )
                    )
                    
                    // Set user role
                    val role = userRoles[userId] ?: "guest"
                    actions.add(
                        SpoeAction.SetVariable(
                            scope = VariableScope.SESSION,
                            name = "user_role",
                            value = SpoeValue.String(role)
                        )
                    )
                    
                    // Check authorization for specific paths
                    val hasAccess = checkPathAccess(requestPath, role)
                    actions.add(
                        SpoeAction.SetVariable(
                            scope = VariableScope.TRANSACTION,
                            name = "path_authorized",
                            value = SpoeValue.Boolean(hasAccess)
                        )
                    )
                }
            }
        }
        
        // No valid authentication found
        else {
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "auth_required",
                    value = SpoeValue.Boolean(true)
                )
            )
        }
        
        // Add audit information
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "client_info",
                value = SpoeValue.String("$clientIp|$userAgent")
            )
        )
        
        return actions
    }
    
    private suspend fun handleApiAccess(message: SpoeMessage): List<SpoeAction> {
        val apiKeyHeader = (message.args["http_x_api_key"] as? SpoeValue.String)?.value
        val requestMethod = (message.args["method"] as? SpoeValue.String)?.value ?: "GET"
        val requestPath = (message.args["path"] as? SpoeValue.String)?.value ?: "/"
        
        val actions = mutableListOf<SpoeAction>()
        
        if (apiKeyHeader != null && validApiKeys.contains(apiKeyHeader)) {
            // Check API endpoint access based on key type
            val accessLevel = when (apiKeyHeader) {
                "api-key-12345" -> "full"     // Can access all endpoints
                "api-key-67890" -> "limited"  // Limited to read operations
                else -> "basic"               // Basic endpoints only
            }
            
            val hasAccess = checkApiAccess(requestPath, requestMethod, accessLevel)
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "api_access_granted",
                    value = SpoeValue.Boolean(hasAccess)
                )
            )
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "api_access_level",
                    value = SpoeValue.String(accessLevel)
                )
            )
        } else {
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "api_access_granted",
                    value = SpoeValue.Boolean(false)
                )
            )
        }
        
        return actions
    }
    
    private suspend fun handleUserSession(message: SpoeMessage): List<SpoeAction> {
        val sessionId = (message.args["http_x_session_id"] as? SpoeValue.String)?.value
        val csrfToken = (message.args["http_x_csrf_token"] as? SpoeValue.String)?.value
        val requestMethod = (message.args["method"] as? SpoeValue.String)?.value ?: "GET"
        
        val actions = mutableListOf<SpoeAction>()
        
        if (sessionId != null) {
            val sessionValid = validateSession(sessionId)
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.SESSION,
                    name = "session_valid",
                    value = SpoeValue.Boolean(sessionValid)
                )
            )
            
            // For state-changing operations, require CSRF token
            if (requestMethod in setOf("POST", "PUT", "DELETE", "PATCH")) {
                val csrfValid = csrfToken != null && validateCsrfToken(sessionId, csrfToken)
                actions.add(
                    SpoeAction.SetVariable(
                        scope = VariableScope.TRANSACTION,
                        name = "csrf_valid",
                        value = SpoeValue.Boolean(csrfValid)
                    )
                )
            }
        } else {
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.SESSION,
                    name = "session_required",
                    value = SpoeValue.Boolean(true)
                )
            )
        }
        
        return actions
    }
    
    private fun validateJwtToken(token: String): JwtValidationResult {
        // Simplified JWT validation - in real implementation, use proper JWT library
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return JwtValidationResult(false, null)
            }
            
            // In real implementation, verify signature and decode payload
            // For demo purposes, assume token format: app.userid.signature
            val appId = parts[0]
            val userId = parts[1]
            
            if (jwtSecrets.containsKey(appId)) {
                JwtValidationResult(true, userId)
            } else {
                JwtValidationResult(false, null)
            }
        } catch (e: Exception) {
            JwtValidationResult(false, null)
        }
    }
    
    private fun checkPathAccess(path: String, role: String): Boolean {
        return when (role) {
            "admin" -> true // Admin can access everything
            "user" -> !path.startsWith("/admin") // Users can't access admin paths
            "readonly" -> path.startsWith("/api/") && !path.contains("/write") // Readonly can only access read APIs
            else -> path == "/" || path.startsWith("/public") // Guests only get public access
        }
    }
    
    private fun checkApiAccess(path: String, method: String, accessLevel: String): Boolean {
        return when (accessLevel) {
            "full" -> true
            "limited" -> method in setOf("GET", "HEAD", "OPTIONS")
            "basic" -> path.startsWith("/api/v1/public") && method in setOf("GET", "HEAD")
            else -> false
        }
    }
    
    private fun validateSession(sessionId: String): Boolean {
        // Simplified session validation
        // In real implementation, check against session store (Redis, database, etc.)
        return sessionId.length >= 32 && sessionId.all { it.isLetterOrDigit() }
    }
    
    private fun validateCsrfToken(sessionId: String, csrfToken: String): Boolean {
        // Simplified CSRF validation
        // In real implementation, verify token matches session
        return csrfToken.length >= 16 && csrfToken != sessionId
    }
    
    private data class JwtValidationResult(
        val isValid: Boolean,
        val userId: String?
    )
}