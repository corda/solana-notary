package net.corda.solana.notary.client.kotlincodegen

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.base.CaseFormat
import com.lmax.solana4j.Solana
import com.lmax.solana4j.api.InstructionBuilderBase
import com.lmax.solana4j.api.PublicKey
import com.lmax.solana4j.programs.SystemProgram
import com.lmax.solana4j.programs.SystemProgram.SYSTEM_PROGRAM_ACCOUNT
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.jvmStatic
import net.corda.solana.notary.common.codegen.BorshSerialisable
import net.corda.solana.notary.common.codegen.BorshUtils
import net.corda.solana.notary.common.codegen.FixedBytesNewtypeStruct
import net.corda.solana.notary.common.codegen.IdlCodeGenSupport
import net.corda.solana.notary.common.codegen.U128
import net.corda.solana.notary.client.kotlincodegen.AnchorIdl.*
import net.corda.solana.notary.client.kotlincodegen.AnchorIdl.AnchorTypeDef.Struct
import net.corda.solana.notary.common.AnchorInstruction
import net.corda.solana.notary.common.Signer
import java.nio.ByteBuffer
import javax.annotation.processing.Generated
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

lateinit var outputPackage: String
lateinit var programName: String
lateinit var programId: PropertySpec
lateinit var accountTypes: Map<String, ByteArray>
val fixedSizeStructs = HashMap<ClassName, Int>()

fun main(args: Array<String>) {
    val idlFile = Path(args[0])
    val outputDir = Path(args[1]).createDirectories()
    outputPackage = args[2]

    val anchorIdl = jacksonObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .readValue<AnchorIdl>(idlFile.toFile())

    programName = anchorIdl.metadata.name.toUpperCamel()

    programId = PropertySpec.builder("PROGRAM_ID", PublicKey::class)
        .jvmStatic()
        .initializer("%M(%S)", Solana::class.member("account"), anchorIdl.address)
        .build()

    accountTypes = anchorIdl.accounts.associateBy(Account::name, Account::discriminator)

    val file = FileSpec.builder(outputPackage, programName)
        .indent("    ")
        .addFileComment("THIS IS GENERATED CODE, DO NOT MODIFY!")
        .addAnnotation(Generated::class)
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "unused")
                .addMember("%S", "RedundantVisibilityModifier")
                .build()
        )
        .addType(parseProgram(anchorIdl))
        .build()
    file.writeTo(outputDir)
}

fun parseProgram(anchorIdl: AnchorIdl): TypeSpec {
    val typesClassBuilder = TypeSpec.classBuilder("Types")
    val accountsClassBuilder = TypeSpec.classBuilder("Accounts")

    for (anchorType in anchorIdl.types.asReversed()) {
        when (anchorType.type) {
            is Struct -> parseAnchorStruct(anchorType.name, anchorType.docs, anchorType.type, typesClassBuilder, accountsClassBuilder)
        }
    }

    val programObjectBuilder = TypeSpec.objectBuilder(programName)

    for (instruction in anchorIdl.instructions) {
        parseInstruction(instruction, programObjectBuilder, anchorIdl.types)
    }

    return programObjectBuilder
        .addProperty(programId)
        .addType(typesClassBuilder.build())
        .addType(accountsClassBuilder.build())
        .addType(parseErrors(anchorIdl))
        .build()
}

fun parseAnchorStruct(
    name: String,
    docs: List<String>?,
    type: Struct,
    typesClassBuilder: TypeSpec.Builder,
    accountsClassBuilder: TypeSpec.Builder,
) {
    val anchorTypeBuilder = TypeSpec.classBuilder(name)
    docs?.forEach(anchorTypeBuilder::addKdoc)
    val fixedArrayFields = type.fields.filterIsInstance<Struct.Field.FixedArray>()
    if (fixedArrayFields.isEmpty()) {
        anchorTypeBuilder
            .addModifiers(DATA)
            .primaryProperties {
                for ((index, field) in type.fields.withIndex()) {
                    if (field is Struct.Field.Named) {
                        it.add(field.name.toLowerCamel(), field.type.toTypeName())
                    } else if (field is Struct.Field.Unnamed) {
                        it.add("field$index", field.type.toBuiltinClassName())
                    }
                }
            }
        val companionObjectBuilder = TypeSpec.companionObjectBuilder()
        if (name in accountTypes) {
            companionObjectBuilder.addProperty(
                PropertySpec.builder("DISCRIMINATOR", ByteArray::class)
                    .jvmStatic()
                    .initializer(accountTypes.getValue(name).toCodeBlock())
                    .build()
            )
            anchorTypeBuilder
                .implementBorshSerialisable(ClassName(outputPackage, programName, "Accounts", name), companionObjectBuilder)
                .addType(companionObjectBuilder.build())
            accountsClassBuilder.addType(anchorTypeBuilder.build())
        } else {
            anchorTypeBuilder
                .implementBorshSerialisable(ClassName(outputPackage, programName, "Types", name), companionObjectBuilder)
                .addType(companionObjectBuilder.build())
            typesClassBuilder.addType(anchorTypeBuilder.build())
        }
    } else if (fixedArrayFields.size == 1) {
        check(type.fields.size == 1)
        if (fixedArrayFields[0].type != "u8") {
            throw UnsupportedOperationException(fixedArrayFields[0].type)
        }
        anchorTypeBuilder.extendFixedBytesNewtypeStruct(ClassName(outputPackage, programName, "Types", name), fixedArrayFields[0].size)
        typesClassBuilder.addType(anchorTypeBuilder.build())
    } else {
        throw UnsupportedOperationException("More than one fixed array field")
    }
}

fun TypeSpec.Builder.implementBorshSerialisable(
    name: ClassName,
    companionObjectBuilder: TypeSpec.Builder,
    isDeserialisable: Boolean = true,
    exclude: Set<String> = emptySet(),
): TypeSpec.Builder {
    addSuperinterface(BorshSerialisable::class)

    val bufferParam = ParameterSpec.builder("buffer", ByteBuffer::class).build()

    val borshWriteFunctionBuilder = FunSpec.builder("borshWrite")
        .addModifiers(OVERRIDE)
        .addParameter(bufferParam)

    val borshReadFunctionBuilder = FunSpec.builder("borshRead")
        .jvmStatic()
        .addParameter(bufferParam)
        .returns(name)

    val serializableProperties = ArrayList<PropertySpec>(propertySpecs.filter { it.name !in exclude })
    serializableProperties.removeIf { it.name == "borshSize" }

    var totalFixedSize = 0
    val nonFixedSizeProperties = ArrayList<PropertySpec>()
    for (property in serializableProperties) {
        borshWriteFunctionBuilder.addStatement("%M(%N, %N)", className<BorshUtils>().member("write"), bufferParam, property)
        when (val propertyType = property.type) {
            is ParameterizedTypeName -> {
                if (propertyType.rawType.toClass() != List::class.java) {
                    throw UnsupportedOperationException("$propertyType")
                }
                nonFixedSizeProperties += property
                val elementType = propertyType.typeArguments[0] as ClassName
                borshReadFunctionBuilder.addStatement(
                    "val %N = %M(%N) { %L }",
                    property,
                    className<BorshUtils>().member("readList"),
                    bufferParam,
                    readFunctionCall(elementType, bufferParam)
                )
            }
            is ClassName -> {
                val fixedSize = fixedSizeStructs[propertyType] ?: BorshUtils.fixedSize(propertyType.toClass())
                if (fixedSize != null) {
                    totalFixedSize += fixedSize
                } else {
                    nonFixedSizeProperties += property
                }
                borshReadFunctionBuilder.addStatement("val %N = %L", property, readFunctionCall(propertyType, bufferParam))
            }
            else -> throw UnsupportedOperationException("$propertyType")
        }
    }

    if (nonFixedSizeProperties.isEmpty()) {
        fixedBorshSize(name, totalFixedSize, companionObjectBuilder)
    } else {
        val borshSizeBuilder = FunSpec.getterBuilder().addStatement("var size = $totalFixedSize")
        for (property in nonFixedSizeProperties) {
            borshSizeBuilder.addStatement("size += %M(%N)", className<BorshUtils>().member("size"), property)
        }
        borshSizeBuilder.addStatement("return size")
        addProperty(PropertySpec.builder("borshSize", Int::class, OVERRIDE).getter(borshSizeBuilder.build()).build())
    }

    addFunction(borshWriteFunctionBuilder.build())

    if (isDeserialisable) {
        borshReadFunctionBuilder.addStatement(
            "return %T(%L)",
            name,
            serializableProperties.joinToString(", ") { CodeBlock.of("%N", it).toString() }
        )
        companionObjectBuilder.addFunction(borshReadFunctionBuilder.build())
    }

    return this
}

fun readFunctionCall(className: ClassName, bufferParam: ParameterSpec): CodeBlock {
    return if (className.isBorshSerialisable()) {
        CodeBlock.of("%M(%N)", className.member("borshRead"), bufferParam)
    } else {
        CodeBlock.of("%M(%N)", className<BorshUtils>().member("read${className.simpleName}"), bufferParam)
    }
}

fun ClassName.isBorshSerialisable(): Boolean {
    if (packageName == outputPackage && topLevelClassName().simpleName == programName) {
        val containingClass = enclosingClassName()?.simpleName
        return containingClass == "Types" || containingClass == "Accounts"
    }
    return BorshSerialisable::class.java.isAssignableFrom(toClass())
}

fun TypeSpec.Builder.fixedBorshSize(name: ClassName, size: Int, companionBuilder: TypeSpec.Builder): PropertySpec {
    val borshSizeProperty = PropertySpec.builder("BORSH_SIZE", Int::class, CONST).initializer("%L", size).build()
    companionBuilder.addProperty(borshSizeProperty)
    addProperty(
        PropertySpec.builder("borshSize", Int::class, OVERRIDE)
            .getter(FunSpec.getterBuilder().addStatement("return %N", borshSizeProperty).build())
            .build()
    )
    fixedSizeStructs[name] = size
    return borshSizeProperty
}

fun TypeSpec.Builder.extendFixedBytesNewtypeStruct(name: ClassName, size: Int) {
    val companionObjectBuilder = TypeSpec.companionObjectBuilder()
    val borshSizeProperty = fixedBorshSize(name, size, companionObjectBuilder)
    val bufferParam = ParameterSpec.builder("buffer", ByteBuffer::class).build()
    companionObjectBuilder.addFunction(
        FunSpec.builder("borshRead")
            .jvmStatic()
            .addParameter(bufferParam)
            .addKdoc("The ByteBuffer needs to be in LITTLE_ENDIAN byte order.")
            .returns(name)
            .addStatement("val bytes = ByteArray(%N)", borshSizeProperty)
            .addStatement("%N.get(bytes)", bufferParam)
            .addStatement("return %T(bytes)", name)
            .build()
    )

    addType(companionObjectBuilder.build())
    primaryConstructor(FunSpec.constructorBuilder().addParameter("bytes", ByteArray::class).build())
    superclass(FixedBytesNewtypeStruct::class)
    addSuperclassConstructorParameter("bytes")
}

fun parseInstruction(instruction: Instruction, programObjectBuilder: TypeSpec.Builder, types: List<AnchorType>) {
    val discriminator = PropertySpec.builder("DISCRIMINATOR", ByteArray::class, PRIVATE)
        .jvmStatic()
        .initializer(instruction.discriminator.toCodeBlock())
        .build()

    val ixBuilderParam = ParameterSpec.builder("builder", InstructionBuilderBase::class).build()
    val addToTxFunctionBuilder = FunSpec.builder("addToTx")
        .addModifiers(OVERRIDE)
        .addParameter(ixBuilderParam)
        .addStatement("addToTx(%N, %N, %N)", programId, discriminator, ixBuilderParam)

    val signers = ArrayList<String>()
    var defaultTxFeePayer: String? = null

    val genericAccounts = ArrayList<InstructionAccount>()
    val additionalProperties = mutableSetOf<ReferencedAccountField>()
    for (account in instruction.accounts) {
        addToTxFunctionBuilder.addComment(account.name)
        if (account.address != null) {
            addToTxFunctionBuilder.addAddressAccount(account.address, ixBuilderParam)
        } else if (account.pda != null) {
            additionalProperties.addAll(addToTxFunctionBuilder.addPdaAccount(account.pda, account.signer, account.writable, ixBuilderParam))
        } else {
            genericAccounts += account
            val paramName = account.name.toLowerCamel()

            if (account.signer) {
                addToTxFunctionBuilder.addStatement("%N.account(%L.account, %L, %L)", ixBuilderParam, paramName, true, account.writable)
                signers += paramName
                if (account.writable && defaultTxFeePayer == null) {
                    defaultTxFeePayer = paramName
                }
            } else {
                addToTxFunctionBuilder.addStatement("%N.account(%L, %L, %L)", ixBuilderParam, paramName, false, account.writable)
            }
        }
    }

    val instructionClassCompanionBuilder = TypeSpec.companionObjectBuilder().addProperty(discriminator)
    val instructionClass = TypeSpec.classBuilder(instruction.name.toUpperCamel())
        .addModifiers(DATA)
        .superclass(AnchorInstruction::class)
        .primaryProperties { primaryPropertyBuilder ->
            for (arg in instruction.args) {
                primaryPropertyBuilder.add(arg.name.toLowerCamel(), arg.type.toTypeName())
            }
            for (account in genericAccounts) {
                val name = account.name.toLowerCamel()
                if (account.signer) {
                    primaryPropertyBuilder.add(name, className<Signer>())
                } else {
                    primaryPropertyBuilder.add(name, className<PublicKey>())
                }
            }
            additionalProperties.forEach { referencedAccountField ->
                primaryPropertyBuilder.add(
                    referencedAccountField.classPropertyName,
                    types.extractReferencedAccountFieldType(referencedAccountField)
                )
            }
            primaryPropertyBuilder.add(
                "txFeePayer",
                className<Signer>().copy(nullable = true),
                CodeBlock.of("%L", defaultTxFeePayer),
                OVERRIDE
            )
        }
        .addProperty(
            PropertySpec.builder("signers", className<List<*>>().parameterizedBy(className<Signer>()), OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("return listOf(%L)", signers.joinToString(", ")).build())
                .build()
        )
        .implementBorshSerialisable(
            ClassName(outputPackage, programName, instruction.name.toUpperCamel()),
            instructionClassCompanionBuilder,
            isDeserialisable = false,
            exclude = genericAccounts.map { it.name.toLowerCamel() }.toSet() +
                "txFeePayer" +
                "signers" +
                additionalProperties.map { it.classPropertyName }.toSet()
        )
        .addType(instructionClassCompanionBuilder.build())
        .addFunction(addToTxFunctionBuilder.build())
        .build()
    programObjectBuilder.addType(instructionClass)
}

private fun List<AnchorType>.extractReferencedAccountFieldType(referencedAccountField: ReferencedAccountField): TypeName {
    return (first { it.name == referencedAccountField.account }.type as Struct)
        .fields.filterIsInstance<Struct.Field.Named>()
        .first { it.name == referencedAccountField.accountFieldName }
        .type.toTypeName()
}

fun FunSpec.Builder.addPdaAccount(pda: PDA, signer: Boolean, writable: Boolean, ixBuilderParam: ParameterSpec): Set<ReferencedAccountField> {
    val seedMethod = IdlCodeGenSupport::class.member("seedBytes")
    val referencedAccountFields = mutableSetOf<ReferencedAccountField>()
    val seeds = pda.seeds.map { seed ->
        when (seed) {
            is Seed.Arg -> CodeBlock.of("%M(%L)", seedMethod, seed.path.toLowerCamel())
            is Seed.Const -> seed.value.toCodeBlock()
            // Here we assume that the maximum nesting level is 1, i.e., the seed path is either an account name or a field name within the account.
            is Seed.Account -> if (seed.path.contains('.')) {
                // The seed refers to an account field, so we need to create a ReferencedAccountField
                val referencedAccountField = ReferencedAccountField(seed.path.substringAfterLast('.'), checkNotNull(seed.account) {
                    "The \"account\" must be specified for referenced account field seed: $seed"
                })
                referencedAccountFields.add(referencedAccountField)
                CodeBlock.of("%M(%L)", seedMethod, referencedAccountField.classPropertyName)
            } else {
                CodeBlock.of("%M(%L)", seedMethod, seed.path.toLowerCamel())
            }
        }
    }

    addStatement(
        "%N.account(%M(listOf(%L), %N).address(), %L, %L)",
        ixBuilderParam,
        Solana::class.member("programDerivedAddress"),
        seeds.joinToString(", "),
        programId,
        signer,
        writable
    )
    return referencedAccountFields
}

fun FunSpec.Builder.addAddressAccount(address: String, ixBuilderParam: ParameterSpec) {
    when (address) {
        SYSTEM_PROGRAM_ACCOUNT.base58() -> {
            addStatement(
                "%N.account(%M, false, false)",
                ixBuilderParam,
                SystemProgram::class.member("SYSTEM_PROGRAM_ACCOUNT")
            )
        }
        else -> throw UnsupportedOperationException(address)
    }
}

fun parseErrors(anchorIdl: AnchorIdl): TypeSpec {
    val errorEnumBuilder = TypeSpec.enumBuilder("Error").primaryProperties { it.add("code", className<Int>()) }
    val valueOfMethodBuilder = FunSpec.builder("valueOf")
        .jvmStatic()
        .addParameter("code", Int::class)
        .returns(ClassName("", "Error").copy(nullable = true))
        .beginControlFlow("return when (code)")
    for (error in anchorIdl.errors) {
        errorEnumBuilder.addEnumConstant(
            error.name,
            TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("%L", error.code).build()
        )
        valueOfMethodBuilder.addStatement("%L -> %L", error.code, error.name)
    }
    val valueOfMethod = valueOfMethodBuilder
        .addStatement("else -> null")
        .endControlFlow()
        .build()
    return errorEnumBuilder
        .addType(TypeSpec.companionObjectBuilder().addFunction(valueOfMethod).build())
        .build()
}

inline fun <reified T> className(): ClassName = T::class.asClassName()

fun String.toBuiltinClassName(): ClassName {
    return when (this) {
        "u8", "i8" -> className<Byte>()
        "u16", "i16" -> className<Short>()
        "u32", "i32" -> className<Int>()
        "u64", "i64" -> className<Long>()
        "u128" -> className<U128>()
        "f32" -> className<Float>()
        "f64" -> className<Double>()
        "bool" -> className<Boolean>()
        "bytes" -> className<ByteArray>()
        "pubkey" -> className<PublicKey>()
        else -> throw UnsupportedOperationException(this)
    }
}

fun ClassName.toClass(): Class<*> {
    return when (val reflectionName = reflectionName()) {
        "kotlin.Byte" -> Byte::class.java
        "kotlin.Short" -> Short::class.java
        "kotlin.Int" -> Int::class.java
        "kotlin.Long" -> Long::class.java
        "kotlin.Float" -> Float::class.java
        "kotlin.Double" -> Double::class.java
        "kotlin.Boolean" -> Boolean::class.java
        "kotlin.ByteArray" -> ByteArray::class.java
        "kotlin.collections.List" -> List::class.java
        else -> Class.forName(reflectionName)
    }
}

fun JsonNode.toTypeName(): TypeName {
    return when {
        isTextual -> textValue().toBuiltinClassName()
        has("defined") -> ClassName(outputPackage, programName, "Types", this["defined"]["name"].textValue())
        has("vec") -> className<List<*>>().parameterizedBy(this["vec"].toTypeName())
        else -> throw UnsupportedOperationException(toString())
    }
}

inline fun TypeSpec.Builder.primaryProperties(block: (PrimaryPropertyBuilder) -> Unit): TypeSpec.Builder {
    val ctorBuilder = FunSpec.constructorBuilder()
    block(PrimaryPropertyBuilder(this, ctorBuilder))
    primaryConstructor(ctorBuilder.build())
    return this
}

class PrimaryPropertyBuilder(private val typeBuilder: TypeSpec.Builder, private val ctorBuilder: FunSpec.Builder) {
    fun add(name: String, type: TypeName, defaultValue: CodeBlock? = null, vararg modifiers: KModifier): PrimaryPropertyBuilder {
        ctorBuilder.addParameter(ParameterSpec.builder(name, type, modifiers.asList() - OVERRIDE).defaultValue(defaultValue).build())
        typeBuilder.addProperty(PropertySpec.builder(name, type, modifiers.asList()).initializer(name).build())
        return this
    }
}

fun String.toUpperCamel(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this)
fun String.toLowerCamel(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this)

fun ByteArray.toCodeBlock() = CodeBlock.of("byteArrayOf(%L)", joinToString(", "))

data class ReferencedAccountField(
    val accountFieldName: String,
    val account: String,
) {
    val classPropertyName = accountFieldName.toLowerCamel()
}
