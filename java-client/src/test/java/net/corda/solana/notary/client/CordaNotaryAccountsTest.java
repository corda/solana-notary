package net.corda.solana.notary.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CordaNotaryAccountsTest {
    @Test
    public void PROGRAM_ID() {
        assertThat(CordaNotaryAccounts.PROGRAM.toBase58()).isEqualTo("notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY");
    }
}
