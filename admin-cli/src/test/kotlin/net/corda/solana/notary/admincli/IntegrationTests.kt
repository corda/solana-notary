package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.core.SolanaTransactionException
import com.r3.corda.lib.solana.core.SolanaUtils
import com.r3.corda.lib.solana.testing.ConfigureValidator
import com.r3.corda.lib.solana.testing.SolanaTestClass
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.client.instructions.Commit
import net.corda.solana.notary.client.types.FlaggedU8
import net.corda.solana.notary.client.types.StateRefGroup
import net.corda.solana.notary.client.types.TxId
import net.corda.solana.notary.testing.NotaryEnvironment
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.encoding.ByteUtil
import software.sava.core.tx.TransactionSkeleton.deserializeSkeleton
import java.nio.file.Path
import kotlin.experimental.or
import kotlin.io.path.absolutePathString
import kotlin.random.Random

// This can't use SolanaNotaryExtension as we are testing the CLI can initialise a newly deployed program
@SolanaTestClass
class IntegrationTests {
    companion object {
        private const val REFERENCE_MASK = 0b1000_0000.toByte()

        private lateinit var testValidator: SolanaTestValidator
        private lateinit var admin: FileSigner

        @ConfigureValidator
        @JvmStatic
        fun configureValidator(builder: SolanaTestValidator.Builder) {
            NotaryEnvironment.addNotaryProgram(builder)
        }

        @BeforeAll
        @JvmStatic
        fun beforeAll(testValidator: SolanaTestValidator, @TempDir tempDir: Path) {
            this.testValidator = testValidator
            admin = FileSigner.random(tempDir)
            fundAccount(admin)
        }

        private fun fundAccount(signer: Signer = SolanaUtils.randomSigner()): Signer {
            testValidator.accounts().airdropSol(signer.publicKey(), 10)
            return signer
        }

        @JvmStatic
        fun nullableEncoding(): List<Encoding?> = Encoding.entries + null
    }

    @AfterEach
    fun refreshBlockhash(client: SolanaClient) {
        client.getBlockhashInfo(forceFetch = true)
    }

    @Test
    fun version() {
        val output = exec("--version")
        assertThat(output).contains(CordaNotary.PROGRAM_ID.toBase58())
        assertThat(output).contains(System.getProperty("gradle.test.version"))
    }

    @MethodSource("nullableEncoding")
    @ParameterizedTest
    fun `e2e initilisation, adding and removing notaries`(encoding: Encoding?, testInfo: TestInfo) {
        val isFirstRun = testInfo.displayName.startsWith("[1]")

        val notary1 = fundAccount()
        val notary2 = fundAccount()

        if (isFirstRun) {
            // Hacky way to get around the fact the notary program will already have been initialised by the first
            // parameterised run.
            assertThat(execCmd("info", null)).isEqualTo(
                "Program ID: ${CordaNotary.PROGRAM_ID}\nNotary program has not been initialized"
            )
            execCmd("initialize", encoding)
            assertThat(execCmd("info", null)).isEqualTo(
                "Program ID: ${CordaNotary.PROGRAM_ID}\nAdmin: ${admin.publicKey()}\nNext available network ID: 0"
            )
        }

        // Create two Corda networks, with IDs 0 and 1 respectively
        execCmd("create-network", encoding)
        execCmd("create-network", encoding)

        execCmd("authorize", encoding, notary1.publicKey(), 0)
        commitRandomInputState(notary1, networkId = 0)

        // Try to commit from an unauthorised notary on a valid network
        assertThatThrownBy { commitRandomInputState(notary2, networkId = 0) }
            .isInstanceOf(SolanaTransactionException::class.java)
            .hasMessageContaining("Error Code: AccountNotInitialized")

        execCmd("authorize", encoding, notary2.publicKey(), 1)
        commitRandomInputState(notary2, networkId = 1)

        execCmd("revoke", encoding, notary2.publicKey())
        assertThatThrownBy { commitRandomInputState(notary2, networkId = 1) }
            .isInstanceOf(SolanaTransactionException::class.java)
            .hasMessageContaining("Error Code: AccountNotInitialized")
    }

    private fun exec(vararg furtherArgs: String): String {
        val args = ArrayList<String>()
        val binary = System.getProperty("gradle.test.bin")
        if (binary.endsWith(".jar")) {
            args += "${System.getProperty("java.home")}/bin/java"
            args += "-jar"
        }
        args += binary
        args.addAll(furtherArgs)
        println(args.joinToString(" "))
        val process = ProcessBuilder(args).start()
        val exitCode = process.waitFor()
        val output = process.inputReader().readText()
        println("STDOUT> $output")
        check(exitCode == 0) { "cli failed with error code $exitCode: ${process.errorReader().readText()}" }
        return output.trimEnd()
    }

    private fun execCmd(cmd: String, encoding: Encoding?, vararg cmdArgs: Any): String {
        val args = arrayListOf(cmd)
        args += "--rpc"
        args += testValidator.rpcUrl().toString()
        args += "--ws"
        args += testValidator.websocketUrl().toString()
        args += "-c"
        args += "CONFIRMED"
        if (encoding != null) {
            args += "--encoding"
            args += encoding.toString()
            if (cmd == "initialize") {
                args += "--admin-address"
                args += admin.publicKey().toString()
            }
        } else {
            if (cmd != "info") {
                args += "-k"
                args += admin.file.absolutePathString()
            }
        }
        cmdArgs.mapTo(args) { it.toString() }
        val output = exec(*args.toTypedArray())
        if (encoding != null) {
            // Manually send the transaction
            testValidator.client().sendAndConfirm(
                { deserializeSkeleton(encoding.decode(output)).createTransaction() },
                admin
            )
        }
        return output
    }

    private fun commitRandomInputState(notary: Signer, networkId: Short) {
        val consumingTxId = TxId(Random.nextBytes(32))
        val inputStateTxId = TxId(Random.nextBytes(32))
        testValidator.client().sendAndConfirm(
            {
                it.createTransaction(
                    Commit.instruction(
                        consumingTxId,
                        listOf(StateRefGroup(inputStateTxId, listOf(1.toFlaggedU8(false)))),
                        notary.publicKey()
                    ).extraAccounts(
                        listOf(
                            cordaTxPda(consumingTxId, networkId),
                            cordaTxPda(inputStateTxId, networkId)
                        )
                    )
                )
            },
            notary
        )
    }

    private fun cordaTxPda(txId: TxId, networkId: Short): AccountMeta {
        val networkIdBytes = ByteArray(Short.SIZE_BYTES).also { ByteUtil.putInt16LE(it, 0, networkId) }
        val derivedAddress = PublicKey.findProgramAddress(
            listOf("corda_tx".toByteArray(), txId.bytes, networkIdBytes),
            CordaNotary.PROGRAM_ID
        )
        return AccountMeta.createMeta(derivedAddress.publicKey(), true, false)
    }

    private fun Int.toFlaggedU8(isReference: Boolean): FlaggedU8 {
        val byte = if (isReference) toByte() or REFERENCE_MASK else toByte()
        return FlaggedU8(byte)
    }
}
