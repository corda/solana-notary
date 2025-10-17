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

class Signer(val account: PublicKey, val byteBufferSigner: ByteBufferSigner) {
    companion object {
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
            val publicKey = Ed25519PrivateKeyParameters(privateKey).generatePublicKey().encoded
            return Signer(Solana.account(publicKey), PrivateKeyByteBufferSigner(privateKey))
        }

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
}

fun SignedMessageBuilder.by(signer: Signer): SignedMessageBuilder = by(signer.account, signer.byteBufferSigner)
