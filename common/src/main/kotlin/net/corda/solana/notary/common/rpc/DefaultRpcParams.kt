package net.corda.solana.notary.common.rpc

import com.lmax.solana4j.client.api.Commitment
import com.lmax.solana4j.client.api.SolanaClientOptionalParams

data class DefaultRpcParams(
    val commitment: Commitment = Commitment.CONFIRMED,
    val skipPreflight: Boolean = false,
) : SolanaClientOptionalParams {
    private val params = mapOf(
            "encoding" to "base64",
            "maxSupportedTransactionVersion" to 0,
            "commitment" to commitment.name.lowercase(),
            "preflightCommitment" to commitment.name.lowercase(),
            "skipPreflight" to skipPreflight,
    )

    override fun addParam(key: String, value: Any): Unit = throw UnsupportedOperationException()

    override fun getParams(): Map<String, Any> = params
}
