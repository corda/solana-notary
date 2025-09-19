package net.corda.solana.notary.common.codegen

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.PublicKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

// https://borsh.io/#pills-specification
// https://github.com/near/borshj/blob/master/src/main/java/org/near/borshj
object BorshUtils {
    fun fixedSize(clazz: Class<*>): Int? {
        when (clazz) {
            Byte::class.java, java.lang.Byte::class.java -> return 1
            Short::class.java, java.lang.Short::class.java -> return 2
            Int::class.java, Integer::class.java -> return 4
            Long::class.java, java.lang.Long::class.java -> return 8
            Float::class.java, java.lang.Float::class.java -> return 4
            Double::class.java, java.lang.Double::class.java -> return 8
            Boolean::class.java, java.lang.Boolean::class.java -> return 1
        }
        if (PublicKey::class.java.isAssignableFrom(clazz)) {
            return PublicKey.PUBLIC_KEY_LENGTH
        }
        if (BorshSerialisable::class.java.isAssignableFrom(clazz)) {
            try {
                return clazz.getDeclaredField("BORSH_SIZE").get(null) as Int
            } catch (_: Exception) { }
        }
        return null
    }

    fun readByte(buffer: ByteBuffer): Byte {
        return buffer.get()
    }

    fun readShort(buffer: ByteBuffer): Short {
        return buffer.getShort()
    }

    fun write(buffer: ByteBuffer, value: Byte) {
        buffer.put(value)
    }

    fun write(buffer: ByteBuffer, value: Short) {
        buffer.putShort(value)
    }

    fun readInt(buffer: ByteBuffer): Int {
        return buffer.getInt()
    }

    fun write(buffer: ByteBuffer, value: Int) {
        buffer.putInt(value)
    }

    fun write(buffer: ByteBuffer, value: Long) {
        buffer.putLong(value)
    }

    fun write(buffer: ByteBuffer, value: Float) {
        buffer.putFloat(value)
    }

    fun write(buffer: ByteBuffer, value: Double) {
        buffer.putDouble(value)
    }

    fun write(buffer: ByteBuffer, value: Boolean) {
        buffer.put(if (value) 1 else 0)
    }

    fun size(value: ByteArray): Int {
        return 4 + value.size
    }

    fun readByteArray(buffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(readInt(buffer))
        buffer.get(bytes)
        return bytes
    }

    fun write(buffer: ByteBuffer, value: ByteArray) {
        write(buffer, value.size)
        buffer.put(value)
    }

    fun readPublicKey(buffer: ByteBuffer): PublicKey {
        val bytes = ByteArray(PublicKey.PUBLIC_KEY_LENGTH)
        buffer.get(bytes)
        return Solana.account(bytes)
    }

    fun write(buffer: ByteBuffer, value: PublicKey) {
        value.write(buffer)
    }

    fun size(value: List<*>): Int {
        var size = size(value.size)
        for (element in value) {
            size += size(element)
        }
        return size
    }

    inline fun <reified T> readList(buffer: ByteBuffer, readElement: () -> T): List<T> {
        val size = readInt(buffer)
        return Array(size) { readElement() }.asList()
    }

    fun write(buffer: ByteBuffer, value: List<*>) {
        write(buffer, value.size)
        for (element in value) {
            write(buffer, element)
        }
    }

    fun size(value: BorshSerialisable): Int {
        return value.borshSize
    }

    fun write(buffer: ByteBuffer, value: BorshSerialisable) {
        value.borshWrite(buffer)
    }

    private fun size(value: Any?): Int {
        val fixedSize = value?.javaClass?.let(::fixedSize)
        if (fixedSize != null) {
            return fixedSize
        }
        return when (value) {
            is ByteArray -> size(value)
            is List<*> -> size(value)
            is BorshSerialisable -> size(value)
            else -> throw UnsupportedOperationException(value?.javaClass.toString())
        }
    }

    private fun write(buffer: ByteBuffer, value: Any?) {
        when (value) {
            is Byte -> write(buffer, value)
            is Short -> write(buffer, value)
            is Int -> write(buffer, value)
            is Long -> write(buffer, value)
            is Float -> write(buffer, value)
            is Double -> write(buffer, value)
            is Boolean -> write(buffer, value)
            is ByteArray -> write(buffer, value)
            is PublicKey -> write(buffer, value)
            is List<*> -> write(buffer, value)
            is BorshSerialisable -> write(buffer, value)
            else -> throw UnsupportedOperationException(value?.javaClass.toString())
        }
    }
}

/**
 * Concrete types should also have a static `borshRead` method which takes in a single [ByteBuffer] parameter and returns the deserialised
 * instance of the same type.
 *
 * If a concrete type has a fixed size then also add a `BORSH_SIZE` static constant (see [U128] as example).
 */
interface BorshSerialisable {
    val borshSize: Int

    /**
     * It is assumed the buffer is in little endian order.
     */
    fun borshWrite(buffer: ByteBuffer)
}

fun BorshSerialisable.toBorshBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(borshSize)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    borshWrite(buffer)
    return buffer.array()
}
