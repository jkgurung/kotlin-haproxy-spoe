# kotlin-haproxy-spoe

A simple Kotlin-based library for implementing HAProxy Stream Processing Offload Engine (SPOE) agents.

This project aims to provide an easy-to-use API for creating custom SPOA agents in Kotlin, leveraging the power of the Kotlin language and the efficiency of the Okio library for I/O operations.

## Features

- Basic SPOE protocol implementation
- Easy-to-use API for implementing custom SPOA agents
- Built with Kotlin and Okio for efficient I/O

## Getting Started

### Prerequisites

- Kotlin 1.5 or later
- Okio library (add it to your project's dependencies)

### Usage

1. Import the `kotlin-haproxy-spoe` library into your project.
2. Create a custom SPOA agent by implementing the `Agent` interface.

```kotlin
class CustomAgent : Agent {
    override fun processMessage(message: Message): List<Action> {
        // Implement your custom message processing logic here
        // and return a list of actions
    }
}
