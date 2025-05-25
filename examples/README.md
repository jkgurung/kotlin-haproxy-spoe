# Testing the SPOE Agents

This directory contains everything you need to test the Kotlin HAProxy SPOE agents.

## Quick Start

### 1. Run Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific agent tests
./gradlew test --tests "*AuthorizationAgentTest*"
./gradlew test --tests "*WafAgentTest*"
```

### 2. Run Demo Scenarios

```bash
# Build the project
./gradlew build

# Run demo scenarios (no HAProxy needed)
./gradlew run --args="demo"
```

### 3. Test Individual Agents

```bash
# Start individual agents
./gradlew run --args="auth"    # Port 12345
./gradlew run --args="waf"     # Port 12346  
./gradlew run --args="ip"      # Port 12347
```

## Full HAProxy Integration Testing

### Prerequisites

- HAProxy 2.0+ with SPOE support
- Your agents running on ports 12345, 12346, 12347

### Setup

1. **Start the agents:**
   ```bash
   # Terminal 1 - Authorization Agent
   ./gradlew run --args="auth"
   
   # Terminal 2 - WAF Agent  
   ./gradlew run --args="waf"
   
   # Terminal 3 - IP Reputation Agent
   ./gradlew run --args="ip"
   ```

2. **Start HAProxy with the provided configuration:**
   ```bash
   # Copy config files
   sudo cp examples/haproxy/*.cfg /etc/haproxy/
   sudo cp examples/haproxy/*.conf /etc/haproxy/
   
   # Start HAProxy
   sudo haproxy -f examples/haproxy/haproxy.cfg
   ```

3. **Start a test web server (optional):**
   ```bash
   # Simple HTTP server for testing
   python3 -m http.server 8080
   ```

### Running Tests

#### Authorization Tests
```bash
chmod +x examples/test-scripts/test-auth.sh
./examples/test-scripts/test-auth.sh
```

**Test scenarios:**
- ✅ Valid API key → Should allow access
- ❌ Invalid API key → Should block (401) 
- ✅ Valid JWT token → Should allow with user context
- ❌ Admin access without auth → Should block (401)
- ❌ User accessing admin paths → Should block (403)
- ✅ Session with CSRF token → Should allow POST requests
- ❌ Limited API key for POST → Should block (403)

#### WAF Tests
```bash
chmod +x examples/test-scripts/test-waf.sh
./examples/test-scripts/test-waf.sh
```

**Test scenarios:**
- ✅ Legitimate request → Should allow (200)
- ❌ SQL injection → Should block with WAF
- ❌ XSS attack → Should block with WAF
- ❌ Suspicious user agent → Should block with WAF
- ❌ Directory traversal → Should block with WAF
- ❌ Vulnerability scanning → Should block with WAF
- ❌ Malicious JSON → Should block with WAF
- ❌ Oversized URI → Should block with WAF
- ❌ Rate limit exceeded → Should block (429)
- ❌ XSS in request body → Should block with WAF

#### Manual Testing Examples

```bash
# Test IP reputation
curl -H "X-Forwarded-For: 192.168.1.100" http://localhost/

# Test authorization with API key
curl -H "X-API-Key: api-key-12345" http://localhost/api/users

# Test JWT authentication  
curl -H "Authorization: Bearer app1.user123.signature" http://localhost/dashboard

# Test SQL injection (should be blocked)
curl "http://localhost/search?q='; DROP TABLE users; --"

# Test XSS (should be blocked)
curl "http://localhost/comment?text=<script>alert('xss')</script>"

# Test rate limiting
for i in {1..20}; do curl http://localhost/login; done
```

## Understanding the Results

### HAProxy Variables Set by Agents

#### Authorization Agent Variables:
- `sess.auth.user_id` - Authenticated user ID
- `sess.auth.user_role` - User role (admin, user, readonly, guest)
- `sess.auth.rate_limit` - Rate limit for this user/key
- `txn.auth.api_key_valid` - Boolean: API key valid
- `txn.auth.jwt_valid` - Boolean: JWT token valid  
- `txn.auth.auth_method` - Authentication method used
- `txn.auth.path_authorized` - Boolean: User authorized for path
- `txn.auth.auth_required` - Boolean: Authentication required

#### WAF Agent Variables:
- `txn.waf.waf_block` - Boolean: Request should be blocked
- `txn.waf.waf_threat_level` - Numeric threat level (0-100)
- `txn.waf.waf_threats` - Comma-separated list of threats detected
- `txn.waf.waf_block_reason` - Human-readable block reason
- `sess.waf.rate_limit_exceeded` - Boolean: Rate limit exceeded
- `sess.waf.client_reputation` - Client reputation score (0-100)
- `txn.waf.content_blocked` - Boolean: Request content blocked

#### IP Reputation Agent Variables:
- `sess.iprep.ip_score` - IP reputation score (0-100)

### Response Headers

HAProxy adds these headers based on SPOE results:
- `X-IP-Score` - IP reputation score
- `X-Threat-Level` - WAF threat level
- `X-User-Role` - Authenticated user role

### HTTP Status Codes

- **200** - Request allowed
- **401** - Authentication required
- **403** - Forbidden (authorization failed)
- **429** - Rate limit exceeded  
- **456** - WAF blocked (custom HAProxy status)

## Troubleshooting

### Common Issues

1. **Agents not connecting:**
   - Check that agents are running on correct ports
   - Verify HAProxy backend configuration
   - Check firewall settings

2. **HAProxy errors:**
   - Check HAProxy logs: `journalctl -u haproxy -f`
   - Verify SPOE configuration syntax
   - Ensure all config files are in correct locations

3. **Tests failing:**
   - Verify agents are responding: `telnet localhost 12345`
   - Check agent logs for errors
   - Ensure test scripts have execute permissions

### Debug Mode

Enable debug logging in your agents:

```kotlin
val engine = SpoeEngineBuilder()
    .port(12345)
    .agent(AuthorizationAgent())
    .enableDebugLogging(true)  // Add this line
    .build()
```

### Monitoring

- HAProxy stats: http://localhost:8404/stats
- Agent health checks in HAProxy backend status
- Custom metrics in your agent implementations

## Next Steps

1. **Customize the agents** for your specific use cases
2. **Add persistence** (Redis, database) for rate limiting and session storage
3. **Implement real JWT validation** with proper libraries
4. **Add metrics and monitoring** (Prometheus, StatsD)
5. **Create production deployment** configurations
6. **Add more sophisticated security rules** based on your needs

## Production Considerations

- Use external storage (Redis) for rate limiting and session data
- Implement proper JWT validation with signature verification
- Add comprehensive logging and monitoring
- Use real IP geolocation databases
- Implement circuit breakers and fallback behavior
- Configure appropriate timeouts and connection limits
- Set up health checks and auto-scaling