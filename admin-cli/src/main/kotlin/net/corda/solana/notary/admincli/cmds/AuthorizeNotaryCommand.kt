package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.instructions.AuthorizeNotary
import picocli.CommandLine
import picocli.CommandLine.Parameters
import software.sava.core.accounts.PublicKey

/**
 * Command to authorize a notary account.
 */
class AuthorizeNotaryCommand : CliWrapperBase("authorize", "Authorizes a notary account on the Solana notary program") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

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

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()

        println("Authorizing notary $notaryAddress...")

        return try {
            solanaConfig.client.sendAndConfirm(
                {
                    it.createTransaction(
                        AuthorizeNotary.instruction(notaryAddress, solanaConfig.wallet.publicKey(), networkId!!)
                    )
                },
                solanaConfig.wallet
            )
            println("✓ Notary authorized successfully: $notaryAddress")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary authorization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
