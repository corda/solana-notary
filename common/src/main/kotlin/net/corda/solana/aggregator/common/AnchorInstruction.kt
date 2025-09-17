package net.corda.solana.aggregator.common

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.InstructionBuilderBase
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.api.TransactionBuilder
import com.lmax.solana4j.client.api.Blockhash
import com.lmax.solana4j.client.api.SolanaApi
import com.lmax.solana4j.client.api.TransactionResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AnchorInstruction : BorshSerialisable {
    /**
     * The transaction fee payer, which may or may not be of the [signers] mandated by the instruction.
     *
     * The IDL generated code will assign a default value from one of the instruction's signer (if there any), but this can be overridden.
     */
    abstract val txFeePayer: Signer?

    abstract val signers: List<Signer>

    abstract fun addToTx(builder: InstructionBuilderBase)

    protected fun addToTx(programId: PublicKey, discriminator: ByteArray, builder: InstructionBuilderBase) {
        builder.program(programId)
        builder.data(discriminator.size + borshSize) { buffer ->
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(discriminator)
            borshWrite(buffer)
        }
    }
}

data class AccountMeta(val pubkey: PublicKey, val isSigner: Boolean, val isWritable: Boolean)

/**
 * Add the given [AnchorInstruction] to the Solana transaction. Any required signers of the instruction will need to be added manually.
 */
fun TransactionBuilder.addAnchorInstruction(instruction: AnchorInstruction, remainingAccounts: List<AccountMeta> = emptyList()) {
    append { ixBuilder ->
        instruction.addToTx(ixBuilder)
        for (remainingAccount in remainingAccounts) {
            ixBuilder.account(remainingAccount.pubkey, remainingAccount.isSigner, remainingAccount.isWritable)
        }
    }
}

/**
 * Serialise the given [AnchorInstruction] into a Base64 encoded Solana transaction.
 */
fun serialiseToTransaction(
        instruction: AnchorInstruction,
        remainingAccounts: List<AccountMeta>,
        latestBlockhash: Blockhash,
        buffer: ByteBuffer = ByteBuffer.allocate(Solana.MAX_MESSAGE_SIZE)
): String {
    val txFeePayer = requireNotNull(instruction.txFeePayer) { "Instruction has not specified the transaction fee payer" }
    return serialiseToTransaction(
            { txBuilder -> txBuilder.addAnchorInstruction(instruction, remainingAccounts) },
            txFeePayer,
            instruction.signers,
            latestBlockhash,
            buffer
    )
}

/**
 * Sends a transaction for the given [AnchorInstruction] and then waits for commitment.
 *
 * @throws SolanaClientException If there was an issue with an RPC call.
 * @throws SolanaTransactionException If the transaction was rejected.
 * @throws SolanaTransactionExpiredException If the transaction was not processed and has expired.
 */
fun SolanaApi.sendAndConfirm(
    instruction: AnchorInstruction,
    remainingAccounts: List<AccountMeta> = emptyList(),
    rpcParams: DefaultRpcParams = DefaultRpcParams(),
    buffer: ByteBuffer = ByteBuffer.allocate(Solana.MAX_MESSAGE_SIZE),
    latestBlockhash: Blockhash = getLatestBlockhash(rpcParams).checkResponse("getLatestBlockhash")!!,
): TransactionResponse {
    val transactionBlob = serialiseToTransaction(instruction, remainingAccounts, latestBlockhash, buffer)
    return sendAndConfirm(transactionBlob, latestBlockhash.lastValidBlockHeight, rpcParams)
}
