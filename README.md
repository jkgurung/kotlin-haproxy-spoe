# kotlin-haproxy-spoe

A Kotlin library for implementing HAProxy Stream Processing Offload Engine (SPOE) agents.

This project provides an easy-to-use API for creating custom SPOA (Stream Processing Offload Agent) agents in Kotlin, leveraging the power of Kotlin coroutines and the efficiency of the Okio library for I/O operations.

## Features

- ✅ **Complete SPOP Protocol Implementation** - Full support for HAProxy's Stream Processing Offload Protocol
- ✅ **Kotlin Coroutines** - Async/await support for high-performance concurrent processing
- ✅ **Type-Safe API** - Leverages Kotlin's type system to prevent common errors
- ✅ **Easy to Use** - Simple builder pattern and agent interface
- ✅ **Efficient I/O** - Built with Okio for optimal binary protocol handling
- ✅ **HAProxy 3.1 Compatible** - Works with both legacy and modern HAProxy SPOE implementations

## Quick Start

### 1. Add Dependency

```kotlin
dependencies {
    implementation("com.github.jhalak:kotlin-haproxy-spoe:0.1.0")
}
```

### 2. Implement Your Agent

```kotlin
import com.github.jhalak.spoe.*

class IpReputationAgent : SpoeAgent {
    override suspend fun processMessage(message: SpoeMessage): List<SpoeAction> {
        return when (message.name) {
            "check-client-ip" -> {
                val ip = (message.args["src"] as? SpoeValue.String)?.value ?: return emptyList()

                // Your custom logic here
                val score = checkIpReputation(ip)

                listOf(
                    SpoeAction.SetVariable(
                        scope = VariableScope.SESSION,
                        name = "ip_score",
                        value = SpoeValue.Int32(score)
                    )
                )
            }
            else -> emptyList()
        }
    }

    private suspend fun checkIpReputation(ip: String): Int {
        // Your IP reputation logic
        return when {
            ip.startsWith("192.168.") -> 100  // Private, trusted
            ip.startsWith("10.") -> 95        // Private network
            else -> 50                        // Default score
        }
    }
}
```

### 3. Start Your Agent

```kotlin
fun main() {
    val engine = SpoeEngineBuilder()
        .port(12345)
        .agent(IpReputationAgent())
        .build()

    runBlocking {
        engine.start()
    }
}
```

### 4. Configure HAProxy

Add to your HAProxy configuration:

**haproxy.cfg:**

```haproxy
backend iprep-backend
    mode tcp
    timeout connect 5s
    timeout server 10s
    server agent1 127.0.0.1:12345 check

frontend web
    bind :80
    filter spoe engine ip-reputation config /etc/haproxy/spoe.conf
    http-request set-header "X-IP-Score" %[var(sess.iprep.ip_score)]
    default_backend webservers
```

**spoe.conf:**

```haproxy
[ip-reputation]
spoe-agent iprep-agent
    messages check-client-ip
    option var-prefix iprep
    timeout hello 30s
    timeout idle 30s
    timeout processing 15s
    use-backend iprep-backend

spoe-message check-client-ip
    args src=src
    event on-client-session
```

## API Reference

### Core Types

#### `SpoeValue`

Represents typed data that can be exchanged with HAProxy:

- `SpoeValue.Null`
- `SpoeValue.Boolean(value: Boolean)`
- `SpoeValue.Int32(value: Int)`
- `SpoeValue.UInt32(value: UInt)`
- `SpoeValue.Int64(value: Long)`
- `SpoeValue.UInt64(value: ULong)`
- `SpoeValue.IPv4(address: ByteArray)`
- `SpoeValue.IPv6(address: ByteArray)`
- `SpoeValue.String(value: String)`
- `SpoeValue.Binary(data: ByteArray)`

#### `SpoeAction`

Actions your agent can return to HAProxy:

- `SpoeAction.SetVariable(scope, name, value)` - Set a HAProxy variable
- `SpoeAction.UnsetVariable(scope, name)` - Unset a HAProxy variable

#### `VariableScope`

Scopes for HAProxy variables:

- `VariableScope.PROCESS` - Process-wide variable
- `VariableScope.SESSION` - Session-specific variable
- `VariableScope.TRANSACTION` - Transaction-specific variable
- `VariableScope.REQUEST` - Request-specific variable
- `VariableScope.RESPONSE` - Response-specific variable

### Agent Interface

```kotlin
interface SpoeAgent {
    suspend fun processMessage(message: SpoeMessage): List<SpoeAction>
}
```

Your agent receives `SpoeMessage` objects containing:

- `name: String` - Message name from HAProxy configuration
- `args: Map<String, SpoeValue>` - Arguments sent by HAProxy

Return a list of `SpoeAction` objects to tell HAProxy what to do.

### Engine Configuration

```kotlin
class SpoeEngineBuilder {
    fun port(port: Int): SpoeEngineBuilder
    fun agent(agent: SpoeAgent): SpoeEngineBuilder
    fun maxFrameSize(size: Int): SpoeEngineBuilder
    fun timeout(timeout: Long): SpoeEngineBuilder
    fun enablePipelining(enable: Boolean): SpoeEngineBuilder
    fun build(): SpoeEngine
}
```

## Examples

See the [examples directory](src/main/kotlin/com/github/jhalak/spoe/examples/) for complete working examples:

- **IP Reputation Agent** - Basic IP scoring example
- **Authentication Agent** - User authentication example
- **WAF Agent** - Web Application Firewall example

## Architecture

```
┌─────────────────────────────┐
│   User Agent Interface     │  ← Your SpoeAgent implementation
├─────────────────────────────┤
│   Message Processing       │  ← Handle NOTIFY/ACK flow
├─────────────────────────────┤
│   Protocol Layer (SPOP)    │  ← Binary frame serialization
├─────────────────────────────┤
│   Transport Layer (TCP)    │  ← Kotlin coroutines + Okio
└─────────────────────────────┘
```

## Requirements

- **Kotlin** 1.9.21+
- **Java** 11+
- **HAProxy** 1.7+ (with SPOE support)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- HAProxy team for the excellent SPOE feature and documentation
- Square's Okio library for efficient I/O operations
- JetBrains for Kotlin and coroutines

## Links

- [HAProxy SPOE Documentation](https://github.com/haproxy/haproxy/blob/master/doc/SPOE.txt)
- [HAProxy Official Website](https://www.haproxy.org/)
- [SPOE Examples in Other Languages](https://github.com/haproxy/wiki/wiki/SPOE:-Stream-Processing-Offloading-Engine)
