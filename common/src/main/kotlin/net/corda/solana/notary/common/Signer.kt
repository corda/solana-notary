package net.corda.solana.notary.common

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.ByteBufferSigner
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.api.SignedMessageBuilder
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.io.path.readText
import kotlin.io.path.writeText

class Signer(val account: PublicKey, val byteBufferSigner: ByteBufferSigner) {
    companion object {
        @JvmStatic
        fun fromFile(file: Path): Signer {
            val content = file.readText().trim()
            require(content.startsWith("[") && content.endsWith("]"))
            val keyPairBytes = content
                .substring(1, content.length - 1)
                .splitToSequence(",")
                .map {
                    val byte = it.trim().toInt()
                    require(byte in 0..255)
                    byte.toByte()
                }.toList()
            require(keyPairBytes.size == 64)
            val privateKeyBytes = keyPairBytes.subList(0, Ed25519.SECRET_KEY_SIZE).toByteArray()
            return fromPrivateKey(privateKeyBytes)
        }

        fun fromPrivateKey(privateKey: ByteArray): Signer {
            val publicKey = toPublicKey(privateKey)
            return Signer(Solana.account(publicKey), PrivateKeyByteBufferSigner(privateKey))
        }

        @JvmStatic
        fun random(): Signer {
            val random = SecureRandom()
            return fromPrivateKey(ByteArray(Ed25519.SECRET_KEY_SIZE).apply(random::nextBytes))
        }
    }

    override fun toString(): String = "Signer(${account.base58()})"
}

class PrivateKeyByteBufferSigner(privateKey: ByteArray) : ByteBufferSigner {
    private val privateKey = privateKey.clone()

    override fun sign(transaction: ByteBuffer, signature: ByteBuffer) {
        Ed25519.sign(
            privateKey,
            0,
            transaction.array(),
            transaction.arrayOffset() + transaction.position(),
            transaction.limit() - transaction.position(),
            signature.array(),
            signature.arrayOffset() + signature.position()
        )
    }

    fun writeToFile(file: Path) {
        file.writeText(
            buildString(256) {
                append('[')
                appendUBytes(privateKey)
                append(',')
                appendUBytes(toPublicKey(privateKey))
                append(']')
            }
        )
    }

    private fun StringBuilder.appendUBytes(bytes: ByteArray) {
        for (i in bytes.indices) {
            append(bytes[i].toUByte().toInt())
            if (i < bytes.size - 1) {
                append(',')
            }
        }
    }
}

private fun toPublicKey(privateKey: ByteArray): ByteArray {
    return Ed25519PrivateKeyParameters(privateKey).generatePublicKey().encoded
}

fun SignedMessageBuilder.by(signer: Signer): SignedMessageBuilder = by(signer.account, signer.byteBufferSigner)
