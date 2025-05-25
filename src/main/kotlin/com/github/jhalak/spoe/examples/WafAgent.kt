package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*

/**
 * Example SPOE agent that implements Web Application Firewall (WAF) functionality
 * to detect and block malicious requests.
 *
 * This agent demonstrates:
 * - SQL injection detection
 * - XSS attack prevention
 * - Suspicious user agent filtering
 * - Rate limiting and DDoS protection
 * - Request size validation
 * - Geographic IP blocking
 */

class WafAgent : SpoeAgent {
    
    private val sqlInjectionPatterns = listOf(
        Regex("""(?i)(\bunion\b.*\bselect\b)"""),
        Regex("""(?i)(\bselect\b.*\bfrom\b)"""),
        Regex("""(?i)(\binsert\b.*\binto\b)"""),
        Regex("""(?i)(\bdelete\b.*\bfrom\b)"""),
        Regex("""(?i)(\bdrop\b.*\btable\b)"""),
        Regex("""(?i)(\bupdate\b.*\bset\b)"""),
        Regex("""['"].*[;].*['"]"""),
        Regex("""--.*"""),
        Regex("""/\*.*\*/""")
    )
    
    private val xssPatterns = listOf(
        Regex("""(?i)<script[^>]*>.*?</script>"""),
        Regex("""(?i)<iframe[^>]*>.*?</iframe>"""),
        Regex("""(?i)javascript:"""),
        Regex("""(?i)on\w+\s*="""),
        Regex("""(?i)<img[^>]+src[^>]*javascript:"""),
        Regex("""(?i)<object[^>]*>.*?</object>"""),
        Regex("""(?i)<embed[^>]*>"""),
        Regex("""(?i)vbscript:""")
    )
    
    private val suspiciousUserAgents = setOf(
        "sqlmap",
        "nikto",
        "nmap",
        "nessus",
        "burp",
        "zap",
        "w3af",
        "paros",
        "havij",
        "acunetix"
    )
    
    private val blockedCountries = setOf(
        "CN", "RU", "KP", "IR"  // Example blocked country codes
    )
    
    // Simple in-memory rate limiting (in production, use Redis or similar)
    private val requestCounts = mutableMapOf<String, RateLimitInfo>()
    
    override suspend fun processMessage(message: SpoeMessage): List<SpoeAction> {
        return when (message.name) {
            "check-request-security" -> handleSecurityCheck(message)
            "check-rate-limit" -> handleRateLimit(message)
            "check-content-security" -> handleContentSecurity(message)
            else -> emptyList()
        }
    }
    
    private suspend fun handleSecurityCheck(message: SpoeMessage): List<SpoeAction> {
        val clientIp = (message.args["src"] as? SpoeValue.String)?.value ?: ""
        val userAgent = (message.args["http_user_agent"] as? SpoeValue.String)?.value ?: ""
        val requestUri = (message.args["uri"] as? SpoeValue.String)?.value ?: ""
        val contentLength = (message.args["http_content_length"] as? SpoeValue.String)?.value?.toIntOrNull() ?: 0
        
        val actions = mutableListOf<SpoeAction>()
        var threatLevel = 0
        val threats = mutableListOf<String>()
        
        // Check for suspicious user agents
        if (suspiciousUserAgents.any { userAgent.contains(it, ignoreCase = true) }) {
            threatLevel += 80
            threats.add("suspicious_user_agent")
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "waf_block_reason",
                    value = SpoeValue.String("Suspicious user agent detected")
                )
            )
        }
        
        // Check for malformed requests
        if (requestUri.length > 2048) {
            threatLevel += 60
            threats.add("oversized_uri")
        }
        
        // Check request size
        if (contentLength > 10 * 1024 * 1024) { // 10MB limit
            threatLevel += 75
            threats.add("oversized_request")
        }
        
        // Check for SQL injection in URI
        if (sqlInjectionPatterns.any { it.containsMatchIn(requestUri) }) {
            threatLevel += 90
            threats.add("sql_injection")
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "waf_block_reason",
                    value = SpoeValue.String("SQL injection attempt detected")
                )
            )
        }
        
        // Check for XSS in URI
        if (xssPatterns.any { it.containsMatchIn(requestUri) }) {
            threatLevel += 85
            threats.add("xss_attempt")
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "waf_block_reason",
                    value = SpoeValue.String("XSS attempt detected")
                )
            )
        }
        
        // Geographic blocking
        val countryCode = geolocateIp(clientIp)
        if (countryCode in blockedCountries) {
            threatLevel += 70
            threats.add("blocked_country")
            
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "waf_block_reason",
                    value = SpoeValue.String("Request from blocked country: $countryCode")
                )
            )
        }
        
        // Check for directory traversal
        if (requestUri.contains("../") || requestUri.contains("..\\")) {
            threatLevel += 75
            threats.add("directory_traversal")
        }
        
        // Check for known vulnerability paths
        val vulnPaths = listOf(
            "/wp-admin/",
            "/phpmyadmin/",
            "/.env",
            "/config.php",
            "/admin.php",
            "/login.php"
        )
        
        if (vulnPaths.any { requestUri.contains(it, ignoreCase = true) }) {
            threatLevel += 40
            threats.add("vuln_path_scan")
        }
        
        // Set threat level and decision
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "waf_threat_level",
                value = SpoeValue.Int32(threatLevel)
            )
        )
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "waf_threats",
                value = SpoeValue.String(threats.joinToString(","))
            )
        )
        
        // Block if threat level is high
        val shouldBlock = threatLevel >= 70
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "waf_block",
                value = SpoeValue.Boolean(shouldBlock)
            )
        )
        
        // Add client reputation score
        val reputationScore = calculateReputationScore(clientIp, userAgent, threatLevel)
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "client_reputation",
                value = SpoeValue.Int32(reputationScore)
            )
        )
        
        return actions
    }
    
    private suspend fun handleRateLimit(message: SpoeMessage): List<SpoeAction> {
        val clientIp = (message.args["src"] as? SpoeValue.String)?.value ?: return emptyList()
        val requestPath = (message.args["path"] as? SpoeValue.String)?.value ?: "/"
        
        val actions = mutableListOf<SpoeAction>()
        val key = clientIp
        val now = System.currentTimeMillis()
        
        val rateLimitInfo = requestCounts.getOrPut(key) {
            RateLimitInfo(count = 0, windowStart = now, isBlocked = false)
        }
        
        // Reset window if it's been more than 1 minute
        if (now - rateLimitInfo.windowStart > 60_000) {
            rateLimitInfo.count = 0
            rateLimitInfo.windowStart = now
            rateLimitInfo.isBlocked = false
        }
        
        rateLimitInfo.count++
        
        // Different limits based on path
        val limit = when {
            requestPath.startsWith("/api/") -> 100  // API endpoints
            requestPath.startsWith("/admin/") -> 20 // Admin pages
            requestPath.startsWith("/login") -> 10  // Login attempts
            else -> 200 // General pages
        }
        
        val isExceeded = rateLimitInfo.count > limit
        if (isExceeded) {
            rateLimitInfo.isBlocked = true
        }
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "rate_limit_exceeded",
                value = SpoeValue.Boolean(isExceeded)
            )
        )
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "request_count",
                value = SpoeValue.Int32(rateLimitInfo.count)
            )
        )
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.SESSION,
                name = "rate_limit",
                value = SpoeValue.Int32(limit)
            )
        )
        
        // Check for potential DDoS
        if (rateLimitInfo.count > limit * 5) {
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.SESSION,
                    name = "ddos_suspected",
                    value = SpoeValue.Boolean(true)
                )
            )
        }
        
        return actions
    }
    
    private suspend fun handleContentSecurity(message: SpoeMessage): List<SpoeAction> {
        val contentType = (message.args["http_content_type"] as? SpoeValue.String)?.value ?: ""
        val requestBody = (message.args["body"] as? SpoeValue.String)?.value ?: ""
        val method = (message.args["method"] as? SpoeValue.String)?.value ?: "GET"
        
        val actions = mutableListOf<SpoeAction>()
        var contentThreatLevel = 0
        val contentThreats = mutableListOf<String>()
        
        // Only check body content for POST/PUT requests
        if (method in setOf("POST", "PUT", "PATCH") && requestBody.isNotEmpty()) {
            
            // Check for SQL injection in body
            if (sqlInjectionPatterns.any { it.containsMatchIn(requestBody) }) {
                contentThreatLevel += 90
                contentThreats.add("sql_injection_body")
            }
            
            // Check for XSS in body
            if (xssPatterns.any { it.containsMatchIn(requestBody) }) {
                contentThreatLevel += 85
                contentThreats.add("xss_body")
            }
            
            // Check for malicious file uploads
            if (contentType.contains("multipart/form-data", ignoreCase = true)) {
                if (requestBody.contains("<?php", ignoreCase = true) ||
                    requestBody.contains("<script", ignoreCase = true) ||
                    requestBody.contains("eval(", ignoreCase = true)) {
                    contentThreatLevel += 95
                    contentThreats.add("malicious_upload")
                }
            }
            
            // Check for suspicious patterns in JSON/XML
            if (contentType.contains("application/json", ignoreCase = true) ||
                contentType.contains("application/xml", ignoreCase = true)) {
                
                val suspiciousJsonPatterns = listOf(
                    "__proto__",
                    "constructor",
                    "prototype",
                    "eval(",
                    "Function(",
                    "require("
                )
                
                if (suspiciousJsonPatterns.any { requestBody.contains(it, ignoreCase = true) }) {
                    contentThreatLevel += 60
                    contentThreats.add("suspicious_payload")
                }
            }
        }
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "content_threat_level",
                value = SpoeValue.Int32(contentThreatLevel)
            )
        )
        
        if (contentThreats.isNotEmpty()) {
            actions.add(
                SpoeAction.SetVariable(
                    scope = VariableScope.TRANSACTION,
                    name = "content_threats",
                    value = SpoeValue.String(contentThreats.joinToString(","))
                )
            )
        }
        
        actions.add(
            SpoeAction.SetVariable(
                scope = VariableScope.TRANSACTION,
                name = "content_blocked",
                value = SpoeValue.Boolean(contentThreatLevel >= 70)
            )
        )
        
        return actions
    }
    
    private fun geolocateIp(ip: String): String {
        // Simplified geolocation - in real implementation, use GeoIP database
        return when {
            ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.") -> "US" // Local IPs
            ip.startsWith("8.8.") -> "US" // Google DNS
            ip.startsWith("1.1.") -> "US" // Cloudflare DNS
            else -> listOf("US", "GB", "DE", "FR", "JP", "AU", "CA").random() // Random for demo
        }
    }
    
    private fun calculateReputationScore(ip: String, userAgent: String, threatLevel: Int): Int {
        var score = 50 // Neutral score
        
        // Reduce score based on threat level
        score -= (threatLevel / 10)
        
        // Check for known good patterns
        if (userAgent.contains("Mozilla") && userAgent.contains("Chrome")) {
            score += 10
        }
        
        // Check for automation indicators
        if (userAgent.contains("bot", ignoreCase = true) ||
            userAgent.contains("crawler", ignoreCase = true) ||
            userAgent.contains("spider", ignoreCase = true)) {
            score -= 20
        }
        
        // Private IP addresses get higher trust
        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
            score += 30
        }
        
        return score.coerceIn(0, 100)
    }
    
    private data class RateLimitInfo(
        var count: Int,
        var windowStart: Long,
        var isBlocked: Boolean
    )
}