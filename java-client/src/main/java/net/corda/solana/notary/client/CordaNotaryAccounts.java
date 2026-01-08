package net.corda.solana.notary.client;

import net.corda.solana.notary.client.anchor.types.TxId;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;

public interface CordaNotaryAccounts {
    PublicKey PROGRAM = PublicKey.fromBase58Encoded("notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY");

    AccountMeta INVOKED_PROGRAM = AccountMeta.createInvoked(PROGRAM);

    static ProgramDerivedAddress administrationPda() {
        return PublicKey.findProgramAddress(List.of("admin".getBytes(US_ASCII)), PROGRAM);
    }

    static ProgramDerivedAddress authorizationPda(PublicKey notaryAccount) {
        return PublicKey.findProgramAddress(
            List.of(
                "notary_authorization".getBytes(US_ASCII),
                notaryAccount.toByteArray()
            ),
            PROGRAM
        );
    }

    static ProgramDerivedAddress networkPda(short networkId) {
        return PublicKey.findProgramAddress(
            List.of(
                "network_account".getBytes(US_ASCII),
                ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(networkId).array()
            ),
            PROGRAM
        );
    }

    static ProgramDerivedAddress cordaTxPda(TxId txId, short networkId) {
        return PublicKey.findProgramAddress(
            List.of(
                "corda_tx".getBytes(US_ASCII),
                txId._array(),
                ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(networkId).array()
            ),
            PROGRAM
        );
    }
}
