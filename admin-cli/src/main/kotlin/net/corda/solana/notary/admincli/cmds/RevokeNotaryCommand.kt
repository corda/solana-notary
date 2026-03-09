package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.instructions.RevokeNotary
import picocli.CommandLine
import picocli.CommandLine.Parameters
import software.sava.core.accounts.PublicKey

/**
 * Command to revoke a notary account.
 */
class RevokeNotaryCommand :
    CliWrapperBase("revoke", "Revokes access for a notary account on the Solana notary program") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    @Parameters(
        paramLabel = "NOTARY_ADDRESS",
        index = "0",
        description = ["The notary address (base 58) to revoke"],
    )
    private lateinit var notaryAddress: PublicKey

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()

        println("Revoking notary $notaryAddress...")

        return try {
            solanaConfig.client.sendAndConfirm(
                {
                    it.createTransaction(RevokeNotary.instruction(notaryAddress, solanaConfig.wallet.publicKey()))
                },
                solanaConfig.wallet
            )
            println("✓ Notary revoked successfully: $notaryAddress")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary revocation transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
