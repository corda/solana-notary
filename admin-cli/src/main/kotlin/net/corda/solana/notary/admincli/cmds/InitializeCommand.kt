package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.Initialize
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import software.sava.core.accounts.PublicKey

@Command(
    name = "initialize",
    description = ["Initializes the Corda Notary on Solana blockchain"],
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
)
class InitializeCommand : Runnable {
    @ArgGroup(exclusive = true, multiplicity = "1")
    private lateinit var signingConfig: SigningConfig

    @Mixin
    private val rpcConfig = RpcConfig()

    @Option(
        names = ["--admin-address"],
        description = [
            "The admin address in base 58.",
            "Cannot be used with --keypair.",
        ],
        required = false
    )
    private var adminAddress: PublicKey? = null

    override fun run() {
        require(
            (signingConfig.keypair != null && adminAddress == null) ||
                (signingConfig.keypair == null && adminAddress != null)
        ) {
            "Either --keypair or --admin-address must be specified"
        }
        val adminAddress = adminAddress ?: signingConfig.keypair!!.publicKey()
        val sent = signingConfig.action(adminAddress, Initialize.instruction(adminAddress), rpcConfig)
        if (sent) {
            println("✓ Notary program initialized successfully with $adminAddress as admin.")
        }
    }
}
