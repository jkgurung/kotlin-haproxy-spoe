# 🧪 TestRunner Guide

The TestRunner provides comprehensive testing capabilities for your SPOE agents with detailed pass/fail reporting and execution summaries.

## 🚀 How to Run TestRunner

### Method 1: Using Gradle (Recommended)

```bash
# Run demo scenarios with detailed test results
./gradlew run --args="demo"

# Start individual agents for HAProxy integration
./gradlew run --args="auth"    # Authorization Agent on port 12345
./gradlew run --args="waf"     # WAF Agent on port 12346
./gradlew run --args="ip"      # IP Reputation Agent on port 12347
```

### Method 2: Direct Java Execution

```bash
# Compile first
./gradlew build

# Run with Java
java -cp build/classes/kotlin/main:build/libs/* com.github.jhalak.spoe.examples.TestRunner demo
java -cp build/classes/kotlin/main:build/libs/* com.github.jhalak.spoe.examples.TestRunner auth
java -cp build/classes/kotlin/main:build/libs/* com.github.jhalak.spoe.examples.TestRunner waf
java -cp build/classes/kotlin/main:build/libs/* com.github.jhalak.spoe.examples.TestRunner ip
```

### Method 3: IDE Execution

1. Open `TestRunner.kt` in your IDE
2. Run the `main` method with program arguments:
   - `demo` - Run all test scenarios
   - `auth` - Start Authorization Agent
   - `waf` - Start WAF Agent  
   - `ip` - Start IP Reputation Agent

## 📊 Demo Mode Output

When you run `./gradlew run --args="demo"`, you'll see:

```
🧪 Running Demo Scenarios...
==================================================

=== Authorization Agent Tests ===
1. Testing valid API key...
  ✅ PASS Valid API Key:
    transaction.api_key_valid = true
    transaction.auth_method = "api_key"
    session.rate_limit = 1000
    transaction.client_info = "192.168.1.100|unknown"

2. Testing JWT token...
  ✅ PASS JWT Token:
    transaction.jwt_valid = true
    transaction.auth_method = "jwt"
    session.user_id = "user123"
    session.user_role = "admin"
    transaction.path_authorized = true
    transaction.client_info = "unknown|unknown"

3. Testing unauthorized access...
  ✅ PASS Unauthorized:
    transaction.auth_required = true
    transaction.client_info = "203.0.113.1|unknown"

=== WAF Agent Tests ===
1. Testing SQL injection detection...
  ✅ PASS SQL Injection:
    transaction.waf_threat_level = 90
    transaction.waf_threats = "sql_injection"
    transaction.waf_block_reason = "SQL injection attempt detected"
    transaction.waf_block = true
    session.client_reputation = 41

2. Testing XSS attack detection...
  ✅ PASS XSS Attack:
    transaction.waf_threat_level = 85
    transaction.waf_threats = "xss_attempt"
    transaction.waf_block_reason = "XSS attempt detected"
    transaction.waf_block = true
    session.client_reputation = 41

3. Testing suspicious user agent...
  ✅ PASS Suspicious User Agent:
    transaction.waf_threat_level = 80
    transaction.waf_threats = "suspicious_user_agent"
    transaction.waf_block_reason = "Suspicious user agent detected"
    transaction.waf_block = true
    session.client_reputation = 42

4. Testing legitimate request...
  ✅ PASS Legitimate Request:
    transaction.waf_threat_level = 0
    transaction.waf_threats = ""
    transaction.waf_block = false
    session.client_reputation = 90

=== IP Reputation Agent Tests ===
1. Testing private IP...
  ✅ PASS Private IP:
    session.ip_score = 100

2. Testing public IP...
  ✅ PASS Public IP:
    session.ip_score = 50

3. Testing known good IP...
  ✅ PASS Known Good IP:
    session.ip_score = 95

==================================================
📊 SUMMARY REPORT
==================================================
Total Tests: 10
✅ Passed: 10
❌ Failed: 0
Success Rate: 100.0%

🎉 All tests passed! Your SPOE agents are working correctly.
```

## 🧪 What Tests Are Run

### Authorization Agent Tests (3 tests)
1. **Valid API Key** - Tests API key authentication with rate limiting
2. **JWT Token** - Tests JWT validation and role-based authorization
3. **Unauthorized Access** - Tests proper rejection of unauthenticated requests

### WAF Agent Tests (4 tests)
1. **SQL Injection** - Tests detection of SQL injection attempts
2. **XSS Attack** - Tests XSS pattern recognition
3. **Suspicious User Agent** - Tests blocking of known attack tools
4. **Legitimate Request** - Tests that normal requests pass through

### IP Reputation Agent Tests (3 tests)
1. **Private IP** - Tests high trust score for internal IPs
2. **Public IP** - Tests medium score for unknown public IPs
3. **Known Good IP** - Tests high score for trusted services (Google DNS)

## ✅ Success/Failure Indicators

- **✅ PASS** - Test passed, expected behavior observed
- **❌ FAIL** - Test failed, unexpected behavior
- **Final Summary** - Shows total/passed/failed counts and success percentage

## 🎯 Expected Results

All tests should **PASS** if your agents are working correctly. If you see failures:

1. **Check the agent logic** - Ensure expected variables are being set
2. **Verify input data** - Confirm test inputs match what agents expect
3. **Review test expectations** - Make sure test validations match agent behavior

## 🔧 Agent Server Mode

When running individual agents (auth/waf/ip), the TestRunner starts SPOE servers that can connect to HAProxy:

```bash
# Start Authorization Agent
./gradlew run --args="auth"
# Output: 🔐 Starting Authorization Agent on port 12345...
#         Test with: curl -H 'X-API-Key: api-key-12345' http://localhost:8080/api/users

# Start WAF Agent  
./gradlew run --args="waf"
# Output: 🛡️ Starting WAF Agent on port 12346...
#         Test with: curl 'http://localhost:8080/search?q='; DROP TABLE users; --'

# Start IP Reputation Agent
./gradlew run --args="ip"
# Output: 🌐 Starting IP Reputation Agent on port 12347...
#         Test with requests from different IP addresses
```

These servers will run indefinitely and can be used with the HAProxy configurations in the `examples/haproxy/` directory.

## 📝 Customizing Tests

To add your own tests, modify the test methods in `TestRunner.kt`:

1. **Add new test scenarios** to existing agent test methods
2. **Create custom expectations** using `validateExpectation()`
3. **Update test counts** and pass/fail logic
4. **Add new agent types** by creating additional test methods

## 🔍 Debugging Failed Tests

If tests fail:

1. **Check the printed variables** - See what values are actually being set
2. **Compare with expectations** - Verify the test expects the right values
3. **Run agents individually** - Test specific scenarios in isolation
4. **Enable debug logging** - Add println statements to agent logic

## 📊 Integration with CI/CD

The TestRunner returns appropriate exit codes:
- **Exit 0** - All tests passed
- **Exit 1** - Some tests failed

Perfect for automated testing in CI/CD pipelines!