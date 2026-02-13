package net.corda.solana.notary.testing

import com.r3.corda.lib.solana.core.AccountManagement.Companion.LAMPORTS_PER_SOL
import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.testing.ConfigureValidator
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import net.corda.solana.notary.client.CordaNotary.PROGRAM_ID
import net.corda.solana.notary.client.accounts.Administration
import net.corda.solana.notary.client.accounts.Network
import net.corda.solana.notary.client.accounts.NotaryAuthorization
import net.corda.solana.notary.client.instructions.AuthorizeNotary.authorizationPda
import net.corda.solana.notary.client.instructions.CreateNetwork.administrationPda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.rpc.json.http.client.SolanaRpcClient
import java.nio.file.Path

class SolanaNotaryExtensionTest {
    @ExtendWith(SolanaNotaryExtension::class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    class General {
        companion object {
            @TempDir
            lateinit var tempDir: Path

            private var captureSameNotaryPublicKey: PublicKey? = null

            @ConfigureValidator
            @JvmStatic
            fun configureValidator(builder: SolanaTestValidator.Builder) {
                builder.ledger(tempDir)
            }

            @AfterAll
            @JvmStatic
            fun `false-positive guard`() {
                assertThat(captureSameNotaryPublicKey).isNotNull
            }
        }

        @Test
        fun `SolanaClient is available`(client: SolanaClient, validator: SolanaTestValidator) {
            assertThat(client).isSameAs(validator.client())
        }

        @Test
        fun `@ConfigureValidator annotation`(validator: SolanaTestValidator) {
            assertThat(validator.ledger()).isEqualTo(tempDir)
        }

        @Order(1)
        @Test
        fun `notary program is already initialised and has zero networks by default`(client: SolanaClient) {
            assertThat(client.administration().nextNetworkId).isEqualTo(0)
            val networks = client.call(
                SolanaRpcClient::getProgramAccounts,
                PROGRAM_ID,
                listOf(Network.DISCRIMINATOR_FILTER)
            )
            assertThat(networks).isEmpty()
        }

        @Test
        fun `admin is available as PublicKey`(@Admin admin: PublicKey, client: SolanaClient) {
            assertThat(admin).isEqualTo(client.administration().admin)
            assertThat(client.call(SolanaRpcClient::getBalance, admin).lamports).isGreaterThan(LAMPORTS_PER_SOL)
        }

        @Test
        fun `admin is available as both Signer and FileSigner`(
            @Admin signer: Signer,
            @Admin fileSigner: FileSigner,
            client: SolanaClient,
        ) {
            assertThat(signer.publicKey()).isEqualTo(client.administration().admin)
            assertThat(signer).isSameAs(fileSigner)
        }

        @Test
        fun `NotaryEnvironment is available`(notaryEnvironment: NotaryEnvironment, @Admin admin: Signer) {
            assertThat(notaryEnvironment.admin()).isSameAs(admin)
        }

        @Test
        fun `specifying notary PublicKey authorises it by default on network 0`(
            @Notary notary: PublicKey,
            client: SolanaClient,
        ) {
            assertThat(client.authorization(notary).networkId).isEqualTo(0)
            assertThat(client.call(SolanaRpcClient::getBalance, notary).lamports).isGreaterThan(LAMPORTS_PER_SOL)
        }

        @Test
        fun `notary is available as both Signer and FileSigner`(
            @Notary signer: Signer,
            @Notary fileSigner: FileSigner,
            @Notary publicKey: PublicKey,
        ) {
            assertThat(signer.publicKey()).isEqualTo(publicKey)
            assertThat(signer).isSameAs(fileSigner)
        }

        @Test
        fun `specifying multiple notary PublicKeys on same network`(
            @Notary(0) notary0: PublicKey,
            @Notary(2) notary2: PublicKey,
            client: SolanaClient,
        ) {
            assertThat(client.authorization(notary0).networkId).isEqualTo(client.authorization(notary2).networkId)
            assertThat(notary0).isNotEqualTo(notary2)
        }

        @Test
        fun `specifying multiple notary PublicKeys on different networks`(
            @Notary(1, network = 1) notary1: PublicKey,
            @Notary(3, network = 3) notary3: PublicKey,
            client: SolanaClient,
        ) {
            assertThat(client.authorization(notary1).networkId).isEqualTo(1)
            assertThat(client.authorization(notary3).networkId).isEqualTo(3)
            assertThat(notary1).isNotEqualTo(notary3)
        }

        @RepeatedTest(2)
        fun `same Notary coordinates gives same PublicKey`(@Notary(1) publicKey: PublicKey) {
            if (captureSameNotaryPublicKey == null) {
                captureSameNotaryPublicKey = publicKey
            } else {
                assertThat(publicKey).isEqualTo(captureSameNotaryPublicKey)
            }
        }

        private fun SolanaClient.administration(): Administration {
            val adminPda = call(SolanaRpcClient::getAccountInfo, administrationPda().publicKey())
            return Administration.read(adminPda.data)
        }

        private fun SolanaClient.authorization(notary: PublicKey): NotaryAuthorization {
            val authorizationPda = call(SolanaRpcClient::getAccountInfo, authorizationPda(notary).publicKey())
            return NotaryAuthorization.read(authorizationPda.data)
        }
    }

    companion object {
        private var assertSameValidatorInstancePerTestValidator: SolanaTestValidator? = null
        private var assertDifferentValidatorInstancePerClassValidator: SolanaTestValidator? = null

        @AfterAll
        @JvmStatic
        fun `false-positive guard`() {
            assertThat(assertSameValidatorInstancePerTestValidator).isNotNull
            assertThat(assertDifferentValidatorInstancePerClassValidator).isNotNull
        }

        @AfterAll
        @JvmStatic
        fun `assert different validator instances per class`() {
            assertThat(assertSameValidatorInstancePerTestValidator)
                .isNotSameAs(assertDifferentValidatorInstancePerClassValidator)
        }
    }

    @Nested
    @ExtendWith(SolanaNotaryExtension::class)
    @DisplayName("assert same validator instance per test")
    inner class AssertSameValidatorInstancePerTest {
        @RepeatedTest(2)
        fun test(validator: SolanaTestValidator) {
            if (assertSameValidatorInstancePerTestValidator == null) {
                assertSameValidatorInstancePerTestValidator = validator
            } else {
                assertThat(validator).isSameAs(assertSameValidatorInstancePerTestValidator)
            }
        }
    }

    @Nested
    @ExtendWith(SolanaNotaryExtension::class)
    @DisplayName("assert different validator instance per class")
    inner class AssertDifferentValidatorInstancePerClass {
        @Test
        fun captureInstance(validator: SolanaTestValidator) {
            assertDifferentValidatorInstancePerClassValidator = validator
        }
    }
}
