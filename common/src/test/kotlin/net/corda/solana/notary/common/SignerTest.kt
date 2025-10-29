package net.corda.solana.notary.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.div

class SignerTest {
    @Test
    fun `write to file`(@TempDir tempDir: Path) {
        val original = Signer.random()
        val file = tempDir / "signer.json"
        (original.byteBufferSigner as PrivateKeyByteBufferSigner).writeToFile(file)
        val copy = Signer.fromFile(file)
        assertThat(copy.account).isEqualTo(copy.account)
    }
}
