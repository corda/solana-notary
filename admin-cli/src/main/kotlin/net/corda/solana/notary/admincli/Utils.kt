package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.lookup.AddressLookupTable
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.tx.Instruction
import software.sava.core.tx.TransactionSkeleton
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.response.TxMeta
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.stream.Stream

object Utils {
    val jsonMapper: JsonMapper = jsonMapperBuilder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    fun jsonMapperBuilder(): JsonMapper.Builder {
        return jacksonMapperBuilder()
            .addModule(
                SimpleModule().also {
                    it.addSerializer(PublicKey::class.java, PublicKeySerializer)
                    it.addDeserializer(PublicKey::class.java, PublicKeyDeserializer)
                }
            )
    }

    fun SolanaClient.getMaterializedTransaction(signature: String): MaterializedTransaction {
        val tx = call(SolanaRpcClient::getTransaction, signature)
        val skeleton = TransactionSkeleton.deserializeSkeleton(tx.data)
        val accounts = if (skeleton.isLegacy) {
            skeleton.parseAccounts()
        } else {
            val lookupTableAccounts = skeleton.lookupTableAccounts().asList()
            val lookupTables = if (lookupTableAccounts.isEmpty()) {
                Stream.empty()
            } else {
                call(SolanaRpcClient::getMultipleAccounts, lookupTableAccounts, AddressLookupTable.FACTORY)
                    .stream()
                    .map { it.data }
            }
            skeleton.parseAccounts(lookupTables)
        }
        val instructions = skeleton.parseInstructions(accounts)
        return MaterializedTransaction(tx.meta, accounts.asList(), instructions.asList())
    }

    data class MaterializedTransaction(
        val meta: TxMeta,
        val accounts: List<AccountMeta>,
        val instructions: List<Instruction>,
    )

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
