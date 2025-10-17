package net.corda.solana.notary.admincli

import picocli.CommandLine.Option

class SharedCliOptions {
    @Option(
        names = ["--keypair", "-k"],
        description = ["Optional: Path to the Admin Solana keypair file [default: ~/.config/solana/id.json]"],
        required = false
    )
    var keypairPath: String? = null

    @Option(
        names = ["--url", "-u"],
        description = [
            "Optional: The RPC URL of the Solana cluster to connect to",
            "Default: taken from the ~/.config/solana/cli/config.yaml file"
        ],
        required = false
    )
    var rpcUrl: String? = null
}
