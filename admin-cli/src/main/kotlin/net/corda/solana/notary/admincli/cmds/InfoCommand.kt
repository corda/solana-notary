package net.corda.solana.notary.admincli.cmds

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.solana.notary.admincli.HeliusClient
import net.corda.solana.notary.admincli.RpcConfig
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.client.accounts.Network
import net.corda.solana.notary.client.accounts.NotaryAuthorization
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.lookup.AddressLookupTable
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.tx.Instruction
import software.sava.core.tx.TransactionSkeleton
import software.sava.idl.clients.squads.v4.gen.types.Multisig
import software.sava.idl.clients.squads.v4.gen.types.Permission
import software.sava.idl.clients.squads.v4.gen.types.Permissions
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.response.TxMeta
import java.lang.Thread.startVirtualThread
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

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

        val multisigFuture = httpClient
            .sendAsync(
                httpGet("https://4fnetmviidiqkjzenwxe66vgoa0soerr.lambda-url.us-east-1.on.aws/isSquad/$admin"),
                HttpResponse.BodyHandlers.ofString()
            )
            .thenApply {
                val isSquad = it.body().contains("\"isSquad\":true")
                if (isSquad) getMultisig(admin, httpClient) else null
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

    private fun getMultisig(vaultPda: PublicKey, httpClient: HttpClient): Multisig? {
        val client = rpcConfig.client

        if (client.rpcUrl.host.endsWith(".helius-rpc.com")) {
            val heliusClient = HeliusClient.fromRpcUrl(httpClient, client.rpcUrl)
            return heliusClient.getEnhancedTransactions(vaultPda, "SQUADS_V4")
                .stream()
                .flatMap { it.instructions.stream() }
                .filter { ix -> ix.innerInstructions.any { it.programId == PROGRAM_ID } }
                // This most likely to be a Squads VaultTransactionExecute instruction, which has the multisig as the
                // first account
                .map { it.accounts[0] }
                .distinct()
                .filter { multisigPda -> findVaultIndex(vaultPda, multisigPda) != null }
                .map { multisigPda -> client.call(SolanaRpcClient::getAccountInfo, multisigPda, Multisig.FACTORY).data }
                .findAny()
                .getOrNull()
        } else {
            val txSigs = client.call(SolanaRpcClient::getSignaturesForAddress, vaultPda, 20)
            for (txSig in txSigs) {
                val tx = client.materialiseTransaction(txSig.signature)
                for (innerIxWrapper in tx.meta.innerInstructions) {
                    val outerIx = tx.instructions[innerIxWrapper.index]
                    if (outerIx.programId().publicKey() == SQUADS_V4_PROGRAM_ID &&
                        innerIxWrapper.instructions.any { tx.accounts[it.programIdIndex].publicKey() == PROGRAM_ID }) {
                        val multisigPda = outerIx.accounts()[0].publicKey()
                        if (findVaultIndex(vaultPda, multisigPda) != null) {
                            return client.call(SolanaRpcClient::getAccountInfo, multisigPda, Multisig.FACTORY).data
                        }
                    }
                }
            }
            return null
        }
    }

    private fun findVaultIndex(vaultPda: PublicKey, multisigPda: PublicKey): Int? {
        for (vaultIndex in 0..255) {
            val pda = PublicKey.findProgramAddress(
                listOf(SEED_PREFIX, multisigPda.toByteArray(), SEED_VAULT, byteArrayOf(vaultIndex.toByte())),
                SQUADS_V4_PROGRAM_ID
            ).publicKey()
            if (pda == vaultPda) {
                return vaultIndex
            }
        }
        return null
    }

    private fun SolanaClient.materialiseTransaction(signature: String): MaterializedTx {
        val tx = call(SolanaRpcClient::getTransaction, signature)
        val skeleton = TransactionSkeleton.deserializeSkeleton(tx.data)
        val accounts = if (skeleton.isLegacy) {
            skeleton.parseAccounts()
        } else {
            val lookupTableAccounts = skeleton.lookupTableAccounts().asList()
            val lookupTables = if (lookupTableAccounts.isEmpty()) {
                Stream.empty()
            } else {
                call(SolanaRpcClient::getMultipleAccounts, lookupTableAccounts, AddressLookupTable.FACTORY)
                    .stream()
                    .map { it.data }
            }
            skeleton.parseAccounts(lookupTables)
        }
        val instructions = skeleton.parseInstructions(accounts)
        return MaterializedTx(tx.meta, accounts.asList(), instructions.asList())
    }

    private operator fun Permissions.contains(permission: Permission): Boolean {
        return mask and (1 shl permission.ordinal) != 0
    }

    private class MaterializedTx(
        val meta: TxMeta,
        val accounts: List<AccountMeta>,
        val instructions: List<Instruction>,
    )

    private companion object {
        private val SQUADS_V4_PROGRAM_ID = PublicKey.fromBase58Encoded("SQDS4ep65T869zMMBKyuUq6aD6EgTu8psMjkvj52pCf")
        private val SEED_PREFIX = "multisig".toByteArray(US_ASCII)
        private val SEED_VAULT = "vault".toByteArray(US_ASCII)
    }
}
