package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.instructions.RevokeNotary
import net.corda.solana.notary.common.SolanaTransactionException
import picocli.CommandLine
import picocli.CommandLine.Option
import software.sava.core.accounts.PublicKey

/**
 * Command to revoke a notary account.
 */
class RevokeNotaryCommand :
    CliWrapperBase("revoke", "Revokes authorization for a notary account on the Solana notary program") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    @Option(
        names = ["--address", "-a"],
        description = ["The notary account base58 public key to revoke"],
        required = true
    )
    private lateinit var notaryAddress: String

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()

        solanaConfig.validateNotaryAddress(notaryAddress)

        println("Revoking notary $notaryAddress...")

        return try {
            solanaConfig.client.sendAndConfirm(
                {
                    it.createTransaction(
                        RevokeNotary.instruction(
                            PublicKey.fromBase58Encoded(notaryAddress),
                            solanaConfig.wallet.publicKey()
                        )
                    )
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
