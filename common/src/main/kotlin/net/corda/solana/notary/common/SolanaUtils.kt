package net.corda.solana.notary.common

import software.sava.core.accounts.Signer

object SolanaUtils {
    @JvmStatic
    fun randomSigner(): Signer = Signer.createFromPrivateKey(Signer.generatePrivateKeyBytes())
}
