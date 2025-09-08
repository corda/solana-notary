package net.corda.solana.aggregator.admincli.cli

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.aggregator.admincli.common.SharedCliOptions
import net.corda.solana.aggregator.admincli.common.SolanaConfig
import net.corda.solana.aggregator.common.SolanaTransactionException
import net.corda.solana.aggregator.common.sendAndConfirm
import net.corda.solana.aggregator.notary.idl.CordaNotary
import picocli.CommandLine

/**
 * Command to initialize the Solana notary configuration.
 */
class InitializeCommand : CliWrapperBase("initialize", "Initializes the Corda Notary on Solana blockchain") {

    @CommandLine.Mixin
    var shared = SharedCliOptions()

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl) }

    override fun runProgram(): Int {
        println("Initializing notary program ${CordaNotary.PROGRAM_ID}...")

        val instruction = CordaNotary.Initialize(solanaConfig.wallet)
        return try {
            solanaConfig.rpcClient.sendAndConfirm(instruction)
            println("✓ Notary program initialized successfully with ${solanaConfig.wallet.account.base58()} as admin.")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary program initialization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
