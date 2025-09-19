package net.corda.solana.notary.admincli

import net.corda.solana.notary.client.kotlin.CordaNotary
import picocli.CommandLine

class ManifestVersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf("solana-notary-admin ${javaClass.getPackage().implementationVersion} " +
            "(program: ${CordaNotary.PROGRAM_ID.base58()})")
    }
}
