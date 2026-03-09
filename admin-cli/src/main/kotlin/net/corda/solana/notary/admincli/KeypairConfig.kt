package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.FileSigner
import picocli.CommandLine.Option

class KeypairConfig {
    @Option(
        names = ["--keypair", "-k"],
        description = ["Keypair file of the admin authority"],
        required = true
    )
    lateinit var keypair: FileSigner
}
