package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.SolanaClient
import net.corda.solana.notary.admincli.SquadsV4.isVaultPda
import net.corda.solana.notary.admincli.Utils.jsonMapper
import software.sava.core.accounts.PublicKey
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpClient
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

object Store {
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
        val multisigPda = if (httpClient.isVaultPda(admin)) {
            SquadsV4.findMultisigPda(admin, solanaClient, httpClient)
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

    private data class Data(val adminMultisig: MultisigVault? = null)
    private data class MultisigVault(val vaultPda: PublicKey, val multisigPda: PublicKey)
}
