package net.corda.solana.notary.admincli

import net.corda.solana.notary.client.CordaNotary
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class IntegrationTests {
    @Test
    fun version() {
        val output = exec("--version")
        assertThat(output).contains(CordaNotary.PROGRAM_ID.base58())
        assertThat(output).contains(System.getProperty("gradle.test.version"))
    }

    private fun exec(flag: String): String {
        val process = ProcessBuilder(
            "${System.getProperty("java.home")}/bin/java",
            "-jar",
            System.getProperty("gradle.test.shadowjar"),
            flag
        ).start()
        val exitCode = process.waitFor()
        val output = process.inputReader().readText()
        if (exitCode != 0) {
            fail<Unit>("cli failed with exit code $exitCode: $output")
        }
        return output
    }
}
