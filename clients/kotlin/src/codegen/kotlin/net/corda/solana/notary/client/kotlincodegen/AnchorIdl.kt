package net.corda.solana.notary.client.kotlincodegen

import com.fasterxml.jackson.annotation.JsonCreator
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
            val args: List<Arg>
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

    class Arg(val name: String, val type: JsonNode)

    class Account(val name: String, val discriminator: ByteArray)

    class Error(val name: String, val code: Int)

    class AnchorType(val name: String, val docs: List<String>?, val type: AnchorTypeDef)

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    sealed interface AnchorTypeDef {

        @JsonTypeName("struct")
        class Struct(val fields: List<Field>) : AnchorTypeDef {

            sealed interface Field {
                class Named(val name: String, val type: JsonNode) : Field
                class Unnamed(val type: String) : Field
                class FixedArray(val type: String, val size: Int) : Field

                companion object {
                    @JvmStatic
                    @JsonCreator
                    fun creator(json: JsonNode): Field {
                        return when {
                            json.isTextual -> Unnamed(json.textValue())
                            json["name"] != null -> Named(json["name"].textValue(), json["type"]!!)
                            json["array"] != null -> {
                                val array = json["array"]
                                FixedArray(array[0].textValue(), array[1].intValue())
                            }
                            else -> throw UnsupportedOperationException("${json.toPrettyString()}")
                        }
                    }
                }
            }
        }
    }
}
