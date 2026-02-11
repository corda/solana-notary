package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.instructions.CreateNetwork
import picocli.CommandLine

/**
 * Command to create a new Corda notary network.
 */
class CreateNetworkCommand :
    CliWrapperBase("create-network", "Creates a new Corda notary network on the Solana blockchain") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()

        println("Creating network ...")
        return try {
            val networkId = ShowNextAvailableNetworkIdCommand.getNextNetworkId(solanaConfig)
            solanaConfig.client.sendAndConfirm(
                { it.createTransaction(CreateNetwork.instruction(solanaConfig.wallet.publicKey(), networkId)) },
                solanaConfig.wallet
            )
            println("✓ Corda network creation successful - network ID: $networkId")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Corda network creation failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
