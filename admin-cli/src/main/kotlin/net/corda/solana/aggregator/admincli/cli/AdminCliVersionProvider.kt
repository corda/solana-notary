package net.corda.solana.aggregator.admincli.cli

import net.corda.cliutils.CordaVersionProvider
import net.corda.solana.aggregator.notary.idl.CordaNotary

class AdminCliVersionProvider : CordaVersionProvider() {
    override fun getVersion(): Array<String> {
        return super.getVersion() + "Notary Program ID: ${CordaNotary.PROGRAM_ID.base58()}"
    }
}