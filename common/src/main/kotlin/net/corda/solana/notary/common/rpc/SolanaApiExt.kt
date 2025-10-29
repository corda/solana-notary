@file:JvmName("SolanaApiExt")

package net.corda.solana.notary.common.rpc

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.TransactionBuilder
import com.lmax.solana4j.client.api.Blockhash
import com.lmax.solana4j.client.api.SimulateTransactionResponse
import com.lmax.solana4j.client.api.SolanaApi
import com.lmax.solana4j.client.api.SolanaClientOptionalParams
import com.lmax.solana4j.client.api.SolanaClientResponse
import com.lmax.solana4j.client.api.TransactionResponse
import net.corda.solana.notary.common.AccountMeta
import net.corda.solana.notary.common.AnchorInstruction
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.serialiseToTransaction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

private const val SIMULATION_ERROR_CODE = -32002L

/**
 * Sends a transaction for the given [AnchorInstruction] and then waits for commitment.
 *
 * @throws SolanaClientException If there was an issue with an RPC call.
 * @throws SolanaTransactionException If the transaction was rejected.
 * @throws SolanaTransactionExpiredException If the transaction was not processed and has expired.
 */
@JvmOverloads
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

@JvmOverloads
fun SolanaApi.sendAndConfirm(
    instructions: (TransactionBuilder) -> Unit,
    txFeePayer: Signer,
    otherSigners: List<Signer> = emptyList(),
    rpcParams: DefaultRpcParams = DefaultRpcParams(),
    buffer: ByteBuffer = ByteBuffer.allocate(Solana.MAX_MESSAGE_SIZE),
    latestBlockhash: Blockhash = getLatestBlockhash(rpcParams).checkResponse("getLatestBlockhash")!!,
): TransactionResponse {
    val transactionBlob = serialiseToTransaction(instructions, txFeePayer, otherSigners, latestBlockhash, buffer)
    return sendAndConfirm(transactionBlob, latestBlockhash.lastValidBlockHeight, rpcParams)
}

/**
 * Sends the given transaction blob and then waits for commitment.
 *
 * @param lastValidBlockHeight The corresponding [Blockhash.getLastValidBlockHeight] to the
 * [Blockhash.getBlockhashBase58] used as the recent blockhash in the given transaction. This is used to determine when
 * the transaction has expired and will no longer be picked up by the network.
 *
 * @throws SolanaClientException If there was an issue with an RPC call.
 * @throws SolanaTransactionException If the transaction was rejected.
 * @throws SolanaTransactionExpiredException If the transaction was not processed and has expired.
 */
@JvmOverloads
fun SolanaApi.sendAndConfirm(
    transactionBlob: String,
    lastValidBlockHeight: Int,
    rpcParams: DefaultRpcParams = DefaultRpcParams(),
): TransactionResponse {
    val signature = sendTransactionAndCheck(transactionBlob, rpcParams)
    var sleepMillis = 100L
    while (true) {
        val txResponse = getTransaction(signature, rpcParams).checkResponse("getTransaction")
        if (txResponse != null) {
            val err = txResponse.metadata.err ?: return txResponse
            throw SolanaTransactionException("Transaction failed: $txResponse", err, txResponse.metadata.logMessages)
        }
        val blockHeight = getBlockHeight(rpcParams).checkResponse("getBlockHeight")!!
        // Keep polling getTransaction until the blockhash used in the transaction has expired. It's only until then can
        // the user safely submit a new transaction. If they try before then it's possible for both transactions to be
        // accepted by the network. See https://solana.com/developers/guides/advanced/retry#when-to-re-sign-transactions.
        if (blockHeight > lastValidBlockHeight) {
            throw SolanaTransactionExpiredException(
                "Transaction lastValidBlockHeight ($lastValidBlockHeight) is no longer valid against " +
                    "current block height ($blockHeight)"
            )
        }
        Thread.sleep(sleepMillis)
        sleepMillis = (sleepMillis * 1.1).toLong() // Increase the poll time to avoid spamming the node
    }
}

@JvmOverloads
fun SolanaApi.sendTransactionAndCheck(
    transactionBlob: String,
    rpcParams: SolanaClientOptionalParams = DefaultRpcParams(),
): String {
    val txResponse = sendTransaction(transactionBlob, rpcParams)
    if (txResponse.error == null) {
        return txResponse.response
    }
    if (txResponse.error.errorCode == SIMULATION_ERROR_CODE) {
        // If the error was in the simulation, then use `simulate` to throw SolanaTransactionException with the program
        // errors.
        try {
            simulate(transactionBlob, rpcParams)
        } catch (e: SolanaTransactionException) {
            throw e
        } catch (e: Exception) {
            // Throw the original sendTransaction error if we're unable to get the program errors.
            throw SolanaClientException("sendTransaction", txResponse.error).apply { addSuppressed(e) }
        }
        // If for some reason the simulation didn't return program errors, then fall-through and throw the original
        // sendTransaction error
    }
    throw SolanaClientException("sendTransaction", txResponse.error)
}

/**
 * Wrapper around [SolanaApi.simulateTransaction] with better error handling and default parameters.
 *
 * @throws SolanaTransactionException If the transaction was rejected.
 */
@JvmOverloads
fun SolanaApi.simulate(
    transactionBlob: String,
    rpcParams: SolanaClientOptionalParams = DefaultRpcParams(),
): SimulateTransactionResponse {
    val response = simulateTransaction(transactionBlob, rpcParams).checkResponse("simulateTransaction")!!
    if (response.err != null) {
        throw SolanaTransactionException("Simulated transaction failed: $response", response.err, response.logs)
    }
    return response
}

fun <T> SolanaClientResponse<T>.checkResponse(method: String): T? {
    if (error != null) {
        throw SolanaClientException(method, error)
    }
    return response
}

fun <T> SolanaApi.getAnchorAccount(
    address: String,
    discriminator: ByteArray,
    rpcParams: DefaultRpcParams,
    deserializer: (ByteBuffer) -> T,
): T {
    val accountInfo = getAccountInfo(address, rpcParams).checkResponse("getAccountInfo")!!
    val data = Base64.getDecoder().decode(accountInfo.data.accountInfoEncoded[0])
    val dataOffset = discriminator.size
    val dataLength = data.size - dataOffset
    return deserializer(ByteBuffer.wrap(data, dataOffset, dataLength).order(ByteOrder.LITTLE_ENDIAN))
}
