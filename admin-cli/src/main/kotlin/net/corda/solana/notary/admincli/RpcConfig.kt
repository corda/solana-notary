package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import picocli.CommandLine.Option
import software.sava.rpc.json.http.request.Commitment
import java.net.URI

class RpcConfig {
    @Option(
        names = ["--rpc"],
        description = [
            "The RPC URL of the Solana cluster to connect to",
        ],
        required = false
    )
    var rpcUrl: URI = URI.create("https://api.mainnet.solana.com")

    @Option(
        names = ["--ws"],
        description = [
            "The websocket URL of the Solana cluster to connect to.",
            "If not specified then derived from the RPC URL.",
        ],
        required = false
    )
    var websocketUrl: URI = toWebSocketUrl(rpcUrl)

    @Option(
        names = ["--commitment", "-c"],
        description = [
            "The commitment level to wait for on the cluster",
        ],
        required = false
    )
    var commitment: Commitment = Commitment.FINALIZED

    fun startClient(): SolanaClient {
        val client = SolanaClient(rpcUrl, websocketUrl, commitment)
        client.start()
        return client
    }

    companion object {
        fun toWebSocketUrl(rpcUrl: URI): URI {
            val websocketScheme = when (rpcUrl.scheme) {
                "https" -> "wss"
                "http" -> "ws"
                else -> throw IllegalArgumentException("Unexpected scheme: ${rpcUrl.scheme}")
            }
            return URI(websocketScheme, rpcUrl.authority, rpcUrl.path, rpcUrl.query, rpcUrl.fragment)
        }
    }
}
