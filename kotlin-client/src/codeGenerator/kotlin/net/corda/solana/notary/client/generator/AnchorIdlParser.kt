package net.corda.solana.notary.client.generator

import com.fasterxml.jackson.databind.JsonNode
import net.corda.solana.notary.client.generator.AnchorIdl.AnchorType
import net.corda.solana.notary.client.generator.OutputModel.DefinedAccount
import net.corda.solana.notary.client.generator.OutputModel.Instruction
import net.corda.solana.notary.client.generator.OutputModel.Type
import software.sava.core.accounts.PublicKey
import software.sava.core.programs.Discriminator

class AnchorIdlParser(private val idl: AnchorIdl) {
    private val definedTypes = LinkedHashMap<String, Type.Defined>()

    fun parse(): OutputModel {
        val accounts = idl.accounts.associateBy({ it.name }, ::parseAccount)
        return OutputModel(
            idl.metadata.name,
            PublicKey.fromBase58Encoded(idl.address),
            idl.instructions.map { InstructionParser(it).parse() },
            accounts.values.toList(),
            definedTypes.values.filter { it.name !in accounts },
            idl.errors
        )
    }

    private fun parseAccount(idlAccount: AnchorIdl.Account): DefinedAccount {
        return DefinedAccount(
            getDefinedType(idlAccount.name),
            Discriminator.createDiscriminator(idlAccount.discriminator)
        )
    }

    private fun parseType(json: JsonNode): Type {
        return when {
            json.isTextual -> Type.Native.valueOf(json.textValue())
            json.has("defined") -> getDefinedType(json["defined"]["name"].textValue())
            json.has("vec") -> Type.Vec(parseType(json["vec"]))
            else -> throw IllegalArgumentException(json.toString())
        }
    }

    private fun getDefinedType(name: String): Type.Defined {
        return definedTypes.getOrPut(name) {
            val idlType = checkNotNull(idl.types.firstOrNull { it.name == name }) { name }
            when (idlType.type) {
                is AnchorIdl.AnchorTypeDef.Struct -> parseStruct(idlType.type, idlType)
            }
        }
    }

    private fun parseStruct(struct: AnchorIdl.AnchorTypeDef.Struct, idlType: AnchorType): Type.Defined {
        val fields = struct.fields.map { fieldJson ->
            when {
                fieldJson.has("name") -> {
                    Type.Defined.Field.Named(
                        fieldJson["name"].textValue(),
                        parseType(fieldJson["type"]!!),
                        fieldJson["docs"]?.map { it.textValue() } ?: emptyList()
                    )
                }
                fieldJson.has("array") -> {
                    val array = fieldJson["array"]!!
                    check(array[1].isNumber)
                    Type.Defined.Field.FixedSizeArray(array[1].intValue(), parseType(array[0]))
                }
                else -> Type.Defined.Field.Unnamed(parseType(fieldJson))
            }
        }
        return Type.Defined(idlType.name, fields, idlType.docs ?: emptyList())
    }

    private inner class InstructionParser(private val idlInstruction: AnchorIdl.Instruction) {
        private val args = LinkedHashMap<String, Instruction.Arg>()
        private val accounts = LinkedHashMap<String, Instruction.Account>()

        fun parse(): Instruction {
            idlInstruction.args.associateByTo(args, { it.name }, ::parseArg)
            idlInstruction.accounts.associateByTo(accounts, { it.name }, ::parseAccount)
            return Instruction(
                idlInstruction.name,
                Discriminator.createDiscriminator(idlInstruction.discriminator),
                args.values.toList(),
                accounts.values.toList(),
                idlInstruction.docs ?: emptyList()
            )
        }

        private fun parseArg(idlArg: AnchorIdl.Arg): Instruction.Arg {
            return Instruction.Arg(idlArg.name, parseType(idlArg.type), idlArg.docs ?: emptyList())
        }

        private fun parseAccount(idlIxAcct: AnchorIdl.InstructionAccount): Instruction.Account {
            return if (idlIxAcct.address != null) {
                check(idlIxAcct.pda == null)
                val address = PublicKey.fromBase58Encoded(idlIxAcct.address)
                Instruction.Account.FixedAddress(idlIxAcct.name, idlIxAcct.writable, idlIxAcct.signer, address)
            } else if (idlIxAcct.pda != null) {
                val seeds = idlIxAcct.pda.seeds.map(::parseSeed)
                Instruction.Account.Pda(idlIxAcct.name, idlIxAcct.writable, idlIxAcct.signer, seeds)
            } else {
                Instruction.Account.Named(idlIxAcct.name, idlIxAcct.writable, idlIxAcct.signer)
            }
        }

        private fun parseSeed(idlSeed: AnchorIdl.Seed): Instruction.Seed {
            return when (idlSeed) {
                is AnchorIdl.Seed.Arg -> Instruction.Seed.Arg(args.getValue(idlSeed.path))
                is AnchorIdl.Seed.Const -> Instruction.Seed.Const(idlSeed.value)
                is AnchorIdl.Seed.Account -> {
                    if (idlSeed.account != null) {
                        val (_, fieldName) = idlSeed.path.split('.').apply { require(size == 2) { this } }
                        val definedAccountField = getDefinedType(idlSeed.account)
                            .fields
                            .filterIsInstance<Type.Defined.Field.Named>()
                            .firstOrNull { it.name == fieldName }
                            ?: throw IllegalArgumentException("${idlSeed.path} ${idlSeed.account}")
                        Instruction.Seed.DefinedAccountField(definedAccountField)
                    } else {
                        val account = accounts.getValue(idlSeed.path)
                        Instruction.Seed.NamedAccount(account as Instruction.Account.Named)
                    }
                }
            }
        }
    }
}
