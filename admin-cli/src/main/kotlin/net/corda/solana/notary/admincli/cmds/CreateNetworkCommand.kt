package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.CreateNetwork
import picocli.CommandLine.Mixin

/**
 * Command to create a new Corda notary network.
 */
class CreateNetworkCommand : CliWrapperBase(
    "create-network",
    "Creates a new Corda network with the next available ID"
) {
    @Mixin
    private val signingConfig = SigningConfig()

    @Mixin
    private val rpcConfig = RpcConfig()

    override fun runProgram(): Int {
        val networkId = rpcConfig.getRequiredAdministration().nextNetworkId
        return try {
            val sent = signingConfig.action(rpcConfig) { admin -> CreateNetwork.instruction(admin, networkId) }
            if (sent) {
                println("✓ Corda network successfully created - network ID: $networkId")
            }
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Corda network creation failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
