package net.corda.solana.notary.admincli

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.solana.notary.admincli.cmds.AuthorizeNotaryCommand
import net.corda.solana.notary.admincli.cmds.CreateNetworkCommand
import net.corda.solana.notary.admincli.cmds.InitializeCommand
import net.corda.solana.notary.admincli.cmds.ListNotariesCommand
import net.corda.solana.notary.admincli.cmds.RevokeNotaryCommand
import net.corda.solana.notary.admincli.cmds.ShowNextAvailableNetworkIdCommand
import net.corda.solana.notary.client.CordaNotary
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
