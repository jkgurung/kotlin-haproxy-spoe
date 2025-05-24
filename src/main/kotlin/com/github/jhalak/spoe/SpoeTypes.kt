package com.github.jhalak.spoe

/**
 * SPOE Value types that match HAProxy's typed data system
 */
sealed class SpoeValue {
    data object Null : SpoeValue()
    data class Boolean(val value: kotlin.Boolean) : SpoeValue()
    data class Int32(val value: Int) : SpoeValue()
    data class UInt32(val value: UInt) : SpoeValue()
    data class Int64(val value: Long) : SpoeValue()
    data class UInt64(val value: ULong) : SpoeValue()
    data class IPv4(val address: ByteArray) : SpoeValue() {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as IPv4
            return address.contentEquals(other.address)
        }
        
        override fun hashCode(): Int = address.contentHashCode()
    }
    data class IPv6(val address: ByteArray) : SpoeValue() {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as IPv6
            return address.contentEquals(other.address)
        }
        
        override fun hashCode(): Int = address.contentHashCode()
    }
    data class String(val value: kotlin.String) : SpoeValue()
    data class Binary(val data: ByteArray) : SpoeValue() {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Binary
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int = data.contentHashCode()
    }
}

/**
 * SPOE Message containing name and arguments
 */
data class SpoeMessage(
    val name: String,
    val args: Map<String, SpoeValue>
)

/**
 * Variable scope for SPOE actions
 */
enum class VariableScope(val value: UByte) {
    PROCESS(0u),
    SESSION(1u),
    TRANSACTION(2u),
    REQUEST(3u),
    RESPONSE(4u)
}

/**
 * Actions that SPOE agents can return to HAProxy
 */
sealed class SpoeAction {
    data class SetVariable(
        val scope: VariableScope,
        val name: String,
        val value: SpoeValue
    ) : SpoeAction()
    
    data class UnsetVariable(
        val scope: VariableScope,
        val name: String
    ) : SpoeAction()
}

/**
 * SPOE Agent interface that users implement
 */
interface SpoeAgent {
    /**
     * Process a SPOE message and return a list of actions for HAProxy to execute
     */
    suspend fun processMessage(message: SpoeMessage): List<SpoeAction>
}
