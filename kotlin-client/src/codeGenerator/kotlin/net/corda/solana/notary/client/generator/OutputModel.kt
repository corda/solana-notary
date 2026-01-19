package net.corda.solana.notary.client.generator

import software.sava.core.accounts.PublicKey
import software.sava.core.programs.Discriminator

data class OutputModel(
    val name: String,
    val programId: PublicKey,
    val instructions: List<Instruction>,
    val accounts: List<DefinedAccount>,
    val types: List<Type.Defined>,
    val errors: List<AnchorIdl.Error>
) {
    data class Instruction(
        val name: String,
        val discriminator: Discriminator,
        val args: List<Arg>,
        val accounts: List<Account>,
        val docLines: List<String>
    ) {
        data class Arg(val name: String, val type: Type, val docLines: List<String>)

        sealed interface Account {
            val name: String
            val writable: Boolean
            val signer:  Boolean

            data class Pda(
                override val name: String,
                override val writable: Boolean,
                override val signer: Boolean,
                val seeds: List<Seed>,
            ) : Account

            data class FixedAddress(
                override val name: String,
                override val writable: Boolean,
                override val signer: Boolean,
                val address: PublicKey,
            ) : Account

            data class Named(
                override val name: String,
                override val writable: Boolean,
                override val signer: Boolean
            ) : Account
        }

        sealed interface Seed {
            data class Arg(val ref: Instruction.Arg) : Seed
            data class Const(private val _bytes: ByteArray) : Seed {
                val bytes: ByteArray get() = _bytes.clone()
                override fun equals(other: Any?): Boolean = other is Const && _bytes.contentEquals(other._bytes)
                override fun hashCode(): Int = 31 * _bytes.contentHashCode()
            }
            data class DefinedAccountField(val ref: Type.Defined.Field.Named) : Seed
            data class NamedAccount(val ref: Account.Named) : Seed
        }
    }

    data class DefinedAccount(val type: Type.Defined, val discriminator: Discriminator)

    sealed interface Type {
        @Suppress("EnumEntryName")
        enum class Native : Type {
            u8,
            i8,
            u16,
            i16,
            u32,
            i32,
            u64,
            i64,
            u128,
            f32,
            f64,
            bool,
            bytes,
            pubkey,
        }

        data class Defined(val name: String, val fields: List<Field>, val docLines: List<String>) : Type {
            sealed interface Field {
                data class Named(val name: String, val type: Type, val docLines: List<String>) : Field
                data class Unnamed(val type: Type) : Field
                data class FixedSizeArray(val size: Int, val elementType: Type) : Field
            }
        }

        data class Vec(val elementType: Type) : Type
    }
}
