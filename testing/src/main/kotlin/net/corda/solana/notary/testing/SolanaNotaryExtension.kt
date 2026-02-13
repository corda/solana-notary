package net.corda.solana.notary.testing

import com.r3.corda.lib.solana.core.FileSigner
import com.r3.corda.lib.solana.core.SolanaClient
import com.r3.corda.lib.solana.testing.ConfigureValidator
import com.r3.corda.lib.solana.testing.SolanaTestValidator
import com.r3.corda.lib.solana.testing.SolanaTestValidator.Builder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods
import org.junit.platform.commons.support.HierarchyTraversalMode.BOTTOM_UP
import org.junit.platform.commons.support.ModifierSupport.isPublic
import org.junit.platform.commons.support.ModifierSupport.isStatic
import org.slf4j.LoggerFactory
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.Signer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Parameter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

/**
 * Automatically starts a [SolanaTestValidator] which is configured with the Solana notary program. This is done before
 * all the tests start and the validator is shutdown after they have run.
 *
 * Similar to [com.r3.corda.lib.solana.testing.SolanaTestClass], tests and lifecycle methods can specify a
 * [SolanaClient] parameter which will be connected to the validator. They can also specify a [SolanaTestValidator]
 * parameter to get access to the instance itself.
 *
 * By default, the test validator uses a temporary directory for its ledger and listens on dynamically assigned ports.
 * This can be changed, or the validator further configured, by having a static method annotated with
 * [ConfigureValidator] which takes in a [SolanaTestValidator.Builder] as a single parameter.
 *
 * Access to the notary admin, either as a [PublicKey], [Signer] or [FileSigner] parameter, is possible by
 * annotating the parameter with [Admin].
 *
 * The same can also be done for notary keys with the [Notary] annotation. Different keys can be retrieved by
 * specifying the network ID ([Notary.network]) and key index ([Notary.value]). Notaries are automatically
 * authorised onto the program by the admin.
 *
 * For further customisation access to an [NotaryEnvironment] instance is also possible.
 *
 * Note, it is required Solana be [installed](https://solana.com/docs/intro/installation) locally to use this extension.
 */
class SolanaNotaryExtension : ParameterResolver, AfterAllCallback {
    private companion object {
        private val logger = LoggerFactory.getLogger(SolanaNotaryExtension::class.java)
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        val parameter = parameterContext.parameter
        val isAdmin = parameter.isAnnotationPresent(Admin::class.java)
        val isNotary = parameter.isAnnotationPresent(Notary::class.java)
        val parameterType = parameter.type
        return when (parameterType) {
            SolanaTestValidator::class.java -> true
            SolanaClient::class.java -> true
            NotaryEnvironment::class.java -> true
            PublicKey::class.java, Signer::class.java, FileSigner::class.java -> {
                (isAdmin && !isNotary) || (!isAdmin && isNotary)
            }
            else -> false
        }
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val store = getStore(extensionContext)
        val testClass = extensionContext.requiredTestClass
        val parameter = parameterContext.parameter
        return when (parameter.type) {
            SolanaTestValidator::class.java -> store.getRequiredTestClassContext(testClass).validator
            SolanaClient::class.java -> store.getRequiredTestClassContext(testClass).validator.client()
            PublicKey::class.java -> store.getSigner(parameter, testClass).publicKey()
            Signer::class.java -> store.getSigner(parameter, testClass)
            FileSigner::class.java -> store.getSigner(parameter, testClass)
            NotaryEnvironment::class.java -> store.getRequiredTestClassContext(testClass).notaryEnv
            else -> throw IllegalArgumentException("Unsupported parameter type: $parameter")
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val validator = getStore(context).getOptionalTestClassContext()?.validator
        if (validator != null) {
            logger.info("Closing test validator on RPC port ${validator.rpcPort()}")
            validator.close()
        }
    }

    private fun getStore(extensionContext: ExtensionContext): ExtensionContext.Store {
        val namespace = Namespace.create(SolanaNotaryExtension::class, extensionContext.requiredTestClass)
        return extensionContext.root.getStore(namespace)
    }

    private fun ExtensionContext.Store.getOptionalTestClassContext(): TestClassContext? {
        return get(ContextKey, TestClassContext::class.java)
    }

    private fun ExtensionContext.Store.getRequiredTestClassContext(testClass: Class<*>): TestClassContext {
        return computeIfAbsent(
            ContextKey,
            {
                val context = TestClassContext(testClass)
                context.notaryEnv.initializeProgram()
                logger.info("Started test validator on RPC port ${context.validator.rpcPort()}")
                context
            },
            TestClassContext::class.java
        )
    }

    private fun ExtensionContext.Store.getSigner(parameter: Parameter, testClass: Class<*>): FileSigner {
        val context = getRequiredTestClassContext(testClass)
        val notaryAnnotation = parameter.getAnnotation(Notary::class.java)
        return when {
            parameter.isAnnotationPresent(Admin::class.java) -> context.admin
            notaryAnnotation != null -> getNotary(notaryAnnotation, testClass)
            else -> throw IllegalArgumentException("Unsupported parameter type: $parameter")
        }
    }

    private fun ExtensionContext.Store.getNotary(notary: Notary, testClass: Class<*>): FileSigner {
        val context = getRequiredTestClassContext(testClass)

        @Suppress("UNCHECKED_CAST")
        val networkMap = computeIfAbsent(
            NetworkKey(notary.network),
            {
                // Fill in any gaps in the networks
                while (context.notaryEnv.nextNetworkId() <= notary.network) {
                    context.notaryEnv.createNewCordaNetwork()
                }
                ConcurrentHashMap<Int, FileSigner>()
            }
        ) as ConcurrentMap<Int, FileSigner>

        return networkMap.computeIfAbsent(notary.value) {
            val signer = FileSigner.random(context.tempDir)
            context.validator.accounts().airdropSol(signer.publicKey(), 10)
            context.notaryEnv.addCordaNotary(notary.network.toShort(), signer.publicKey())
            logger.info("Authorised notary ${signer.file.absolutePathString()} on network ${notary.network}")
            signer
        }
    }

    private class TestClassContext(testClass: Class<*>) {
        val tempDir: Path = Files.createTempDirectory("solana-notary-test")
        val admin = FileSigner.random(tempDir)
        val validator: SolanaTestValidator = SolanaTestValidator
            .builder()
            .ledger(tempDir / "ledger")
            .dynamicPorts()
            .also(NotaryEnvironment::addNotaryProgram)
            .also { configureBuilder(testClass, it) }
            .start()
            .waitForReadiness()
        val notaryEnv = NotaryEnvironment(validator.client(), admin)

        private fun configureBuilder(testClass: Class<*>, builder: Builder) {
            val methods = findAnnotatedMethods(testClass, ConfigureValidator::class.java, BOTTOM_UP)
            val method = when (methods.size) {
                0 -> return
                1 -> methods[0]
                else -> throw ParameterResolutionException(
                    "Multiple @ConfigureValidator methods found in ${testClass.name}"
                )
            }
            if (!isPublic(method)) {
                throw ParameterResolutionException("@ConfigureValidator method ${method.name} must be public")
            }
            if (!isStatic(method)) {
                throw ParameterResolutionException("@ConfigureValidator method ${method.name} must be static")
            }
            if (method.parameterCount != 1 || method.parameterTypes[0] != Builder::class.java) {
                throw ParameterResolutionException(
                    "@ConfigureValidator method ${method.name} must have a single SolanaTestValidator.Builder parameter"
                )
            }
            if (method.returnType != Void.TYPE) {
                throw ParameterResolutionException("@ConfigureValidator method ${method.name} must be void")
            }
            try {
                method.invoke(null, builder)
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }

    private object ContextKey
    private data class NetworkKey(val id: Int)
}
