package net.corda.solana.aggregator.admincli.cli

import com.lmax.solana4j.Solana
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.aggregator.admincli.common.SharedCliOptions
import net.corda.solana.aggregator.admincli.common.SolanaConfig
import net.corda.solana.aggregator.common.SolanaTransactionException
import net.corda.solana.aggregator.common.sendAndConfirm
import net.corda.solana.notary.client.kotlin.CordaNotary
import picocli.CommandLine
import picocli.CommandLine.Option

/**
 * Command to revoke a notary account.
 */
class RevokeNotaryCommand : CliWrapperBase("revoke", "Revokes authorization for a notary account on the Solana notary program") {

    @CommandLine.Mixin
    var shared = SharedCliOptions()

    @Option(
            names = ["--address", "-a"],
            description = ["The notary account base58 public key to revoke"],
            required = true
    )
    private lateinit var notaryAddress: String
    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl) }

    override fun runProgram(): Int {
        solanaConfig.validateNotaryAddress(notaryAddress)

        println("Revoking notary $notaryAddress...")

        val notaryAccount = Solana.account(notaryAddress)
        val instruction = CordaNotary.RevokeNotary(notaryAccount, solanaConfig.wallet)
        return try {
            solanaConfig.rpcClient.sendAndConfirm(instruction)
            println("✓ Notary revoked successfully: $notaryAddress")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary revocation transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
