package net.corda.solana.notary.testing

/**
 * Test classes using the [NotaryEnvironment] extension can get access notary keys authorised on the program by
 * annotating any [software.sava.core.accounts.PublicKey], [software.sava.core.accounts.Signer]
 * or [com.r3.corda.lib.solana.core.FileSigner] parameter with this annotation.
 *
 * By default the first key on network ID 0 is used. Further notaries on the same network can be authorised by
 * specifying a different [value]. Different networks can only be used by specifying the [network].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Notary(
    val value: Int = 0,
    val network: Int = 0,
)
