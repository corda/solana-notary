package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.client.accounts.Network
import net.corda.solana.notary.client.accounts.NotaryAuthorization
import picocli.CommandLine.Mixin
import software.sava.rpc.json.http.client.SolanaRpcClient

/**
 * Command to list all notaries registered on the Solana notary program.
 */
class ListNotariesCommand : CliWrapperBase(
    "list-notaries",
    "Returns the list of notaries registered on the Solana notary program"
) {
    @Mixin
    private val rpcConfig = RpcConfig()

    override fun runProgram(): Int {
        val networkIds = rpcConfig
            .client
            .call(SolanaRpcClient::getProgramAccounts, PROGRAM_ID, listOf(Network.DISCRIMINATOR_FILTER))
            .map { Network.read(it.data).networkId }

        val notariesByNetworkId = rpcConfig
            .client
            .call(SolanaRpcClient::getProgramAccounts, PROGRAM_ID, listOf(NotaryAuthorization.DISCRIMINATOR_FILTER))
            .map { NotaryAuthorization.read(it.data) }
            .groupBy { it.networkId }

        // Get all networks
        for (networkId in networkIds) {
            println("Network ID: $networkId")
            val notaries = notariesByNetworkId[networkId]
            if (notaries != null) {
                notaries.forEachIndexed { index, pda ->
                    println("  ${index + 1}. Notary: ${pda.notary}")
                }
            } else {
                println("  No notaries registered")
            }
        }

        return ExitCodes.SUCCESS
    }
}
