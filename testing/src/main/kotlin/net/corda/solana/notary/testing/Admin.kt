package net.corda.solana.notary.testing

/**
 * Test classes using the [NotaryEnvironment] extension can get access to the notary admin by annotating any
 * [software.sava.core.accounts.PublicKey], [software.sava.core.accounts.Signer]
 * or [com.r3.corda.lib.solana.core.FileSigner] parameter with this annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Admin
