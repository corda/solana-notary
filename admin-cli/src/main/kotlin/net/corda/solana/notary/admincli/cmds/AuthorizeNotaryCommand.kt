package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.AuthorizeNotary
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import software.sava.core.accounts.PublicKey

@Command(
    name = "authorize",
    description = ["Authorizes a notary account on the Solana notary program"],
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
)
class AuthorizeNotaryCommand : Runnable {
    @ArgGroup(exclusive = true, multiplicity = "1")
    private lateinit var signingConfig: SigningConfig

    @Mixin
    private val rpcConfig = RpcConfig()

    @Parameters(
        paramLabel = "NOTARY_ADDRESS",
        index = "0",
        description = ["The notary address (base 58) to authorize"],
    )
    private lateinit var notaryAddress: PublicKey

    @Parameters(
        paramLabel = "NETWORK_ID",
        index = "1",
        description = ["The network ID to assign this notary to"],
    )
    private var networkId: Short? = null

    override fun run() {
        val sent = signingConfig.action(rpcConfig) { admin ->
            AuthorizeNotary.instruction(notaryAddress, admin, networkId!!)
        }
        if (sent) {
            println("✓ Notary $notaryAddress successfully authorized")
        }
    }
}
