package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.kotlin.CordaNotary
import net.corda.solana.notary.common.rpc.SolanaTransactionException
import net.corda.solana.notary.common.rpc.sendAndConfirm
import picocli.CommandLine

/**
 * Command to create a new Corda notary network.
 */
class CreateNetworkCommand :
    CliWrapperBase("create-network", "Creates a new Corda notary network on the Solana blockchain") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl) }

    override fun runProgram(): Int {
        println("Creating network ...")
        return try {
            val networkId = ShowNextAvailableNetworkIdCommand.getNextNetworkId(solanaConfig)
            val instruction = CordaNotary.CreateNetwork(solanaConfig.wallet, networkId)
            solanaConfig.rpcClient.sendAndConfirm(instruction)
            println("✓ Corda network creation successful - network ID: $networkId")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Corda network creation failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
