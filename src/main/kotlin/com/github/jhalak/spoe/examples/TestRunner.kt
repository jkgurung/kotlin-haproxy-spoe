
package com.github.jhalak.spoe.examples

import com.github.jhalak.spoe.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler

/**
 * Test runner for SPOE agents that can be used for local testing
 * without requiring a full HAProxy setup.
 */
object TestRunner {
    
    data class TestResult(val total: Int, val passed: Int, val failed: Int)
    
    @JvmStatic
    fun main(args: Array<String>) {
        when (args.getOrNull(0)) {
            "auth" -> runAuthorizationAgent()
            "waf" -> runWafAgent()
            "ip" -> runIpReputationAgent()
            "demo" -> runDemoScenarios()
            else -> {
                println("Usage: TestRunner [auth|waf|ip|demo]")
                println("  auth - Run AuthorizationAgent on port 12345")
                println("  waf  - Run WafAgent on port 12346")
                println("  ip   - Run IpReputationAgent on port 12347")
                println("  demo - Run demo scenarios against all agents")
            }
        }
    }
    
    private fun runAuthorizationAgent() {
        println("🔐 Starting Authorization Agent on port 12345...")
        println("Test with: curl -H 'X-API-Key: api-key-12345' http://localhost:8080/api/users")
        
        val engine = SpoeEngineBuilder()
            .port(12345)
            .agent(AuthorizationAgent())
            .build()
        
        runBlocking {
            engine.start()
        }
    }
    
    private fun runWafAgent() {
        println("🛡️ Starting WAF Agent on port 12346...")
        println("Test with: curl 'http://localhost:8080/search?q='; DROP TABLE users; --'")
        
        val engine = SpoeEngineBuilder()
            .port(12346)
            .agent(WafAgent())
            .build()
        
        runBlocking {
            engine.start()
        }
    }
    
    private fun runIpReputationAgent() {
        println("🌐 Starting IP Reputation Agent on port 12347...")
        println("Test with requests from different IP addresses")
        
        val engine = SpoeEngineBuilder()
            .port(12347)
            .agent(IpReputationAgent())
            .build()
        
        runBlocking {
            engine.start()
        }
    }
    
    private fun runDemoScenarios() {
        println("🧪 Running Demo Scenarios...")
        println("=".repeat(50))
        
        var totalTests = 0
        var passedTests = 0
        var failedTests = 0
        
        runBlocking {
            println("\n=== Authorization Agent Tests ===")
            val authResults = testAuthorizationScenarios()
            totalTests += authResults.total
            passedTests += authResults.passed
            failedTests += authResults.failed
            
            println("\n=== WAF Agent Tests ===")
            val wafResults = testWafScenarios()
            totalTests += wafResults.total
            passedTests += wafResults.passed
            failedTests += wafResults.failed
            
            println("\n=== IP Reputation Agent Tests ===")
            val ipResults = testIpReputationScenarios()
            totalTests += ipResults.total
            passedTests += ipResults.passed
            failedTests += ipResults.failed
            
            println("\n" + "=".repeat(50))
            println("📊 SUMMARY REPORT")
            println("=".repeat(50))
            println("Total Tests: $totalTests")
            println("✅ Passed: $passedTests")
            println("❌ Failed: $failedTests")
            println("Success Rate: ${if (totalTests > 0) "%.1f%%".format((passedTests.toDouble() / totalTests) * 100) else "N/A"}")
            
            if (failedTests == 0) {
                println("\n🎉 All tests passed! Your SPOE agents are working correctly.")
            } else {
                println("\n⚠️  Some tests failed. Please review the output above.")
            }
        }
    }
    
    private suspend fun testAuthorizationScenarios(): TestResult {
        val agent = AuthorizationAgent()
        var passed = 0
        var failed = 0
        var total = 0
        
        // Test 1: Valid API Key
        println("1. Testing valid API key...")
        total++
        val validApiKeyMessage = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_x_api_key" to SpoeValue.String("api-key-12345"),
                "src" to SpoeValue.String("192.168.1.100"),
                "path" to SpoeValue.String("/api/users")
            )
        )
        
        val apiActions = agent.processMessage(validApiKeyMessage)
        val test1Pass = validateExpectation(apiActions, "api_key_valid", SpoeValue.Boolean(true))
        if (test1Pass) passed++ else failed++
        printTestResult("Valid API Key", apiActions, test1Pass)
        
        // Test 2: JWT Token
        println("\n2. Testing JWT token...")
        total++
        val jwtMessage = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "http_authorization" to SpoeValue.String("Bearer app1.user123.signature"),
                "path" to SpoeValue.String("/dashboard")
            )
        )
        
        val jwtActions = agent.processMessage(jwtMessage)
        val test2Pass = validateExpectation(jwtActions, "jwt_valid", SpoeValue.Boolean(true))
        if (test2Pass) passed++ else failed++
        printTestResult("JWT Token", jwtActions, test2Pass)
        
        // Test 3: Unauthorized Access
        println("\n3. Testing unauthorized access...")
        total++
        val unauthorizedMessage = SpoeMessage(
            name = "check-authorization",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.1"),
                "path" to SpoeValue.String("/admin/settings")
            )
        )
        
        val unauthorizedActions = agent.processMessage(unauthorizedMessage)
        val test3Pass = validateExpectation(unauthorizedActions, "auth_required", SpoeValue.Boolean(true))
        if (test3Pass) passed++ else failed++
        printTestResult("Unauthorized", unauthorizedActions, test3Pass)
        
        return TestResult(total, passed, failed)
    }
    
    private suspend fun testWafScenarios(): TestResult {
        val agent = WafAgent()
        var passed = 0
        var failed = 0
        var total = 0
        
        // Test 1: SQL Injection
        println("1. Testing SQL injection detection...")
        total++
        val sqlInjectionMessage = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.1"),
                "http_user_agent" to SpoeValue.String("Mozilla/5.0"),
                "uri" to SpoeValue.String("/search?q=' OR 1=1; DROP TABLE users; --"),
                "method" to SpoeValue.String("GET")
            )
        )
        
        val sqlActions = agent.processMessage(sqlInjectionMessage)
        val test1Pass = validateExpectation(sqlActions, "waf_block", SpoeValue.Boolean(true))
        if (test1Pass) passed++ else failed++
        printTestResult("SQL Injection", sqlActions, test1Pass)
        
        // Test 2: XSS Attack
        println("\n2. Testing XSS attack detection...")
        total++
        val xssMessage = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("198.51.100.1"),
                "uri" to SpoeValue.String("/comment?text=<script>alert('xss')</script>"),
                "http_user_agent" to SpoeValue.String("Chrome/90.0")
            )
        )
        
        val xssActions = agent.processMessage(xssMessage)
        val test2Pass = validateExpectation(xssActions, "waf_block", SpoeValue.Boolean(true))
        if (test2Pass) passed++ else failed++
        printTestResult("XSS Attack", xssActions, test2Pass)
        
        // Test 3: Suspicious User Agent
        println("\n3. Testing suspicious user agent...")
        total++
        val suspiciousAgentMessage = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("192.0.2.1"),
                "http_user_agent" to SpoeValue.String("sqlmap/1.0"),
                "uri" to SpoeValue.String("/")
            )
        )
        
        val suspiciousActions = agent.processMessage(suspiciousAgentMessage)
        val test3Pass = validateExpectation(suspiciousActions, "waf_block", SpoeValue.Boolean(true))
        if (test3Pass) passed++ else failed++
        printTestResult("Suspicious User Agent", suspiciousActions, test3Pass)
        
        // Test 4: Legitimate Request
        println("\n4. Testing legitimate request...")
        total++
        val legitimateMessage = SpoeMessage(
            name = "check-request-security",
            args = mapOf(
                "src" to SpoeValue.String("192.168.1.100"),
                "http_user_agent" to SpoeValue.String("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
                "uri" to SpoeValue.String("/products?category=electronics")
            )
        )
        
        val legitimateActions = agent.processMessage(legitimateMessage)
        val test4Pass = validateExpectation(legitimateActions, "waf_block", SpoeValue.Boolean(false))
        if (test4Pass) passed++ else failed++
        printTestResult("Legitimate Request", legitimateActions, test4Pass)
        
        return TestResult(total, passed, failed)
    }
    
    private suspend fun testIpReputationScenarios(): TestResult {
        val agent = IpReputationAgent()
        var passed = 0
        var failed = 0
        var total = 0
        
        // Test 1: Private IP (should get high score)
        println("1. Testing private IP...")
        total++
        val privateIpMessage = SpoeMessage(
            name = "check-client-ip",
            args = mapOf(
                "src" to SpoeValue.String("192.168.1.100")
            )
        )
        
        val privateActions = agent.processMessage(privateIpMessage)
        val test1Pass = privateActions.any { action ->
            action is SpoeAction.SetVariable &&
            action.name == "ip_score" &&
            action.value is SpoeValue.Int32 &&
            (action.value as SpoeValue.Int32).value == 100
        }
        if (test1Pass) passed++ else failed++
        printTestResult("Private IP", privateActions, test1Pass)
        
        // Test 2: Public IP (should get medium score)
        println("\n2. Testing public IP...")
        total++
        val publicIpMessage = SpoeMessage(
            name = "check-client-ip",
            args = mapOf(
                "src" to SpoeValue.String("203.0.113.1")
            )
        )
        
        val publicActions = agent.processMessage(publicIpMessage)
        val test2Pass = publicActions.any { action ->
            action is SpoeAction.SetVariable &&
            action.name == "ip_score" &&
            action.value is SpoeValue.Int32 &&
            (action.value as SpoeValue.Int32).value == 50
        }
        if (test2Pass) passed++ else failed++
        printTestResult("Public IP", publicActions, test2Pass)
        
        // Test 3: Known Good IP (should get high score)
        println("\n3. Testing known good IP...")
        total++
        val goodIpMessage = SpoeMessage(
            name = "check-client-ip",
            args = mapOf(
                "src" to SpoeValue.String("8.8.8.8")
            )
        )
        
        val goodActions = agent.processMessage(goodIpMessage)
        val test3Pass = goodActions.any { action ->
            action is SpoeAction.SetVariable &&
            action.name == "ip_score" &&
            action.value is SpoeValue.Int32 &&
            (action.value as SpoeValue.Int32).value == 95
        }
        if (test3Pass) passed++ else failed++
        printTestResult("Known Good IP", goodActions, test3Pass)
        
        return TestResult(total, passed, failed)
    }
    
    private fun validateExpectation(actions: List<SpoeAction>, variableName: String, expectedValue: SpoeValue): Boolean {
        return actions.any { action ->
            action is SpoeAction.SetVariable &&
            action.name == variableName &&
            action.value == expectedValue
        }
    }
    
    private fun printTestResult(testName: String, actions: List<SpoeAction>, passed: Boolean) {
        val status = if (passed) "✅ PASS" else "❌ FAIL"
        println("  $status $testName:")
        
        if (actions.isEmpty()) {
            println("    No actions returned")
            return
        }
        
        actions.forEach { action ->
            when (action) {
                is SpoeAction.SetVariable -> {
                    val valueStr = when (val value = action.value) {
                        is SpoeValue.Boolean -> value.value.toString()
                        is SpoeValue.Int32 -> value.value.toString()
                        is SpoeValue.String -> "\"${value.value}\""
                        else -> value.toString()
                    }
                    println("    ${action.scope.name.lowercase()}.${action.name} = $valueStr")
                }
                is SpoeAction.UnsetVariable -> {
                    println("    UNSET ${action.scope.name.lowercase()}.${action.name}")
                }
            }
        }
    }
}

/**
 * Simple test client to send mock SPOE messages
 */
class SpoeTestClient {
    
    suspend fun testAgent(agent: SpoeAgent, scenarios: List<TestScenario>) {
        scenarios.forEach { scenario ->
            println("\n--- Testing: ${scenario.name} ---")
            val actions = agent.processMessage(scenario.message)
            
            println("Input:")
            scenario.message.args.forEach { (key, value) ->
                println("  $key = ${formatValue(value)}")
            }
            
            println("Output:")
            if (actions.isEmpty()) {
                println("  No actions")
            } else {
                actions.forEach { action ->
                    when (action) {
                        is SpoeAction.SetVariable -> {
                            println("  SET ${action.scope.name.lowercase()}.${action.name} = ${formatValue(action.value)}")
                        }
                        is SpoeAction.UnsetVariable -> {
                            println("  UNSET ${action.scope.name.lowercase()}.${action.name}")
                        }
                    }
                }
            }
            
            // Validate expectations
            scenario.expectations.forEach { expectation ->
                val found = actions.any { action ->
                    action is SpoeAction.SetVariable &&
                    action.name == expectation.variableName &&
                    action.value == expectation.expectedValue
                }
                
                val status = if (found) "✅ PASS" else "❌ FAIL"
                println("  $status: Expected ${expectation.variableName} = ${formatValue(expectation.expectedValue)}")
            }
        }
    }
    
    private fun formatValue(value: SpoeValue): String {
        return when (value) {
            is SpoeValue.Boolean -> value.value.toString()
            is SpoeValue.Int32 -> value.value.toString()
            is SpoeValue.String -> "\"${value.value}\""
            else -> value.toString()
        }
    }
}

data class TestScenario(
    val name: String,
    val message: SpoeMessage,
    val expectations: List<TestExpectation> = emptyList()
)

data class TestExpectation(
    val variableName: String,
    val expectedValue: SpoeValue
)