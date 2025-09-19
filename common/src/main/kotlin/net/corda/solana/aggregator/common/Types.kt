@file:Suppress("MagicNumber")

package net.corda.solana.aggregator.common

import java.math.BigInteger
import java.nio.ByteBuffer

class U128(val bigint: BigInteger) : BorshSerialisable {
    companion object {
        const val BORSH_SIZE = 16

        @JvmStatic
        fun borshRead(buffer: ByteBuffer): U128 {
            val bytes = ByteArray(BORSH_SIZE)
            buffer.get(bytes)
            bytes.reverse()
            return U128(BigInteger(1, bytes))
        }
    }

    init {
        require(bigint.signum() != -1)
        require(bigint.bitLength() <= 128)
    }

    override val borshSize: Int
        get() = BORSH_SIZE

    override fun borshWrite(buffer: ByteBuffer) {
        val bytes = bigint.toByteArray()
        val size: Int
        val limit: Int
        // toByteArray includes a leading sign byte (which is zero), which for max u128 means an extra byte is produced.
        if (bytes.size > BORSH_SIZE) {
            size = BORSH_SIZE
            limit = bytes.size - BORSH_SIZE
        } else {
            size = bytes.size
            limit = 0
        }
        for (i in bytes.size - 1 downTo limit) {
            buffer.put(bytes[i])
        }
        repeat(BORSH_SIZE - size) {
            buffer.put(0.toByte())
        }
    }

    override fun toString(): String = bigint.toString()
}

/**
 * Extended by classes which represent Rust ["newtype" structs](https://rust-unofficial.github.io/patterns/patterns/behavioural/newtype.html).
 * over a fixed size byte array.
 */
abstract class FixedBytesNewtypeStruct(val bytes: ByteArray) : BorshSerialisable {
    init {
        require(bytes.size == borshSize) { "${javaClass.name} must be $borshSize bytes" }
    }

    final override fun borshWrite(buffer: ByteBuffer) {
        buffer.put(bytes)
    }

    override fun toString(): String = "${javaClass.simpleName}(${encodeToHex(bytes)})"
}

private val hexCode = "0123456789ABCDEF".toCharArray()

private fun encodeToHex(data: ByteArray): String {
    val r = StringBuilder(data.size * 2)
    for (b in data) {
        r.append(hexCode[(b.toInt() shr 4) and 0xF])
        r.append(hexCode[b.toInt() and 0xF])
    }
    return r.toString()
}
