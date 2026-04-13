package net.corda.solana.notary.admincli

import software.sava.core.accounts.PublicKey
import software.sava.core.encoding.Base58
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.jacksonTypeRef
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

class HeliusClient(private val httpClient: HttpClient, val cluster: Cluster, private val apiKey: String) {
    companion object {
        fun fromRpcUrl(httpClient: HttpClient, rpcUrl: URI): HeliusClient {
            val cluster = when (rpcUrl.host) {
                "mainnet.helius-rpc.com" -> Cluster.Mainnet
                "devnet.helius-rpc.com" -> Cluster.Devnet
                else -> throw IllegalArgumentException("Not Helius RPC: ${rpcUrl.host}")
            }
            val apiKey = rpcUrl.query
                .splitToSequence('&')
                .firstNotNullOfOrNull { keyValue ->
                    val (key, value) = keyValue.split('=')
                    value.takeIf { key == "api-key" }
                }
            requireNotNull(apiKey) { "RPC URL is missing api-key" }
            return HeliusClient(httpClient, cluster, apiKey)
        }

        private val mapper = jacksonMapperBuilder()
            .addModule(
                SimpleModule().also {
                    it.addDeserializer(PublicKey::class.java, PublicKeyDeserializer())
                    it.addDeserializer(ByteArray::class.java, Base58Deserializer())
                }
            )
            .build()
    }

    fun getEnhancedTransactions(address: PublicKey, source: String?): List<TransactionJson> {
        val url = buildString {
            append("https://api-")
                .append(cluster.name.lowercase())
                .append(".helius-rpc.com/v0/addresses/")
                .append(address)
                .append("/transactions?api-key=")
                .append(apiKey)
            if (source != null) {
                append('&').append("source").append('=').append(source)
            }
        }
        val response = httpClient.send(HttpRequest.newBuilder(URI(url)).GET().build(), BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            val message = response.body().use { it.reader().readAllAsString() }
            throw IOException("Error response ${response.statusCode()}: $message")
        }
        val body = response.body()
        return body.use {
            mapper.readValue(body, jacksonTypeRef<List<TransactionJson>>())
        }
    }

    data class TransactionJson(val instructions: List<InstructionJson>)

    data class InstructionJson(
        val programId: PublicKey,
        val accounts: List<PublicKey>,
        val data: ByteArray,
        val innerInstructions: List<InnerInstructionJson>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is InstructionJson) return false
            if (programId != other.programId) return false
            if (accounts != other.accounts) return false
            if (!data.contentEquals(other.data)) return false
            if (innerInstructions != other.innerInstructions) return false
            return true
        }

        override fun hashCode(): Int {
            var result = programId.hashCode()
            result = 31 * result + accounts.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + innerInstructions.hashCode()
            return result
        }
    }

    data class InnerInstructionJson(val programId: PublicKey)

    enum class Cluster {
        Mainnet,
        Devnet,
    }

    private class PublicKeyDeserializer : ValueDeserializer<PublicKey>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PublicKey {
            return PublicKey.fromBase58Encoded(p.valueAsString)
        }
    }

    private class Base58Deserializer : ValueDeserializer<ByteArray>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
            return Base58.decode(p.valueAsString)
        }
    }
}
