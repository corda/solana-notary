package net.corda.solana.notary.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigInteger

/**
 * Modified from the bitcoinj library.
 */
class Base58Test {
    @Test
	fun testEncode() {
        val testbytes = "Hello World".toByteArray()
        assertThat(Base58.encode(testbytes)).isEqualTo("JxF12TrwUP45BMd")

        val bi = BigInteger.valueOf(3471844090L)
        assertThat(Base58.encode(bi.toByteArray())).isEqualTo("16Ho7Hs")

        val zeroBytes1 = ByteArray(1)
        assertThat(Base58.encode(zeroBytes1)).isEqualTo("1")

        val zeroBytes7 = ByteArray(7)
        assertThat(Base58.encode(zeroBytes7)).isEqualTo("1111111")

        // test empty encode
        assertThat(Base58.encode(byteArrayOf())).isEqualTo("")
    }

    @Test
	fun testDecode() {
        val testbytes = "Hello World".toByteArray()
        val actualbytes = Base58.decode("JxF12TrwUP45BMd")
        assertThat(actualbytes).isEqualTo(testbytes)


        assertThat(Base58.decode("1")).isEqualTo(ByteArray(1))
        assertThat(Base58.decode("1111")).isEqualTo(ByteArray(4))

        assertThatThrownBy { Base58.decode("This isn't valid base58") }.isInstanceOf(AddressFormatException::class.java)

        // Test decode of empty String.
        assertThat(Base58.decode("")).isEmpty()
    }
}
