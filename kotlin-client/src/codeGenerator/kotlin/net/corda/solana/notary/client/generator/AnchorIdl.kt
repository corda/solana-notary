package net.corda.solana.notary.client.generator

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.JsonNode

class AnchorIdl(
    val address: String,
    val metadata: Metadata,
    val instructions: List<Instruction>,
    val accounts: List<Account>,
    val errors: List<Error>,
    val types: List<AnchorType>
) {
    class Metadata(val name: String)

    class Instruction(
        val name: String,
        val discriminator: ByteArray,
        val accounts: List<InstructionAccount>,
        val args: List<Arg>,
        val docs: List<String>?
    )

    class InstructionAccount(
        val name: String,
        val pda: PDA?,
        val address: String?,
        val writable: Boolean = false,
        val signer: Boolean = false,
    )

    class PDA(val seeds: List<Seed>)

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    sealed interface Seed {

        @JsonTypeName("arg")
        class Arg(val path: String) : Seed

        @JsonTypeName("const")
        class Const(val value: ByteArray) : Seed

        @JsonTypeName("account")
        class Account(val path: String, val account: String?) : Seed
    }

    class Arg(val name: String, val type: JsonNode, val docs: List<String>?)

    class Account(val name: String, val discriminator: ByteArray)

    class Error(val name: String, val code: Int)

    class AnchorType(val name: String, val docs: List<String>?, val type: AnchorTypeDef)

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    sealed interface AnchorTypeDef {

        @JsonTypeName("struct")
        class Struct(val fields: List<JsonNode>) : AnchorTypeDef
    }
}
