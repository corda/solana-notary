package net.corda.solana.notary.admincli

import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaTransactionException
import net.corda.solana.notary.admincli.cmds.AuthorizeNotaryCommand
import net.corda.solana.notary.admincli.cmds.CreateNetworkCommand
import net.corda.solana.notary.admincli.cmds.InfoCommand
import net.corda.solana.notary.admincli.cmds.InitializeCommand
import net.corda.solana.notary.admincli.cmds.RevokeNotaryCommand
import net.corda.solana.notary.client.CordaNotary
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IExecutionExceptionHandler
import picocli.CommandLine.Option
import picocli.CommandLine.ParseResult
import picocli.CommandLine.ScopeType
import software.sava.core.accounts.PublicKey
import java.nio.file.Paths
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "solana-notary-admin",
    mixinStandardHelpOptions = true,
    versionProvider = ManifestVersionProvider::class,
    sortOptions = false,
    showDefaultValues = true,
    synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
    descriptionHeading = "%n@|bold,underline Description|@:%n%n",
    parameterListHeading = "%n@|bold,underline Parameters|@:%n%n",
    optionListHeading = "%n@|bold,underline Options|@:%n%n",
    commandListHeading = "%n@|bold,underline Commands|@:%n%n",
    subcommands = [
        InitializeCommand::class,
        AuthorizeNotaryCommand::class,
        RevokeNotaryCommand::class,
        InfoCommand::class,
        CreateNetworkCommand::class,
    ]
)
class SolanaNotaryAdmin : Callable<Int> {
    @Option(
        names = ["--logging-level"],
        scope = ScopeType.INHERIT,
        description = [$$"Log level: ${COMPLETION-CANDIDATES}"],
        defaultValue = "WARN"
    )
    @Suppress("unused")
    fun setLoggingLevel(level: Level) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.name.lowercase())
    }

    override fun call(): Int {
        CommandLine.usage(this, System.out)
        return 1
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val commandLine = CommandLine(SolanaNotaryAdmin())
                .registerConverter(PublicKey::class.java, PublicKey::fromBase58Encoded)
                .registerConverter(FileSigner::class.java) { FileSigner.read(Paths.get(it)) }
                .registerConverter(Encoding::class.java, Encoding::parse)
                .setExecutionExceptionHandler(ExceptionHandler())
            commandLine
                .commandSpec
                .usageMessage()
                .description("CLI tool for admin operations on the Solana notary program (${CordaNotary.PROGRAM_ID})")
            val exitCode = commandLine.execute(*args)
            exitProcess(exitCode)
        }
    }

    private class ExceptionHandler : IExecutionExceptionHandler {
        override fun handleExecutionException(e: Exception, cmd: CommandLine, parseResult: ParseResult): Int {
            System.err.println("✗ ${e.message}")
            if (e is SolanaTransactionException) {
                e.logMessages.forEach(System.err::println)
            }
            LoggerFactory.getLogger(ExceptionHandler::class.java).debug("Aborted with error", e)
            return 1
        }
    }
}
