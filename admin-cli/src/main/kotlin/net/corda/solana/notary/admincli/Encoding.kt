package net.corda.solana.notary.admincli

import software.sava.core.encoding.Base58
import java.util.Base64

enum class Encoding {
    BASE_58,
    BASE_64,
    ;

    fun encode(bytes: ByteArray): String {
        return when (this) {
            BASE_58 -> Base58.encode(bytes)
            BASE_64 -> Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun decode(string: String): ByteArray {
        return when (this) {
            BASE_58 -> Base58.decode(string)
            BASE_64 -> Base64.getDecoder().decode(string)
        }
    }

    override fun toString(): String {
        return when (this) {
            BASE_58 -> "base58"
            BASE_64 -> "base64"
        }
    }

    companion object {
        fun parse(string: String): Encoding {
            return when (string) {
                "base58" -> BASE_58
                "base64" -> BASE_64
                else -> throw IllegalArgumentException("Invalid encoding: $string")
            }
        }
    }
}
