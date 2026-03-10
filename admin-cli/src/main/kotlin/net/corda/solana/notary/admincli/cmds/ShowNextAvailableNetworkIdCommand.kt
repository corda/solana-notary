package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.client.accounts.Administration
import net.corda.solana.notary.client.instructions.AuthorizeNotary.administrationPda
import picocli.CommandLine.Mixin
import software.sava.rpc.json.http.client.SolanaRpcClient

/**
 * Shows the next network ID that will be used when creating a new network. Useful for debugging.
 */
class ShowNextAvailableNetworkIdCommand : CliWrapperBase(
    "next-network-id",
    "Returns the the next network ID that will be used when creating a new network. Useful for debugging."
) {
    companion object {
        fun getNextNetworkId(client: SolanaClient): Short {
            val account = client.call(SolanaRpcClient::getAccountInfo, administrationPda().publicKey())
            return Administration.read(account.data).nextNetworkId
        }
    }

    @Mixin
    private val rpcConfig = RpcConfig()

    override fun runProgram(): Int {
        println(getNextNetworkId(rpcConfig.client))
        return ExitCodes.SUCCESS
    }
}
