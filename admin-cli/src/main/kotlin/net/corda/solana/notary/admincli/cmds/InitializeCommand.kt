package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.client.instructions.Initialize
import net.corda.solana.notary.common.SolanaTransactionException
import picocli.CommandLine

/**
 * Command to initialize the Solana notary configuration.
 */
class InitializeCommand : CliWrapperBase("initialize", "Initializes the Corda Notary on Solana blockchain") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()
        println("Initializing notary program ${CordaNotary.PROGRAM_ID}...")

        return try {
            solanaConfig.client.sendAndConfirm(
                { it.createTransaction(Initialize.instruction(solanaConfig.wallet.publicKey())) },
                solanaConfig.wallet
            )
            println("✓ Notary program initialized successfully with ${solanaConfig.wallet.publicKey()} as admin.")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary program initialization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
