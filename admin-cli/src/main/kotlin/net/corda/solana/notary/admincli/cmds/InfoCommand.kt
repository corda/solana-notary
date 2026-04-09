package net.corda.solana.notary.admincli.cmds

import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.client.accounts.Network
import net.corda.solana.notary.client.accounts.NotaryAuthorization
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.lang.Thread.startVirtualThread
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

        val administration = rpcConfig.getAdministration()
        if (administration == null) {
            println("Notary program has not been initialized")
            return
        }

        val admin = administration.admin

        val httpClient = HttpClient.newBuilder().executor(newVirtualThreadPerTaskExecutor()).build()

        val isSquadsFuture = httpClient
            .sendAsync(
                httpGet("https://4fnetmviidiqkjzenwxe66vgoa0soerr.lambda-url.us-east-1.on.aws/isSquad/$admin"),
                HttpResponse.BodyHandlers.ofString()
            )
            .thenApply { it.body().contains("\"isSquad\":true") }

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

        println("Admin: ${admin}${if (isSquadsFuture.get()) " (multisig)" else ""}")
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

    private fun httpGet(url: String) = HttpRequest.newBuilder(URI.create(url)).GET().build()

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
}
