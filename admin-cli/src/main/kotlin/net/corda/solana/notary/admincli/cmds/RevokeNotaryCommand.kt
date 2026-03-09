package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.KeypairConfig
import net.corda.solana.notary.admincli.RpcConfig
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
    var keypairConfig = KeypairConfig()

    @CommandLine.Mixin
    var rpcConfig = RpcConfig()

    @Parameters(
        paramLabel = "NOTARY_ADDRESS",
        index = "0",
        description = ["The notary address (base 58) to revoke"],
    )
    private lateinit var notaryAddress: PublicKey

    override fun runProgram(): Int {
        println("Revoking notary $notaryAddress...")
        val client = rpcConfig.startClient()
        return try {
            client.sendAndConfirm(
                { it.createTransaction(RevokeNotary.instruction(notaryAddress, keypairConfig.keypair.publicKey())) },
                keypairConfig.keypair
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
