package net.corda.solana.notary.common.codegen

import com.lmax.solana4j.api.PublicKey
import net.corda.solana.notary.common.Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder

object IdlCodeGenSupport {
    fun seedBytes(value: BorshSerialisable): ByteArray = value.toBorshBytes()

    fun seedBytes(value: Signer): ByteArray = value.account.bytes()

    fun seedBytes(value: PublicKey): ByteArray = value.bytes()

    // This assumes that the anchor annotation is using .to_le_bytes()
    fun seedBytes(value: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
}
