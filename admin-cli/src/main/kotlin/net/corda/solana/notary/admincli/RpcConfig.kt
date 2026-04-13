package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.solana.notary.client.accounts.Administration
import net.corda.solana.notary.client.instructions.Initialize.administrationPda
import picocli.CommandLine.Option
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.request.Commitment
import java.net.URI
import java.util.concurrent.Executors

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
        val defaultRateLimit = if (rpcUrl.host.endsWith(".solana.com")) 4 else null
        SolanaClient(
            rpcUrl,
            websocketUrl,
            commitment,
            defaultRateLimit = defaultRateLimit,
            userMainExecutor = Executors.newVirtualThreadPerTaskExecutor()
        ).apply { start() }
    }

    private val _administration: Administration? by lazy {
        client
            .call(SolanaRpcClient::getAccountInfo, administrationPda().publicKey())
            .data
            ?.let(Administration::read)
    }

    fun getOptionalAdministration(): Administration? = _administration

    fun getAdministration(): Administration {
        return checkNotNull(getOptionalAdministration()) { "Notary program has not been initialized" }
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
