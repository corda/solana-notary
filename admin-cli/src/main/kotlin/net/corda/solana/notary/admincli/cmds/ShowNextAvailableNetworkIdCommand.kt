package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.accounts.Administration
import net.corda.solana.notary.client.instructions.AuthorizeNotary.administrationPda
import picocli.CommandLine
import software.sava.rpc.json.http.client.SolanaRpcClient

/**
 * Shows the next network ID that will be used when creating a new network. Useful for debugging.
 */
class ShowNextAvailableNetworkIdCommand : CliWrapperBase(
    "next-network-id",
    "Returns the the next network ID that will be used when creating a new network. Useful for debugging."
) {
    companion object {
        fun getNextNetworkId(solanaConfig: SolanaConfig): Short {
            val account = solanaConfig.client.call(SolanaRpcClient::getAccountInfo, administrationPda().publicKey())
            return Administration.read(account.data).nextNetworkId
        }
    }

    @CommandLine.Mixin
    var shared = SharedCliOptions()

    override fun runProgram(): Int {
        val solanaConfig = shared.toSolanaConfig()
        println("Next Network ID = ${getNextNetworkId(solanaConfig)}")
        return ExitCodes.SUCCESS
    }
}
