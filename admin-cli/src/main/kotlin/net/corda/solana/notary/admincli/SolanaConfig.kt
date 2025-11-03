package net.corda.solana.notary.admincli

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lmax.solana4j.client.api.Commitment
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient
import net.corda.solana.notary.common.Signer
import java.io.IOException
import java.net.http.HttpClient
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Data class representing the Solana CLI configuration file structure.
 */
data class SolanaConfigYml(
    @JsonProperty("json_rpc_url")
    val jsonRpcUrl: String,
    @JsonProperty("websocket_url")
    val websocketUrl: String? = null,
    @JsonProperty("keypair_path")
    val keypairPath: String,
    @JsonProperty("address_labels")
    val addressLabels: Map<String, String> = emptyMap(),
    @JsonProperty("commitment")
    val commitment: String,
)

/**
 * Extension function to convert string commitment to Solana Commitment enum.
 */
fun String.toSolanaCommitment(): Commitment {
    try {
        return Commitment.valueOf(this.uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Unknown commitment level: $this. Valid values are: processed, confirmed, finalized"
        )
    }
}

/**
 * Configuration class for Solana blockchain operations.
 * Handles loading configuration from standard Solana CLI paths and initializing required components.
 */
class SolanaConfig(private val keypairPath: String?, rpcUrl: String?, commitment: Commitment?) {
    val config: SolanaConfigYml?
    val wallet: Signer
    val rpcUrl: String
    val rpcClient: SolanaJsonRpcClient
    val commitment: Commitment

    companion object {
        private val YAML_MAPPER = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

        private fun getDefaultConfigPath(): Path =
            Paths.get(System.getProperty("user.home"), ".config", "solana", "cli", "config.yml")

        private fun getDefaultWalletPath(): Path =
            Paths.get(System.getProperty("user.home"), ".config", "solana", "id.json")
    }

    init {
        wallet = loadWalletFromFile()
        config = loadConfigFromFile()
        this.rpcUrl = requireNotNull(rpcUrl ?: config?.jsonRpcUrl) {
            "Solana config file doesn't exist to determine RPC URL, and nor was it provided as a flag"
        }
        rpcClient = createRpcClient(this.rpcUrl)
        this.commitment = requireNotNull(commitment ?: config?.commitment?.toSolanaCommitment()) {
            "Solana config file doesn't exist to determine commitment level, and nor was it provided as a flag"
        }
    }

    fun validateNotaryAddress(notaryAddress: String) {
        require(notaryAddress.isNotBlank()) { "Notary address cannot be empty" }
    }

    /**
     * Loads the Solana configuration from the standard CLI config file.
     */
    private fun loadConfigFromFile(): SolanaConfigYml? {
        val configPath = getDefaultConfigPath()
        if (!configPath.exists()) {
            return null
        }
        return try {
            YAML_MAPPER.readValue(configPath.toFile(), SolanaConfigYml::class.java)
        } catch (e: IOException) {
            throw SolanaConfigurationException("Failed to read or parse config file at: $configPath", e)
        }
    }

    /**
     * Loads the wallet private key from the standard Solana CLI wallet file.
     */
    private fun loadWalletFromFile(): Signer {
        val walletPath = keypairPath?.let { Paths.get(it) } ?: getDefaultWalletPath()
        if (!walletPath.exists()) {
            throw SolanaConfigurationException("Solana wallet file not found at: $walletPath")
        }
        return try {
            Signer.fromFile(walletPath)
        } catch (e: IOException) {
            throw SolanaConfigurationException("Failed to read wallet file at: $walletPath", e)
        }
    }

    /**
     * Creates and configures the Solana JSON-RPC client.
     */
    private fun createRpcClient(jsonRpcUrl: String): SolanaJsonRpcClient {
        return try {
            SolanaJsonRpcClient(HttpClient.newHttpClient(), jsonRpcUrl)
        } catch (e: Exception) {
            throw SolanaConfigurationException("Failed to create RPC client for URL: $jsonRpcUrl", e)
        }
    }
}

/**
 * Custom exception for Solana configuration related errors.
 */
class SolanaConfigurationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
