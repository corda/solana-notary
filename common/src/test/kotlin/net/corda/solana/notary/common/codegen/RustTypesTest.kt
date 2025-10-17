package net.corda.solana.notary.common.codegen

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.nio.ByteBuffer

class RustTypesTest {
    @Test
    fun `U128 rejects BigIntegers larger than 128 bits`() {
        U128(BigInteger.TWO.pow(128) - BigInteger.ONE)
        val bigint = BigInteger.TWO.pow(128)
        assertThatIllegalArgumentException().isThrownBy { U128(bigint) }
    }

    @Test
    fun `U128 rejects negative numbers`() {
        U128(BigInteger.ZERO)
        val minusOne = (-1).toBigInteger()
        assertThatIllegalArgumentException().isThrownBy { U128(minusOne) }
        U128(BigInteger.ONE)
    }

    @Test
    fun `U128 borshWrite`() {
        assertThat(borshWriteU128(BigInteger.ZERO)).isEqualTo(ByteArray(16))
        assertThat(borshWriteU128(BigInteger.ONE))
            .isEqualTo(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertThat(borshWriteU128(0x12345678.toBigInteger()))
            .isEqualTo(byteArrayOf(0x78, 0x56, 0x34, 0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertThat(borshWriteU128(BigInteger.TWO.pow(128) - BigInteger.ONE)).isEqualTo(ByteArray(16) { 0xff.toByte() })
    }

    @Test
    fun `U128 borshRead`() {
        assertThat(borshReadU128(ByteArray(16))).isEqualTo(BigInteger.ZERO)
        assertThat(borshReadU128(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo(BigInteger.ONE)
        assertThat(borshReadU128(byteArrayOf(0x78, 0x56, 0x34, 0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
            .isEqualTo(0x12345678.toBigInteger())
        assertThat(borshReadU128(ByteArray(16) { 0xff.toByte() })).isEqualTo(BigInteger.TWO.pow(128) - BigInteger.ONE)
    }

    private fun borshWriteU128(bigint: BigInteger): ByteArray = U128(bigint).toBorshBytes()

    private fun borshReadU128(bytes: ByteArray): BigInteger = U128.borshRead(ByteBuffer.wrap(bytes)).bigint
}
