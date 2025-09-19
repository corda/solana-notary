package net.corda.solana.aggregator.common

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.client.api.Commitment
import org.bouncycastle.util.encoders.Base64
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

const val HTTP_OK = 200

@Suppress("LongParameterList")
fun <T> getProgramAnchorAccounts(
        programId: PublicKey,
        baseUrl: String, client: HttpClient,
        discriminator: ByteArray,
        deserializer: (ByteBuffer) -> T,
        commitment: Commitment = Commitment.CONFIRMED,
): List<T> {
    val requestBody = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "getProgramAccounts",
              "params": [
                "${programId.base58()}",
                {
                  "commitment": "${commitment.name.lowercase()}",
                  "filters": [
                    {
                      "memcmp": {
                        "bytes": "${Base58.encode(discriminator)}",
                        "offset": 0
                      }
                    }
                  ],
                  "encoding": "base64"
                }
              ]
            }
        """.trimIndent()

    val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    return if (response.statusCode() != HTTP_OK) {
        throw IOException("Failed to fetch program accounts: ${response.statusCode()} - ${response.body()}")
    } else {
        val parsedResponse = mapper.readValue(response.body(), ProgramAccountsResponse::class.java)
        parsedResponse.result.map { accountInfo ->
            val data = Base64.decode(accountInfo.account.data[0])
            val dataOffset = discriminator.size
            val dataLength = data.size - dataOffset
            deserializer(ByteBuffer.wrap(data, dataOffset, dataLength).order(ByteOrder.LITTLE_ENDIAN))
        }
    }
}

@JsonIgnoreProperties
data class ProgramAccountsResponse(
        val jsonrpc: String,
        val result: List<AccountInfo>,
        val id: Int,
)

@JsonIgnoreProperties
data class AccountInfo(
        val account: AccountData,
        val pubkey: String,
)

@JsonIgnoreProperties
class AccountData(
        val data: Array<String>,
)

