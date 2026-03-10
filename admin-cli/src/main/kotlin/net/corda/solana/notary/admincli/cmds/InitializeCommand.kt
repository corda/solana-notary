package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.SigningConfig
import net.corda.solana.notary.client.instructions.Initialize
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import software.sava.core.accounts.PublicKey

/**
 * Command to initialize the Solana notary configuration.
 */
class InitializeCommand : CliWrapperBase("initialize", "Initializes the Corda Notary on Solana blockchain") {
    @Mixin
    private val signingConfig = SigningConfig()

    @Mixin
    private val rpcConfig = RpcConfig()

    @Option(
        names = ["--admin-address"],
        description = [
            "The admin address in base 58.",
            "Cannot be used with --keypair.",
        ],
        required = false
    )
    private var adminAddress: PublicKey? = null

    override fun runProgram(): Int {
        require(
            (signingConfig.keypair != null && adminAddress == null) ||
                (signingConfig.keypair == null && adminAddress != null)
        ) {
            "Either --keypair or --admin-address must be specified"
        }
        val adminAddress = adminAddress ?: signingConfig.keypair!!.publicKey()
        return try {
            val sent = signingConfig.action(adminAddress, Initialize.instruction(adminAddress), rpcConfig)
            if (sent) {
                println("✓ Notary program initialized successfully with $adminAddress as admin.")
            }
            ExitCodes.SUCCESS
        } catch (e: SolanaTransactionException) {
            System.err.println("✗ Notary program initialization transaction failed: ${e.message}")
            e.logMessages.forEach(System.err::println)
            ExitCodes.FAILURE
        }
    }
}
