package net.corda.solana.notary.common.rpc

import com.lmax.solana4j.client.api.Blockhash
import com.lmax.solana4j.client.api.SolanaClientResponse.SolanaClientError

open class SolanaException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}

class SolanaClientException(val method: String, val error: SolanaClientError) :
    SolanaException("$method RPC error: $error")

/**
 * @property error represents the Solana `solana_transaction_error::TransactionError` enum.
 */
class SolanaTransactionException(message: String, val error: Any, val logMessages: List<String>) :
    SolanaException(message)

/**
 * The transaction has expired, where its recent blockhash is no longer in the recent block window and thus no longer
 * valid to be processed. Users will need to sign a new transaction with a fresh recent blockhas if they wish to try
 * again.
 *
 * @see [Blockhash.getLastValidBlockHeight]
 */
class SolanaTransactionExpiredException(message: String) : SolanaException(message)
