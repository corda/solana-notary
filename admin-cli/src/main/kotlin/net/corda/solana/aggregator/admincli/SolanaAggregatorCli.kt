package net.corda.solana.aggregator.admincli

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.solana.aggregator.admincli.cli.AdminCliVersionProvider
import net.corda.solana.aggregator.admincli.cli.AuthorizeNotaryCommand
import net.corda.solana.aggregator.admincli.cli.CreateNetworkCommand
import net.corda.solana.aggregator.admincli.cli.InitializeCommand
import net.corda.solana.aggregator.admincli.cli.ListNotariesCommand
import net.corda.solana.aggregator.admincli.cli.RevokeNotaryCommand
import net.corda.solana.aggregator.admincli.cli.ShowNextAvailableNetworkIdCommand
import picocli.CommandLine.Command

/**
 * Main CLI command class for Solana operations.
 */
@Command(versionProvider = AdminCliVersionProvider::class)
class SolanaAggregatorCli : CordaCliWrapper("solana-notary-admin", "CLI tool for admin operations on the Solana notary program") {

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
