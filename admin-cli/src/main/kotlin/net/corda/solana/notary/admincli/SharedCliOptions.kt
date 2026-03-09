package net.corda.solana.notary.admincli

import picocli.CommandLine.Option
import software.sava.rpc.json.http.request.Commitment
import java.net.URI
import java.nio.file.Path

class SharedCliOptions {
    @Option(
        names = ["--keypair", "-k"],
        description = ["Optional: Path to the Admin Solana keypair file [default: ~/.config/solana/id.json]"],
        required = false
    )
    var keypairPath: Path? = null

    @Option(
        names = ["--rpc"],
        description = [
            "Optional: The RPC URL of the Solana cluster to connect to",
            "Default: taken from the ~/.config/solana/cli/config.yaml file"
        ],
        required = false
    )
    var rpcUrl: URI? = null

    @Option(
        names = ["--websocket"],
        description = [
            "Optional: The websocket URL of the Solana cluster to connect to",
            "Default: taken from the ~/.config/solana/cli/config.yaml file"
        ],
        required = false
    )
    var websocketUrl: URI? = null

    @Option(
        names = ["--commitment", "-c"],
        description = [
            "Optional: The commitment level to wait for on the cluster",
            "Default: taken from the ~/.config/solana/cli/config.yaml file"
        ],
        required = false
    )
    var commitment: Commitment? = null

    fun toSolanaConfig(): SolanaConfig = SolanaConfig(keypairPath, rpcUrl, websocketUrl, commitment)
}
