package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.CreateNetwork
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import java.util.concurrent.Callable

@Command(
    name = "create-network",
    description = ["Creates a new Corda network with the next available ID"],
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
)
class CreateNetworkCommand : Callable<Int> {
    @ArgGroup(exclusive = true, multiplicity = "1")
    private lateinit var signingConfig: SigningConfig

    @Mixin
    private val rpcConfig = RpcConfig()

    override fun call(): Int {
        val networkId = rpcConfig.getRequiredAdministration().nextNetworkId
        val sent = signingConfig.action(rpcConfig) { admin -> CreateNetwork.instruction(admin, networkId) }
        if (sent) {
            println("✓ Corda network successfully created - network ID: $networkId")
        }
        return 0
    }
}
