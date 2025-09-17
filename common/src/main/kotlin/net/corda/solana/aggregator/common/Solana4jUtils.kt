@file:Suppress("MagicNumber")

package net.corda.solana.aggregator.common

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.MessageBuilderV0
import com.lmax.solana4j.api.TransactionBuilder
import com.lmax.solana4j.client.api.*
import com.lmax.solana4j.client.api.SolanaClientResponse.SolanaClientError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.*

private const val SIMULATION_ERROR_CODE = -32002L

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
 * @param lastValidBlockHeight The corresponding [Blockhash.getLastValidBlockHeight] to the [Blockhash.getBlockhashBase58] used as the
 * recent blockhash in the given transaction. This is used to determine when the transaction has expired and will no longer be picked up by
 * the network.
 *
 * @throws SolanaClientException If there was an issue with an RPC call.
 * @throws SolanaTransactionException If the transaction was rejected.
 * @throws SolanaTransactionExpiredException If the transaction was not processed and has expired.
 */
fun SolanaApi.sendAndConfirm(
        transactionBlob: String,
        lastValidBlockHeight: Int,
        rpcParams: DefaultRpcParams = DefaultRpcParams()
): TransactionResponse {
    val signature = sendTransactionAndCheck(transactionBlob, rpcParams)
    var sleepMillis = 100L
    while (true) {
        val txResponse = getTransaction(signature, rpcParams).checkResponse("getTransaction")
        if (txResponse != null) {
            val err = txResponse.metadata.err
            if (err != null) {
                throw SolanaTransactionException("Transaction failed: $txResponse", err, txResponse.metadata.logMessages)
            }
            return txResponse
        }
        val blockHeight = getBlockHeight(rpcParams).checkResponse("getBlockHeight")!!
        // Keep polling getTransaction until the blockhash used in the transaction has expired. It's only until then can the user safely
        // submit a new transaction. If they try before then it's possible for both transactions to be accepted by the network.
        // See https://solana.com/developers/guides/advanced/retry#when-to-re-sign-transactions.
        if (blockHeight > lastValidBlockHeight) {
            throw SolanaTransactionExpiredException("Transaction lastValidBlockHeight ($lastValidBlockHeight) is no longer valid against " +
                    "current block height ($blockHeight)")
        }
        Thread.sleep(sleepMillis)
        sleepMillis = (sleepMillis * 1.1).toLong() // Increase the poll time to avoid spamming the node
    }
}

fun serialiseToTransaction(
        instructions: (TransactionBuilder) -> Unit,
        txFeePayer: Signer,
        otherSigners: Collection<Signer>,
        latestBlockhash: Blockhash,
        buffer: ByteBuffer = ByteBuffer.allocate(Solana.MAX_MESSAGE_SIZE)
): String {
    Solana.builder(buffer)
            .v0()
            .recent(Solana.blockhash(latestBlockhash.blockhashBase58))
            .setInstructionsAndSign(instructions, txFeePayer, otherSigners)
    return US_ASCII.decode(Base64.getEncoder().encode(buffer)).toString()
}

fun MessageBuilderV0.setInstructionsAndSign(
        instructions: (TransactionBuilder) -> Unit,
        txFeePayer: Signer,
        otherSigners: Collection<Signer>,
) {
    instructions(instructions)
            .payer(txFeePayer.account)
            .seal()
            .signed()
            // The `by` implementation de-duplicates the signers, so it's OK if the same one is added multiple times
            .by(txFeePayer)
            .also { builder -> otherSigners.forEach(builder::by) }
            .build()
}

fun SolanaApi.sendTransactionAndCheck(transactionBlob: String, rpcParams: SolanaClientOptionalParams = DefaultRpcParams()): String {
    val txResponse = sendTransaction(transactionBlob, rpcParams)
    if (txResponse.error == null) {
        return txResponse.response
    }
    if (txResponse.error.errorCode == SIMULATION_ERROR_CODE) {
        // If the error was in the simulation, then use `simulate` to throw SolanaTransactionException with the program errors.
        try {
            simulate(transactionBlob, rpcParams)
        } catch (e: SolanaTransactionException) {
            throw e
        } catch (e: Exception) {
            // Throw the original sendTransaction error if we're unable to get the program errors.
            throw SolanaClientException("sendTransaction", txResponse.error).apply { addSuppressed(e) }
        }
        // If for some reason the simulation didn't return program errors, then fall-through and throw the original sendTransaction error
    }
    throw SolanaClientException("sendTransaction", txResponse.error)
}

/**
 * Wrapper around [SolanaApi.simulateTransaction] with better error handling and default parameters.
 *
 * @throws SolanaTransactionException If the transaction was rejected.
 */
fun SolanaApi.simulate(transactionBlob: String, rpcParams: SolanaClientOptionalParams = DefaultRpcParams()): SimulateTransactionResponse {
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

fun <T> SolanaApi.getAnchorAccount(address: String, discriminator: ByteArray, rpcParams: DefaultRpcParams, deserializer: (ByteBuffer) -> T): T {
    val accountInfo = getAccountInfo(address, rpcParams).checkResponse("getAccountInfo")!!
    val data = Base64.getDecoder().decode(accountInfo.data.accountInfoEncoded[0])
    val dataOffset = discriminator.size
    val dataLength = data.size - dataOffset
    return deserializer(ByteBuffer.wrap(data, dataOffset, dataLength).order(ByteOrder.LITTLE_ENDIAN))
}

open class SolanaException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}

class SolanaClientException(val method: String, val error: SolanaClientError) : SolanaException("$method RPC error: $error")

/**
 * @property error represents the Solana `solana_transaction_error::TransactionError` enum.
 */
class SolanaTransactionException(message: String, val error: Any, val logMessages: List<String>) : SolanaException(message)

/**
 * The transaction has expired, where its recent blockhash is no longer in the recent block window and thus no longer valid to be processed.
 * Users will need to sign a new transaction with a fresh recent blockhas if they wish to try again.
 *
 * @see [Blockhash.getLastValidBlockHeight]
 */
class SolanaTransactionExpiredException(message: String) : SolanaException(message)

