package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.admincli.Store
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.client.accounts.Network
import net.corda.solana.notary.client.accounts.NotaryAuthorization
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import software.sava.idl.clients.squads.v4.gen.types.Multisig
import software.sava.idl.clients.squads.v4.gen.types.Permission
import software.sava.idl.clients.squads.v4.gen.types.Permissions
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.lang.Thread.startVirtualThread
import java.net.http.HttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor

@Command(
    name = "info",
    description = ["Prints out information on the notary program"],
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
)
class InfoCommand : Runnable {
    @Mixin
    private val rpcConfig = RpcConfig()

    override fun run() {
        println("Program ID: $PROGRAM_ID")

        val administration = rpcConfig.getOptionalAdministration()
        if (administration == null) {
            println("Notary program has not been initialized")
            return
        }

        val admin = administration.admin

        val httpClient = HttpClient.newBuilder().executor(newVirtualThreadPerTaskExecutor()).build()

        val multisigFuture = async {
            val multisigPda = Store.getAdminMultisigPda(admin, rpcConfig.client, httpClient)
            if (multisigPda != null) {
                rpcConfig.client.call(SolanaRpcClient::getAccountInfo, multisigPda, Multisig.FACTORY).data
            } else {
                null
            }
        }

        val networksFuture = async {
            val networkIds = rpcConfig
                .client
                .call(SolanaRpcClient::getProgramAccounts, PROGRAM_ID, listOf(Network.DISCRIMINATOR_FILTER))
                .map { Network.read(it.data).networkId }

            val notariesByNetworkId = rpcConfig
                .client
                .call(SolanaRpcClient::getProgramAccounts, PROGRAM_ID, listOf(NotaryAuthorization.DISCRIMINATOR_FILTER))
                .map { NotaryAuthorization.read(it.data) }
                .groupBy { it.networkId }

            Pair(networkIds, notariesByNetworkId)
        }

        val multisig = multisigFuture.get()
        val voterCount = multisig?.members?.count { Permission.Vote in it.permissions }

        println("Admin: ${admin}${if (multisig != null) " (${multisig.threshold}-of-$voterCount multisig)" else ""}")
        println("Next available network ID: ${administration.nextNetworkId}")
        println()

        val (networkIds, notariesByNetworkId) = networksFuture.get()
        // Get all networks
        for (networkId in networkIds) {
            println("Network: $networkId")
            val notaries = notariesByNetworkId[networkId]
            if (notaries != null) {
                notaries.forEachIndexed { index, pda ->
                    println("  ${index + 1}. Notary: ${pda.notary}")
                }
            } else {
                println("  No notaries registered")
            }
        }
    }

    private fun <T> async(task: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        startVirtualThread {
            try {
                future.complete(task())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return future
    }

    private operator fun Permissions.contains(permission: Permission): Boolean {
        return mask and (1 shl permission.ordinal) != 0
    }
}
