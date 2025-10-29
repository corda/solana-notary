package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.common.rpc.SolanaTransactionException
import net.corda.solana.notary.common.rpc.sendAndConfirm
import picocli.CommandLine

/**
 * Command to initialize the Solana notary configuration.
 */
class InitializeCommand : CliWrapperBase("initialize", "Initializes the Corda Notary on Solana blockchain") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl, shared.commitment) }

    override fun runProgram(): Int {
        println("Initializing notary program ${CordaNotary.PROGRAM_ID.base58()}...")

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
