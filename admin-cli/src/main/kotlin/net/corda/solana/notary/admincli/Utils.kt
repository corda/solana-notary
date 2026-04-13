package net.corda.solana.notary.admincli

import software.sava.core.accounts.PublicKey
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder

object Utils {
    fun jsonMapperBuilder(): JsonMapper.Builder {
        return jacksonMapperBuilder()
            .addModule(
                SimpleModule().also {
                    it.addSerializer(PublicKey::class.java, PublicKeySerializer)
                    it.addDeserializer(PublicKey::class.java, PublicKeyDeserializer)
                }
            )
    }

    private object PublicKeySerializer : ValueSerializer<PublicKey>() {
        override fun serialize(value: PublicKey, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(value.toBase58())
        }
    }

    private object PublicKeyDeserializer : ValueDeserializer<PublicKey>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PublicKey {
            return PublicKey.fromBase58Encoded(p.valueAsString)
        }
    }
}
