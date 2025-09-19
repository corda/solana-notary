package net.corda.solana.aggregator.admincli

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.solana.aggregator.admincli.cli.*
import net.corda.solana.notary.admincli.ManifestVersionProvider
import net.corda.solana.notary.client.kotlin.CordaNotary
import picocli.CommandLine.Command

/**
 * Main CLI command class for Solana operations.
 */
@Command(versionProvider = ManifestVersionProvider::class)
class SolanaAggregatorCli : CordaCliWrapper(
    "solana-notary-admin",
    "CLI tool for admin operations on the Solana notary program (${CordaNotary.PROGRAM_ID.base58()})"
) {
    override fun additionalSubCommands() = setOf(
            InitializeCommand(),
            AuthorizeNotaryCommand(),
            RevokeNotaryCommand(),
            ListNotariesCommand(),
            CreateNetworkCommand(),
            ShowNextAvailableNetworkIdCommand()
    )

    override fun runProgram() = run()

    fun run(): Int {
        printHelp()
        return ExitCodes.FAILURE
    }
}

fun main(args: Array<String>) {
    SolanaAggregatorCli().start(args)
}
