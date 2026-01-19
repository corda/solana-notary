package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.client.instructions.AuthorizeNotary
import net.corda.solana.notary.common.SolanaTransactionException
import picocli.CommandLine
import picocli.CommandLine.Option
import software.sava.core.accounts.PublicKey

/**
 * Command to authorize a notary account.
 */
class AuthorizeNotaryCommand : CliWrapperBase("authorize", "Authorizes a notary account on the Solana notary program") {
    @CommandLine.Mixin
    var shared = SharedCliOptions()

    @Option(
        names = ["--address", "-a"],
        description = ["The notary account base 58 public key to authorize"],
        required = true
    )
    private lateinit var notaryAddress: String

    @Option(
        names = ["--network", "-n"],
        description = ["The network ID to assign this notary to"],
        required = true
    )
    private lateinit var networkId: String

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()
        solanaConfig.validateNotaryAddress(notaryAddress)

        println("Authorizing notary $notaryAddress...")

        return try {
            solanaConfig.client.sendAndConfirm(
                {
                    it.createTransaction(
                        AuthorizeNotary.instruction(
                            PublicKey.fromBase58Encoded(notaryAddress),
                            solanaConfig.wallet.publicKey(),
                            networkId.toShort()
                        )
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
