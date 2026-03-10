package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.FileSigner
import picocli.CommandLine.Option
import software.sava.core.accounts.PublicKey
import software.sava.core.tx.Instruction
import software.sava.core.tx.Transaction

class SigningConfig {
    @Option(
        names = ["--keypair", "-k"],
        description = [
            "Keypair file of the admin authority.",
            "Cannot be used with --encoding."
        ],
    )
    var keypair: FileSigner? = null

    @Option(
        names = ["--encoding"],
        description = [
            $$"Encode the transaction so that it can be sent separately. Options: ${COMPLETION-CANDIDATES}",
            "This is useful if the admin key is not immediately available or if it is a multisig wallet.",
            "Cannot be used with --keypair.",
        ],
    )
    var encoding: Encoding? = null

    fun action(rpcConfig: RpcConfig, createInstruction: (PublicKey) -> Instruction): Boolean {
        val admin = rpcConfig.getRequiredAdministration().admin
        return action(admin, createInstruction(admin), rpcConfig)
    }

    fun action(admin: PublicKey, instruction: Instruction, rpcConfig: RpcConfig): Boolean {
        require((keypair != null) != (encoding != null)) {
            "Either --keypair or --encoding must be specified, but not both"
        }
        return if (encoding != null) {
            val transaction = Transaction.createTx(admin, instruction)
            println(encoding!!.encode(transaction.serialized()))
            false
        } else {
            require(admin == keypair!!.publicKey()) {
                "Signing keypair (${keypair!!.publicKey()}) and admin ($admin) do not match"
            }
            rpcConfig.client.sendAndConfirm({ it.createTransaction(instruction) }, keypair!!)
            true
        }
    }
}
