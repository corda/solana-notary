package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.solana.notary.admincli.Utils.jsonMapper
import net.corda.solana.notary.admincli.Utils.getMaterializedTransaction
import net.corda.solana.notary.client.CordaNotary
import software.sava.core.accounts.PublicKey
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets.US_ASCII
import kotlin.jvm.optionals.getOrNull

object SquadsV4 {
    private val PROGRAM_ID: PublicKey = PublicKey.fromBase58Encoded("SQDS4ep65T869zMMBKyuUq6aD6EgTu8psMjkvj52pCf")
    private val SEED_PREFIX: ByteArray = "multisig".toByteArray(US_ASCII)
    private val SEED_VAULT: ByteArray = "vault".toByteArray(US_ASCII)

    fun HttpClient.isVaultPda(address: PublicKey): Boolean {
        val url = URI("https://4fnetmviidiqkjzenwxe66vgoa0soerr.lambda-url.us-east-1.on.aws/isSquad/$address")
        val response = send(HttpRequest.newBuilder(url).GET().build(), BodyHandlers.ofString()).body()
        val json = jsonMapper.readTree(response)
        return json["version"]?.stringValue()?.lowercase() == "v4"
    }

    fun findMultisigPda(vaultPda: PublicKey, solanaClient: SolanaClient, httpClient: HttpClient): PublicKey? {
        if (solanaClient.rpcUrl.host.endsWith(".helius-rpc.com")) {
            val heliusClient = HeliusClient.fromRpcUrl(httpClient, solanaClient.rpcUrl)
            return heliusClient.getEnhancedTransactions(vaultPda, "SQUADS_V4")
                .stream()
                .flatMap { it.instructions.stream() }
                .filter { ix -> ix.innerInstructions.any { it.programId == CordaNotary.PROGRAM_ID } }
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
                val tx = solanaClient.getMaterializedTransaction(txSig.signature)
                for (innerIxWrapper in tx.meta.innerInstructions) {
                    val outerIx = tx.instructions[innerIxWrapper.index]
                    if (outerIx.programId().publicKey() == PROGRAM_ID &&
                        innerIxWrapper.instructions.any { tx.accounts[it.programIdIndex].publicKey() == CordaNotary.PROGRAM_ID }) {
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

    fun findVaultIndex(vaultPda: PublicKey, multisigPda: PublicKey): Int? {
        for (vaultIndex in 0..255) {
            val pda = PublicKey.findProgramAddress(
                listOf(SEED_PREFIX, multisigPda.toByteArray(), SEED_VAULT, byteArrayOf(vaultIndex.toByte())),
                PROGRAM_ID
            ).publicKey()
            if (pda == vaultPda) {
                return vaultIndex
            }
        }
        return null
    }
}
