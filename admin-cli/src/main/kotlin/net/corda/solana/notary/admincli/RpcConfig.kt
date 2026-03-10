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
    private var rpcUrl: URI = URI.create("https://api.mainnet.solana.com")

    @Option(
        names = ["--ws"],
        description = [
            "The websocket URL of the Solana cluster to connect to.",
            "If not specified then derived from the RPC URL.",
        ],
        required = false
    )
    private var websocketUrl: URI = toWebSocketUrl(rpcUrl)

    @Option(
        names = ["--commitment", "-c"],
        description = [
            "The commitment level to wait for on the cluster.",
            $$"Options: ${COMPLETION-CANDIDATES}"
        ],
        required = false
    )
    private var commitment: Commitment = Commitment.FINALIZED

    val client: SolanaClient by lazy {
        SolanaClient(rpcUrl, websocketUrl, commitment).apply { start() }
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
