package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.KeypairConfig
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.client.instructions.Initialize
import picocli.CommandLine

/**
 * Command to initialize the Solana notary configuration.
 */
class InitializeCommand : CliWrapperBase("initialize", "Initializes the Corda Notary on Solana blockchain") {
    @CommandLine.Mixin
    var keypairConfig = KeypairConfig()

    @CommandLine.Mixin
    var rpcConfig = RpcConfig()

    override fun runProgram(): Int {
        println("Initializing notary program ${CordaNotary.PROGRAM_ID}...")
        val client = rpcConfig.startClient()
        return try {
            client.sendAndConfirm(
                { it.createTransaction(Initialize.instruction(keypairConfig.keypair.publicKey())) },
                keypairConfig.keypair
            )
            println("✓ Notary program initialized successfully with ${keypairConfig.keypair.publicKey()} as admin.")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary program initialization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
