package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.RevokeNotary
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import software.sava.core.accounts.PublicKey

@Command(
    name = "revoke",
    description = ["Revokes access for a notary account on the Solana notary program"],
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
)
class RevokeNotaryCommand : Runnable {
    @Mixin
    private val signingConfig = SigningConfig()

    @Mixin
    private val rpcConfig = RpcConfig()

    @Parameters(
        paramLabel = "NOTARY_ADDRESS",
        index = "0",
        description = ["The notary address (base 58) to revoke"],
    )
    private lateinit var notaryAddress: PublicKey

    override fun run() {
        val sent = signingConfig.action(rpcConfig) { admin -> RevokeNotary.instruction(notaryAddress, admin) }
        if (sent) {
            println("✓ Notary $notaryAddress successfully revoked")
        }
    }
}
