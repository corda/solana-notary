package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.solana.notary.admincli.Utils.jsonMapperBuilder
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.lookup.AddressLookupTable
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.tx.Instruction
import software.sava.core.tx.TransactionSkeleton
import software.sava.rpc.json.http.client.SolanaRpcClient
import software.sava.rpc.json.http.response.TxMeta
import tools.jackson.databind.SerializationFeature
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull

object Store {
    private val jsonMapper = jsonMapperBuilder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()
    private val file = Path(System.getProperty("user.home")) / ".solana-notary-admin.json"

    @Volatile
    private var data: Data? = null

    init {
        if (file.exists()) {
            data = jsonMapper.readValue<Data>(file.toFile())
        }
    }

    fun getAdminMultisigPda(admin: PublicKey, solanaClient: SolanaClient, httpClient: HttpClient): PublicKey? {
        val data = this.data
        val data2 = if (data != null) {
            if (data.adminMultisig == null) {
                return null
            }
            if (data.adminMultisig.vaultPda == admin) {
                return data.adminMultisig.multisigPda
            }
            data
        } else {
            Data()
        }
        val multisigPda = if (isSquadVault(admin, httpClient)) {
            queryMultisigPda(admin, solanaClient, httpClient)
        } else {
            null
        }
        update(data2.copy(adminMultisig = multisigPda?.let { MultisigVault(admin, it) }))
        return multisigPda
    }

    private fun update(newData: Data) {
        data = newData
        jsonMapper.writeValue(file, newData)
    }

    private fun isSquadVault(address: PublicKey, httpClient: HttpClient): Boolean {
        val url = URI("https://4fnetmviidiqkjzenwxe66vgoa0soerr.lambda-url.us-east-1.on.aws/isSquad/$address")
        val response = httpClient.send(HttpRequest.newBuilder(url).GET().build(), BodyHandlers.ofString()).body()
        val json = jsonMapper.readTree(response)
        return json["isSquad"].booleanValue()
    }

    private fun queryMultisigPda(vaultPda: PublicKey, solanaClient: SolanaClient, httpClient: HttpClient): PublicKey? {
        if (solanaClient.rpcUrl.host.endsWith(".helius-rpc.com")) {
            val heliusClient = HeliusClient.fromRpcUrl(httpClient, solanaClient.rpcUrl)
            return heliusClient.getEnhancedTransactions(vaultPda, "SQUADS_V4")
                .stream()
                .flatMap { it.instructions.stream() }
                .filter { ix -> ix.innerInstructions.any { it.programId == PROGRAM_ID } }
                // This most likely to be a Squads VaultTransactionExecute instruction, which has the multisig as the
                // first account
                .map { it.accounts[0] }
                .distinct()
                .filter { multisigPda -> findVaultIndex(vaultPda, multisigPda) != null }
                .findAny()
                .getOrNull()
        } else {
            val txSigs = solanaClient.call(SolanaRpcClient::getSignaturesForAddress, vaultPda, 20)
            for (txSig in txSigs) {
                val tx = solanaClient.materialiseTransaction(txSig.signature)
                for (innerIxWrapper in tx.meta.innerInstructions) {
                    val outerIx = tx.instructions[innerIxWrapper.index]
                    if (outerIx.programId().publicKey() == SquadsV4.PROGRAM_ID &&
                        innerIxWrapper.instructions.any { tx.accounts[it.programIdIndex].publicKey() == PROGRAM_ID }) {
                        val multisigPda = outerIx.accounts()[0].publicKey()
                        if (findVaultIndex(vaultPda, multisigPda) != null) {
                            return multisigPda
                        }
                    }
                }
            }
            return null
        }
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

    private fun findVaultIndex(vaultPda: PublicKey, multisigPda: PublicKey): Int? {
        for (vaultIndex in 0..255) {
            val pda = PublicKey.findProgramAddress(
                listOf(
                    SquadsV4.SEED_PREFIX,
                    multisigPda.toByteArray(),
                    SquadsV4.SEED_VAULT,
                    byteArrayOf(vaultIndex.toByte())
                ),
                SquadsV4.PROGRAM_ID
            ).publicKey()
            if (pda == vaultPda) {
                return vaultIndex
            }
        }
        return null
    }

    private class MaterializedTx(
        val meta: TxMeta,
        val accounts: List<AccountMeta>,
        val instructions: List<Instruction>,
    )

    private data class Data(val adminMultisig: MultisigVault? = null)
    private data class MultisigVault(val vaultPda: PublicKey, val multisigPda: PublicKey)

    private object SquadsV4 {
        val PROGRAM_ID: PublicKey = PublicKey.fromBase58Encoded("SQDS4ep65T869zMMBKyuUq6aD6EgTu8psMjkvj52pCf")
        val SEED_PREFIX: ByteArray = "multisig".toByteArray(US_ASCII)
        val SEED_VAULT: ByteArray = "vault".toByteArray(US_ASCII)
    }
}
