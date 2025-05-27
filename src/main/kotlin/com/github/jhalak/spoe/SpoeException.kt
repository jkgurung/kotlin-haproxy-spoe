package com.github.jhalak.spoe

/**
 * Base exception for SPOE-related errors
 */
sealed class SpoeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Protocol-level errors during SPOP frame processing
     */
    class ProtocolException(message: String, cause: Throwable? = null) : SpoeException(message, cause)
    
    /**
     * Configuration errors when setting up SPOE engine
     */
    class ConfigurationException(message: String, cause: Throwable? = null) : SpoeException(message, cause)
    
    /**
     * Connection-related errors
     */
    class ConnectionException(message: String, cause: Throwable? = null) : SpoeException(message, cause)
    
    /**
     * Timeout errors
     */
    class TimeoutException(message: String, cause: Throwable? = null) : SpoeException(message, cause)
}