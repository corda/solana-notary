package net.corda.solana.notary.admincli.cmds

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.solana.notary.common.rpc.getProgramAnchorAccounts
import net.corda.solana.notary.admincli.SharedCliOptions
import net.corda.solana.notary.admincli.SolanaConfig
import net.corda.solana.notary.client.kotlin.CordaNotary
import picocli.CommandLine
import java.net.http.HttpClient

/**
 * Command to list all notaries registered on the Solana notary program.
 */
class ListNotariesCommand : CliWrapperBase("list-notaries", "Returns the list of notaries registered on the Solana notary program") {

    @CommandLine.Mixin
    var shared = SharedCliOptions()

    private val solanaConfig by lazy { SolanaConfig(shared.keypairPath, shared.rpcUrl) }

    override fun runProgram(): Int {

        val parsedNotaries = getProgramAnchorAccounts(
                CordaNotary.PROGRAM_ID,
                solanaConfig.config.jsonRpcUrl,
                HttpClient.newHttpClient(),
                CordaNotary.Accounts.NotaryAuthorization.DISCRIMINATOR,
                CordaNotary.Accounts.NotaryAuthorization::borshRead
        )

        val notariesByNetworkId = parsedNotaries.groupBy { it.networkId }

        val parsedNetworks = getProgramAnchorAccounts(
                CordaNotary.PROGRAM_ID,
                solanaConfig.config.jsonRpcUrl,
                HttpClient.newHttpClient(),
                CordaNotary.Accounts.Network.DISCRIMINATOR,
                CordaNotary.Accounts.Network::borshRead
        ).sortedBy { it.networkId }

        // Get all networks
        parsedNetworks.forEach { network ->
            println("Network ID: ${network.networkId}")
            if (notariesByNetworkId.containsKey(network.networkId)) {
                val notaries = notariesByNetworkId[network.networkId]!!
                notaries.forEachIndexed { index, pda ->
                    println("  ${index + 1}. Notary: ${pda.notary.base58()}")
                }
            } else {
                println("  No notaries registered")
            }
        }

        return ExitCodes.SUCCESS
    }
}


