package net.corda.cliutils

import com.r3.corda.lib.solana.core.FileSigner
import net.corda.solana.notary.admincli.Encoding
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.DefaultExceptionHandler
import picocli.CommandLine.ExecutionException
import picocli.CommandLine.Help
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.ParseResult
import picocli.CommandLine.RunLast
import software.sava.core.accounts.PublicKey
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import kotlin.system.exitProcess

// Copied from Corda to avoid the dependency just for this.
// TODO This is overkill, replace with vanilla picocli

fun isOsWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

fun CordaCliWrapper.start(args: Array<String>) {
    this.args = args

    val defaultAnsiMode = if (isOsWindows()) Help.Ansi.ON else Help.Ansi.AUTO

    val exceptionHandler = object : DefaultExceptionHandler<List<Any>>() {
        override fun handleParseException(ex: ParameterException?, args: Array<out String>?): List<Any> {
            super.handleParseException(ex, args)
            return listOf(ExitCodes.FAILURE)
        }

        override fun handleExecutionException(ex: ExecutionException, parseResult: ParseResult?): List<Any> {
            val throwable = ex.cause ?: ex
            System.err.println(throwable.message)
            CliWrapperBase.logger.debug("", throwable)
            return listOf(ExitCodes.FAILURE)
        }
    }

    @Suppress("SpreadOperator")
    val results = cmd.parseWithHandlers(
        RunLast().useOut(System.out).useAnsi(defaultAnsiMode),
        exceptionHandler.useErr(System.err).useAnsi(defaultAnsiMode),
        *args
    )

    // If an error code has been returned, use this and exit
    results?.firstOrNull()?.let {
        if (it is Int) {
            exitProcess(it)
        } else {
            exitProcess(ExitCodes.FAILURE)
        }
    }

    // If no results returned, picocli ran something without invoking the main program, e.g. --help or --version, so
    // exit successfully
    exitProcess(ExitCodes.SUCCESS)
}

@Command(
    mixinStandardHelpOptions = true,
    sortOptions = false,
    showDefaultValues = true,
    synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
    descriptionHeading = "%n@|bold,underline Description|@:%n%n",
    parameterListHeading = "%n@|bold,underline Parameters|@:%n%n",
    optionListHeading = "%n@|bold,underline Options|@:%n%n",
    commandListHeading = "%n@|bold,underline Commands|@:%n%n"
)
abstract class CliWrapperBase(val alias: String, val description: String) : Callable<Int> {
    companion object {
        val logger: Logger by lazy { LoggerFactory.getLogger(CliWrapperBase::class.java) }
    }

    // Raw args are provided for use in logging - this is a lateinit var rather than a constructor parameter as the
    // class needs to be parameterless for autocomplete to work.
    lateinit var args: Array<String>

    @Option(
        names = ["--logging-level"],
        description = [$$"Enable logging at this level and higher. Possible values: ${COMPLETION-CANDIDATES}"],
    )
    var loggingLevel: Level = Level.WARN

    // Override this function with the actual method to be run once all the arguments have been parsed. The return
    // number is the exit code to be returned
    abstract fun runProgram(): Int

    fun initLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", loggingLevel.name.lowercase())
        logger.debug("Args: ${args.joinToString(" ")}")
    }

    override fun call(): Int {
        initLogging()
        return runProgram()
    }
}

/**
 * Simple base class for handling help, version, verbose and logging-level commands.
 * As versionProvider information from the MANIFEST file is used. It can be overwritten by custom version providers
 * (see: Node) Picocli will prioritise versionProvider from the `@Command` annotation on the subclass,
 * see: https://picocli.info/#_reuse_combinations
 */
abstract class CordaCliWrapper(alias: String, description: String) : CliWrapperBase(alias, description) {
    protected open fun additionalSubCommands(): Set<CliWrapperBase> = emptySet()

    val cmd by lazy {
        CommandLine(this).apply {
            // Make sure any provided paths are absolute. Relative paths have caused issues and are less clear in logs.
            registerConverter(Path::class.java) { Paths.get(it).toAbsolutePath().normalize() }
            commandSpec.name(alias)
            commandSpec.usageMessage().description(description)
            subCommands.forEach {
                val subCommand = CommandLine(it)
                it.args = args
                subCommand.commandSpec.usageMessage().description(it.description)
                commandSpec.addSubcommand(it.alias, subCommand)
            }
            registerConverter(PublicKey::class.java, PublicKey::fromBase58Encoded)
            registerConverter(FileSigner::class.java) { FileSigner.read(Paths.get(it)) }
            registerConverter(Encoding::class.java, Encoding::parse)
        }
    }

    val subCommands: Set<CliWrapperBase> by lazy {
        additionalSubCommands()
    }

    override fun call(): Int {
        initLogging()
        return runProgram()
    }

    fun printHelp() = cmd.usage(System.out)
}

object ExitCodes {
    const val SUCCESS: Int = 0
    const val FAILURE: Int = 1
}
