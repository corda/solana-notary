package net.corda.solana.notary.admincli.cmds

import com.lmax.solana4j.Solana.programDerivedAddress
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.kotlin.CordaNotary
import net.corda.solana.notary.client.kotlin.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.common.rpc.DefaultRpcParams
import net.corda.solana.notary.common.rpc.getAnchorAccount
import picocli.CommandLine

/**
 * Shows the next network ID that will be used when creating a new network. Useful for debugging.
 */
class ShowNextAvailableNetworkIdCommand : CliWrapperBase(
    "next-network-id",
    "Returns the the next network ID that will be used when creating a new network. Useful for debugging."
) {
    companion object {
        fun getNextNetworkId(solanaConfig: SolanaConfig): Short {
            val administrationPda = programDerivedAddress(listOf("admin".toByteArray()), PROGRAM_ID).address()
            val adminInfo = solanaConfig.rpcClient.getAnchorAccount(
                administrationPda.base58(),
                CordaNotary.Accounts.Administration.DISCRIMINATOR,
                DefaultRpcParams(),
                CordaNotary.Accounts.Administration::borshRead
            )
            return adminInfo.nextNetworkId
        }
    }

    @CommandLine.Mixin
    var shared = SharedCliOptions()

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl) }

    override fun runProgram(): Int {
        println("Next Network ID = ${getNextNetworkId(solanaConfig)}")
        return ExitCodes.SUCCESS
    }
}
