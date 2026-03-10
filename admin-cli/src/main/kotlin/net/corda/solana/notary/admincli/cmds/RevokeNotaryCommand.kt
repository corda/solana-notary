package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.RevokeNotary
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import software.sava.core.accounts.PublicKey

/**
 * Command to revoke a notary account.
 */
class RevokeNotaryCommand :
    CliWrapperBase("revoke", "Revokes access for a notary account on the Solana notary program") {
    @Mixin
    private val signingConfig = SigningConfig()

    @Mixin
    private val rpcConfig = RpcConfig()

    @Parameters(
        paramLabel = "NOTARY_ADDRESS",
        index = "0",
        description = ["The notary address (base 58) to revoke"],
    )
    private lateinit var notaryAddress: PublicKey

    override fun runProgram(): Int {
        return try {
            val sent = signingConfig.action(rpcConfig) { admin -> RevokeNotary.instruction(notaryAddress, admin) }
            if (sent) {
                println("✓ Notary $notaryAddress successfully revoked")
            }
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary revocation transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
