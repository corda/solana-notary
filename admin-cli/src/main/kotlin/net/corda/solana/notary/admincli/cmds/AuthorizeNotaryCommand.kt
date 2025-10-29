package net.corda.solana.notary.admincli.cmds

import com.lmax.solana4j.Solana
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.common.rpc.SolanaTransactionException
import net.corda.solana.notary.common.rpc.sendAndConfirm
import picocli.CommandLine
import picocli.CommandLine.Option

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

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl, shared.commitment) }

    override fun runProgram(): Int {
        solanaConfig.validateNotaryAddress(notaryAddress)

        println("Authorizing notary $notaryAddress...")

        val notaryAccount = Solana.account(notaryAddress)
        val instruction = CordaNotary.AuthorizeNotary(notaryAccount, solanaConfig.wallet, networkId.toShort())
        return try {
            solanaConfig.rpcClient.sendAndConfirm(instruction)
            println("✓ Notary authorized successfully: $notaryAddress")
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary authorization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
