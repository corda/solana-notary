package net.corda.solana.notary.admincli

import com.lmax.solana4j.Solana
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import net.corda.solana.notary.test.SolanaTestValidator
import net.corda.solana.notary.client.CordaNotary
import net.corda.solana.notary.client.CordaNotary.Types.FlaggedU8
import net.corda.solana.notary.client.CordaNotary.Types.StateRefGroup
import net.corda.solana.notary.client.CordaNotary.Types.TxId
import net.corda.solana.notary.common.AccountMeta
import net.corda.solana.notary.common.Signer
import net.corda.solana.notary.common.rpc.SolanaTransactionException
import net.corda.solana.notary.common.rpc.checkResponse
import net.corda.solana.notary.common.rpc.sendAndConfirm
import net.corda.solana.notary.test.TestingSupport
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.experimental.or
import kotlin.random.Random

class IntegrationTests {
    companion object {
        private const val REFERENCE_MASK = 0b1000_0000.toByte()

        private lateinit var testValidator: SolanaTestValidator
        private lateinit var rpcClient: SolanaJsonRpcClient

        private lateinit var admin: Signer
        private lateinit var adminFile: Path

        @BeforeAll
        @JvmStatic
        fun startTestValidator(@TempDir ledger: Path) {
            val builder = SolanaTestValidator
                .builder()
                .quiet()
                .ledger(ledger)
                .dynamicPorts()
            TestingSupport.addNotaryProgram(builder)
            testValidator = builder.start().waitForReadiness()
            rpcClient = testValidator.connectRpcClient()
            admin = fundNewAccount()
            adminFile = TestingSupport.writeToDir(admin, ledger)
        }

        @AfterAll
        @JvmStatic
        fun close() {
            if (::testValidator.isInitialized) {
                testValidator.close()
            }
        }

        private fun fundNewAccount(): Signer {
            val signer = Signer.random()
            rpcClient.requestAirdrop(signer.account.base58(), 1_000_000_000).checkResponse("requestAirdrop")
            return signer
        }
    }

    @Test
    fun version() {
        val output = exec("--version")
        assertThat(output).contains(CordaNotary.PROGRAM_ID.base58())
        assertThat(output).contains(System.getProperty("gradle.test.version"))
    }

    @Test
    fun `e2e initilisation, adding and removing notaries`() {
        val notary1 = fundNewAccount()
        val notary2 = fundNewAccount()

        execCmd("initialize")

        // Create two Corda networks, with IDs 0 and 1 respectively
        execCmd("create-network")
        execCmd("create-network")

        execCmd("authorize", "-a", notary1.account.base58(), "-n", "0")
        commitRandomInputState(notary1, networkId = 0)

        // Try to commit from an unauthorised notary on a valid network
        assertThatThrownBy { commitRandomInputState(notary2, networkId = 0) }
            .isInstanceOf(SolanaTransactionException::class.java)
            .hasMessageContaining("Error Code: AccountNotInitialized")

        execCmd("authorize", "-a", notary2.account.base58(), "-n", "1")
        commitRandomInputState(notary2, networkId = 1)

        execCmd("revoke", "-a", notary2.account.base58())
        assertThatThrownBy { commitRandomInputState(notary2, networkId = 1) }
            .isInstanceOf(SolanaTransactionException::class.java)
            .hasMessageContaining("Error Code: AccountNotInitialized")
    }

    private fun exec(vararg furtherArgs: String): String {
        val args = arrayListOf(
            "${System.getProperty("java.home")}/bin/java",
            "-jar",
            System.getProperty("gradle.test.shadowjar")
        )
        args.addAll(furtherArgs)
        val process = ProcessBuilder(args).start()
        val exitCode = process.waitFor()
        val output = process.inputReader().readText()
        println("STDOUT> $output")
        if (exitCode != 0) {
            println("STDERR> ${process.errorReader().readText()}")
            println()
            fail<Unit> { "Cli failed with error code $exitCode" }
        }
        return output
    }

    private fun execCmd(cmd: String, vararg cmdArgs: Any): String {
        val args = arrayListOf(cmd)
        args.addAll(listOf("-k", adminFile.toString(), "-u", testValidator.rpcEndpoint.toString(), "-c", "CONFIRMED"))
        cmdArgs.mapTo(args) { it.toString() }
        return exec(*args.toTypedArray())
    }

    private fun commitRandomInputState(notary: Signer, networkId: Short) {
        val consumingTxId = TxId(Random.nextBytes(32))
        val inputStateTxId = TxId(Random.nextBytes(32))
        rpcClient.sendAndConfirm(
            CordaNotary.Commit(
                consumingTxId,
                listOf(StateRefGroup(inputStateTxId, listOf(1.toFlaggedU8(false)))),
                notary,
                txFeePayer = notary
            ),
            remainingAccounts = listOf(
                cordaTxPda(consumingTxId, networkId),
                cordaTxPda(inputStateTxId, networkId)
            )
        )
    }

    private fun cordaTxPda(txId: TxId, networkId: Short): AccountMeta {
        val networkIdBytes = ByteBuffer
            .allocate(Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(networkId)
            .array()
        val derivedAddress = Solana.programDerivedAddress(
            listOf("corda_tx".toByteArray(), txId.bytes, networkIdBytes),
            CordaNotary.PROGRAM_ID
        )
        return AccountMeta(derivedAddress.address(), isSigner = false, isWritable = true)
    }

    private fun Int.toFlaggedU8(isReference: Boolean): FlaggedU8 {
        val byte = if (isReference) toByte() or REFERENCE_MASK else toByte()
        return FlaggedU8(byte)
    }
}
