package net.corda.solana.notary.common.rpc

import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.MessageBuilderV0
import com.lmax.solana4j.api.TransactionBuilder
import com.lmax.solana4j.client.api.Blockhash
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.by
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.Base64

fun serialiseToTransaction(
    instructions: (TransactionBuilder) -> Unit,
    txFeePayer: Signer,
    otherSigners: Collection<Signer>,
    latestBlockhash: Blockhash,
    buffer: ByteBuffer = ByteBuffer.allocate(Solana.MAX_MESSAGE_SIZE),
): String {
    Solana
        .builder(buffer)
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
