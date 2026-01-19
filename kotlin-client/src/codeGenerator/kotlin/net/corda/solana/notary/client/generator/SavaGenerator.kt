package net.corda.solana.notary.client.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.CONST
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.jvmField
import com.squareup.kotlinpoet.jvm.jvmOverloads
import com.squareup.kotlinpoet.jvm.jvmStatic
import net.corda.solana.notary.client.generator.OutputModel.Instruction.Account
import net.corda.solana.notary.client.generator.OutputModel.Instruction.Arg
import net.corda.solana.notary.client.generator.OutputModel.Instruction.Seed
import net.corda.solana.notary.client.generator.OutputModel.Type
import net.corda.solana.notary.common.internal.BorshUtils
import net.corda.solana.notary.common.rust.FixedBytesNewtypeStruct
import net.corda.solana.notary.common.rust.U128
import software.sava.core.accounts.ProgramDerivedAddress
import software.sava.core.accounts.PublicKey
import software.sava.core.accounts.SolanaAccounts
import software.sava.core.accounts.meta.AccountMeta
import software.sava.core.borsh.Borsh
import software.sava.core.programs.Discriminator
import software.sava.core.rpc.Filter
import software.sava.core.tx.Instruction

class SavaGenerator(private val model: OutputModel, private val outputPackage: String) {
    private val programName = model.name.toUpperCamel()
    private val programClassName = ClassName(outputPackage, programName)
    private val programIdRef = CodeBlock.of("%T.PROGRAM_ID", programClassName)

    fun createProgramObject(): TypeSpec {
        return TypeSpec.objectBuilder(programName)
            .addProperty(PropertySpec
                .builder("PROGRAM_ID", PublicKey::class)
                .jvmField()
                .initializer("%M(%S)", publicKeyfromBase58Encoded, model.programId.toBase58())
                .build()
            )
            .addType(createErrorEnum())
            .build()
    }

    fun createInstructionObjects(): List<TypeSpec> = model.instructions.map(::createInstructionObject)

    fun createTypeClasses(): List<TypeSpec> = model.types.map { type -> createDefinedTypeClass(type, null) }

    fun createAccountClasses(): List<TypeSpec> {
        return model.accounts.map { account -> createDefinedTypeClass(account.type, account.discriminator) }
    }

    private fun createInstructionObject(instruction: OutputModel.Instruction): TypeSpec {
        val discriminatorProp = createProperty(instruction.discriminator)
        val builder = TypeSpec
            .objectBuilder(instruction.name.toUpperCamel())
            .addProperty(discriminatorProp)
            .addFunction(createInstructionFunction(instruction, discriminatorProp))
        for (account in instruction.accounts) {
            if (account is Account.Pda) {
                builder.addFunction(createPdaFunction(account))
            }
        }
        return builder.build()
    }

    private fun createPdaFunction(account: Account.Pda): FunSpec {
        val builder = FunSpec
            .builder(account.pdaFunctionName())
            .jvmStatic()
            .returns(ProgramDerivedAddress::class)

        val seedArgs = account.seeds.map { seed ->
            when (seed) {
                is Seed.Const -> seed.bytes.toCodeBlock()
                is Seed.Arg -> {
                    val param = param(seed.ref.name, seed.ref.type.toTypeName())
                    builder.addParameter(param)
                    seed.ref.type.bytesCodeBlock(param.name)
                }
                is Seed.DefinedAccountField -> {
                    val param = param(seed.ref.name, seed.ref.type.toTypeName())
                    builder.addParameter(param)
                    seed.ref.type.bytesCodeBlock(param.name)
                }
                is Seed.NamedAccount -> {
                    val param = param(seed.ref.name, Type.Native.pubkey.toTypeName())
                    builder.addParameter(param)
                    Type.Native.pubkey.bytesCodeBlock(param.name)
                }
            }
        }

        return builder
            .addStatement(
                "return %M(listOf(%L), %L)",
                PublicKey::class.member("findProgramAddress"),
                seedArgs.joinToString(", "),
                programIdRef,
            )
            .build()
    }

    private fun Account.Pda.pdaFunctionName(): String = "${name.toLowerCamel()}Pda"

    private fun createInstructionFunction(
        instruction: OutputModel.Instruction,
        discriminatorProp: PropertySpec
    ): FunSpec {
        val funBuilder = FunSpec
            .builder("instruction")
            .jvmStatic()
            .returns(Instruction::class)
            .also { builder -> instruction.docLines.forEach { builder.addKdoc("$it\n") }  }

        val argParams = instruction.args.associateWith {
            val param = param(it.name, it.type.toTypeName(), it.docLines)
            funBuilder.addParameter(param)
            param
        }

        return funBuilder
            .instructionAccountMetas(instruction, argParams)
            .instructionArgsAndReturn(instruction, discriminatorProp, argParams)
            .build()
    }

    private fun FunSpec.Builder.instructionAccountMetas(
        instruction: OutputModel.Instruction,
        argParams: Map<Arg, ParameterSpec>
    ): FunSpec.Builder {
        addStatement("val accountMetas = listOf(⇥")

        val standardAccountParams = HashMap<Account.Named, ParameterSpec>()

        for (account in instruction.accounts) {
            addComment(account.name)
            when (account) {
                is Account.Pda -> {
                    val seedParams = account.seeds.mapNotNull { seed ->
                        when (seed) {
                            is Seed.Const -> null
                            is Seed.Arg -> argParams.getValue(seed.ref).name
                            is Seed.DefinedAccountField -> {
                                val param = param(seed.ref.name, seed.ref.type.toTypeName())
                                addParameter(param)
                                param.name
                            }
                            is Seed.NamedAccount -> standardAccountParams.getValue(seed.ref).name
                        }
                    }
                    addStatement(
                        "%M(%L(%L).publicKey(), %L, %L),",
                        AccountMeta::class.member("createMeta"),
                        account.pdaFunctionName(),
                        seedParams.joinToString(", "),
                        account.writable,
                        account.signer
                    )
                }
                is Account.FixedAddress -> {
                    when (account.address) {
                        SolanaAccounts.MAIN_NET.systemProgram() ->
                            addStatement(
                                "%M(%T.MAIN_NET.systemProgram(), %L, %L),",
                                AccountMeta::class.member("createMeta"),
                                className<SolanaAccounts>(),
                                account.writable,
                                account.signer
                            )
                        else ->
                            addStatement(
                                "%M(%M(%S), %L, %L),",
                                AccountMeta::class.member("createMeta"),
                                publicKeyfromBase58Encoded,
                                account.address.toBase58(),
                                account.writable,
                                account.signer
                            )
                    }
                }
                is Account.Named -> {
                    val param = param(account.name, className<PublicKey>())
                    standardAccountParams[account] = param
                    addParameter(param)
                    addStatement(
                        "%M(%N, %L, %L),",
                        AccountMeta::class.member("createMeta"),
                        param,
                        account.writable,
                        account.signer
                    )
                }
            }
        }

        addStatement("⇤)")
        return this
    }

    private fun FunSpec.Builder.instructionArgsAndReturn(
        instruction: OutputModel.Instruction,
        discriminatorProperty: PropertySpec,
        argParams: Map<Arg, ParameterSpec>
    ): FunSpec.Builder {
        val argSizeBlocks = ArrayList<CodeBlock>()
        argSizeBlocks += CodeBlock.of("%N.length()", discriminatorProperty)
        instruction.args.mapTo(argSizeBlocks) { it.type.readWriteSpec().sizeCodeBlock(argParams.getValue(it).name) }

        if (instruction.args.isEmpty()) {
            addStatement(
                "return %M(%M(%L), accountMetas, %N)",
                Instruction::class.member("createInstruction"),
                AccountMeta::class.member("createInvoked"),
                programIdRef,
                discriminatorProperty
            )
        } else {
            addStatement("val data = ByteArray(%L)", argSizeBlocks.joinToString(" + "))
                .addStatement("var $POSITION_VAR = %N.write(data, 0)", discriminatorProperty)
            for ((argIndex, arg) in instruction.args.withIndex()) {
                addElementWrite(
                    arg.type.readWriteSpec(),
                    "data",
                    argParams.getValue(arg).name,
                    argIndex < instruction.args.size - 1
                )
            }
            addStatement(
                "return %M(%M(%L), accountMetas, data)",
                Instruction::class.member("createInstruction"),
                AccountMeta::class.member("createInvoked"),
                programIdRef
            )
        }

        return this
    }

    private fun createDefinedTypeClass(definedType: Type.Defined, discriminator: Discriminator?): TypeSpec {
        val typeBuilder = TypeSpec.classBuilder(definedType.name)
        definedType.docLines.forEach(typeBuilder::addKdoc)

        val companionObjectBuilder = TypeSpec.companionObjectBuilder()

        val discriminatorProperty = discriminator?.let(::createProperty)
        if (discriminatorProperty != null) {
            companionObjectBuilder.addProperty(discriminatorProperty)
            companionObjectBuilder.addAccountFilters(definedType, discriminator, discriminatorProperty)
        }

        val fixedBytesNewStructSize = fixedBytesNewStructSize(definedType)
        if (fixedBytesNewStructSize != null) {
            typeBuilder.extendFixedBytesNewtypeStruct(
                definedType.toClassName(),
                fixedBytesNewStructSize,
                companionObjectBuilder
            )
        } else {
            typeBuilder
                .addModifiers(DATA)
                .primaryProperties {
                    for (field in definedType.fields) {
                        it.add(
                            field.outputName(),
                            field.toTypeName(),
                            docLines = (field as? Type.Defined.Field.Named)?.docLines ?: emptyList()
                        )
                    }
                }
                .implementBorsh(definedType, discriminator, discriminatorProperty, companionObjectBuilder)
        }
        return typeBuilder
            .addType(companionObjectBuilder.build())
            .build()
    }

    private fun TypeSpec.Builder.addAccountFilters(
        definedType: Type.Defined,
        discriminator: Discriminator,
        discriminatorProperty: PropertySpec
    ) {
        addProperty(PropertySpec
            .builder("DISCRIMINATOR_FILTER", Filter::class)
            .jvmField()
            .initializer("%T.createMemCompFilter(0, %N.data())", Filter::class, discriminatorProperty)
            .build())

        var currentOffset = discriminator.length()
        for (field in definedType.fields) {
            val (name, type) = field as? Type.Defined.Field.Named ?: break
            val offsetProperty = PropertySpec
                .builder("${name.toUpperUnderscore()}_OFFSET", Int::class, CONST)
                .initializer("%L", currentOffset)
                .build()
            addProperty(offsetProperty)
            val filterParam = param(name.toLowerCamel(), type.toTypeName())
            addFunction(
                FunSpec
                    .builder("${name.toLowerCamel()}Filter")
                    .jvmStatic()
                    .addParameter(filterParam)
                    .returns(Filter::class)
                    .addStatement(
                        "return %T.createMemCompFilter(%N, %L)",
                        Filter::class,
                        offsetProperty,
                        if (type == Type.Native.pubkey) filterParam.name else type.bytesCodeBlock(filterParam.name)
                    )
                    .build()
            )
            currentOffset += type.readWriteSpec().fixedSize ?: break
        }
    }

    private fun fixedBytesNewStructSize(definedType: Type.Defined): Int? {
        if (definedType.fields.size == 1) {
            val field = definedType.fields[0]
            if (field is Type.Defined.Field.FixedSizeArray && field.elementType == Type.Native.u8) {
                return field.size
            }
        }
        return null
    }

    private fun createProperty(discriminator: Discriminator): PropertySpec {
        return PropertySpec
            .builder("DISCRIMINATOR", Discriminator::class)
            .jvmField()
            .initializer(
                "%M(%L)",
                Discriminator::class.member("createDiscriminator"),
                discriminator.data().toCodeBlock()
            )
            .build()
    }

    private fun TypeSpec.Builder.implementBorsh(
        definedType: Type.Defined,
        discriminator: Discriminator?,
        discriminatorProperty: PropertySpec?,
        companionObjectBuilder: TypeSpec.Builder,
    ): TypeSpec.Builder {
        addSuperinterface(Borsh::class)

        val fixedSize = definedType.fixedSize()
        if (fixedSize != null) {
            fixedSize(fixedSize + (discriminator?.length() ?: 0), companionObjectBuilder)
        } else {
            val sizeBlocks = ArrayList<CodeBlock>()
            if (discriminatorProperty != null) {
                sizeBlocks += CodeBlock.of("%N.length()", discriminatorProperty)
            }
            definedType.fields.mapTo(sizeBlocks) { it.readWriteSpec().sizeCodeBlock(it.outputName()) }
            addFunction(FunSpec
                .builder("l")
                .addModifiers(OVERRIDE)
                .returns(Int::class)
                .addStatement("return %L", sizeBlocks.joinToString(" + "))
                .build()
            )
        }

        companionObjectBuilder.addFunction(createReadFunction(definedType, discriminator))
        addFunction(createWriteFunction(definedType, discriminatorProperty))
        return this
    }

    private fun createReadFunction(definedType: Type.Defined, discriminator: Discriminator?): FunSpec {
        val className = definedType.toClassName()

        val dataParam = ParameterSpec.builder("data", ByteArray::class).build()
        val offsetParam = ParameterSpec
            .builder("offset", Int::class)
            .defaultValue("%L", 0)
            .build()

        val readFunctionBuilder = FunSpec.builder("read")
            .jvmStatic()
            .jvmOverloads()
            .addParameter(dataParam)
            .addParameter(offsetParam)
            .returns(className)

        if (discriminator != null) {
            readFunctionBuilder.addStatement("var $POSITION_VAR = %N + %L", offsetParam, discriminator.length())
        } else {
            readFunctionBuilder.addStatement("var $POSITION_VAR = %N", offsetParam)
        }

        for ((fieldIndex, field) in definedType.fields.withIndex()) {
            val readWriteSpec = field.readWriteSpec()
            val fieldName = field.outputName()
            readFunctionBuilder.addStatement(
                "val %L = %L",
                fieldName,
                readWriteSpec.readCodeBlock(dataParam, POSITION_VAR)
            )
            if (fieldIndex < definedType.fields.size - 1) {
                readFunctionBuilder.addStatement("$POSITION_VAR += %L", readWriteSpec.sizeCodeBlock(fieldName))
            }
        }

        return readFunctionBuilder
            .addStatement("return %T(%L)", className, definedType.fields.joinToString(", ") { it.outputName() })
            .build()
    }

    private fun createWriteFunction(definedType: Type.Defined, discriminatorProperty: PropertySpec?): FunSpec {
        val dataParam = ParameterSpec.builder("data", ByteArray::class).build()
        val offsetParam = ParameterSpec.builder("offset", Int::class).build()

        val writeFunctionBuilder = FunSpec.builder("write")
            .addModifiers(OVERRIDE)
            .addParameter(dataParam)
            .addParameter(offsetParam)
            .returns(Int::class)
            .addStatement("var $POSITION_VAR = %N", offsetParam)

        if (discriminatorProperty != null) {
            writeFunctionBuilder.addStatement(
                "$POSITION_VAR += %N.write(%N, $POSITION_VAR)",
                discriminatorProperty,
                dataParam,
            )
        }

        for (field in definedType.fields) {
            writeFunctionBuilder.addElementWrite(field.readWriteSpec(), dataParam.name, field.outputName(), true)
        }

        return writeFunctionBuilder
            .addStatement("return $POSITION_VAR - %N", offsetParam)
            .build()
    }

    private fun FunSpec.Builder.addElementWrite(
        readWriteSpec: ReadWriteSpec,
        dataVar: String,
        elementVar: String,
        advancePos: Boolean
    ) {
        val (writeBlock, sizeBlock) = readWriteSpec.writeCodeBlock(dataVar, elementVar, POSITION_VAR)
        if (!advancePos) {
            addStatement("%L", writeBlock)
        } else if (sizeBlock == null) {
            addStatement("$POSITION_VAR += %L", writeBlock)
        } else {
            addStatement("%L", writeBlock)
            addStatement("$POSITION_VAR += %L", sizeBlock)
        }
    }

    private fun TypeSpec.Builder.extendFixedBytesNewtypeStruct(
        className: ClassName,
        size: Int,
        companionObjectBuilder: TypeSpec.Builder
    ) {
        val bytesProperty = fixedSize(size, companionObjectBuilder)
        val dataParam = ParameterSpec.builder("data", ByteArray::class).build()
        val offsetParam = ParameterSpec
            .builder("offset", Int::class)
            .defaultValue("%L", 0)
            .build()
        companionObjectBuilder.addFunction(FunSpec
            .builder("read")
            .jvmStatic()
            .jvmOverloads()
            .addParameter(dataParam)
            .addParameter(offsetParam)
            .returns(className)
            .addStatement("val bytes = ByteArray(%N)", bytesProperty)
            .addStatement("%M(bytes, %N, %N)", Borsh::class.member("readArray"), dataParam, offsetParam)
            .addStatement("return %T(bytes)", className)
            .build()
        )

        primaryConstructor(FunSpec.constructorBuilder().addParameter("bytes", ByteArray::class).build())
        superclass(FixedBytesNewtypeStruct::class)
        addSuperclassConstructorParameter("bytes")
    }

    private fun createErrorEnum(): TypeSpec {
        val errorEnumBuilder = TypeSpec.enumBuilder("Error").primaryProperties { it.add("code", className<Int>()) }
        val valueOfMethodBuilder = FunSpec
            .builder("valueOf")
            .jvmStatic()
            .addParameter("code", Int::class)
            .returns(programClassName.nestedClass("Error").copy(nullable = true))
            .beginControlFlow("return when (code)")
        for (error in model.errors) {
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

    fun TypeSpec.Builder.fixedSize(size: Int, companionBuilder: TypeSpec.Builder): PropertySpec {
        val property = PropertySpec.builder("BYTES", Int::class, CONST).initializer("%L", size).build()
        companionBuilder.addProperty(property)
        addFunction(FunSpec
            .builder("l")
            .addModifiers(OVERRIDE)
            .returns(Int::class)
            .addStatement("return %N", property)
            .build()
        )
        return property
    }

    private inline fun TypeSpec.Builder.primaryProperties(block: (PrimaryPropertyBuilder) -> Unit): TypeSpec.Builder {
        val ctorBuilder = FunSpec.constructorBuilder()
        block(PrimaryPropertyBuilder(this, ctorBuilder))
        primaryConstructor(ctorBuilder.build())
        return this
    }

    private class PrimaryPropertyBuilder(private val typeBuilder: TypeSpec.Builder, private val ctorBuilder: FunSpec.Builder) {
        fun add(
            name: String,
            type: TypeName,
            defaultValue: CodeBlock? = null,
            docLines: List<String> = emptyList(),
            vararg modifiers: KModifier
        ): PrimaryPropertyBuilder {
            ctorBuilder.addParameter(ParameterSpec
                .builder(name, type, modifiers.asList() - OVERRIDE)
                .defaultValue(defaultValue)
                .also { builder -> docLines.forEach { builder.addKdoc(it) } }
                .build()
            )
            typeBuilder.addProperty(PropertySpec
                .builder(name, type, modifiers.asList())
                .initializer(name)
                .also { builder -> docLines.forEach { builder.addKdoc(it) } }
                .build()
            )
            return this
        }
    }

    private fun Type.Defined.Field.toTypeName(): TypeName {
        return when (this) {
            is Type.Defined.Field.Named -> type.toTypeName()
            is Type.Defined.Field.Unnamed -> type.toTypeName()
            is Type.Defined.Field.FixedSizeArray -> throw UnsupportedOperationException(toString())
        }
    }

    private fun Type.Defined.Field.outputName(): String {
        return when (this) {
            is Type.Defined.Field.Named -> name.toLowerCamel()
            is Type.Defined.Field.Unnamed -> type.unamedField()
            is Type.Defined.Field.FixedSizeArray -> elementType.unamedField()
        }
    }

    private fun Type.unamedField(): String {
        return when (this) {
            is Type.Native -> name
            is Type.Defined -> name.toLowerCamel()
            is Type.Vec -> "vecOf${elementType.unamedField()}"
        }
    }

    private fun Type.toTypeName(): TypeName {
        return when (this) {
            Type.Native.u8 -> className<Byte>()
            Type.Native.i8 -> className<Byte>()
            Type.Native.u16 -> className<Short>()
            Type.Native.i16 -> className<Short>()
            Type.Native.u32 -> className<Int>()
            Type.Native.i32 -> className<Int>()
            Type.Native.u64 -> className<Long>()
            Type.Native.i64 -> className<Long>()
            Type.Native.u128 -> className<U128>()
            Type.Native.f32 -> className<Float>()
            Type.Native.f64 -> className<Double>()
            Type.Native.bool -> className<Boolean>()
            Type.Native.bytes -> className<ByteArray>()
            Type.Native.pubkey -> className<PublicKey>()
            is Type.Defined -> toClassName()
            is Type.Vec -> className<List<*>>().parameterizedBy(elementType.toTypeName())
        }
    }

    private fun Type.Defined.toClassName(): ClassName {
        val subPackage = if (this in model.types) TYPES_SUBPACKAGE else ACCOUNTS_SUBPACKAGE
        return ClassName("$outputPackage.${subPackage}", name)
    }

    private fun Type.readWriteSpec(): ReadWriteSpec {
        return when (this) {
            Type.Native.u8, Type.Native.i8 -> ReadWriteSpec.Byte
            Type.Native.u16, Type.Native.i16 -> ReadWriteSpec.Primitive(2, "getInt16LE", "putInt16LE")
            Type.Native.u32, Type.Native.i32 -> ReadWriteSpec.Primitive(4, "getInt32LE", "putInt32LE")
            Type.Native.u64, Type.Native.i64 -> ReadWriteSpec.Primitive(8, "getInt64LE", "putInt64LE")
            Type.Native.u128 -> ReadWriteSpec.Borsh(className<U128>(), U128.BYTES)
            Type.Native.f32 -> ReadWriteSpec.Primitive(4, "getFloat32LE", "putFloat32LE")
            Type.Native.f64 -> ReadWriteSpec.Primitive(8, "getFloat64LE", "putFloat64LE")
            Type.Native.bool -> TODO()
            Type.Native.bytes -> TODO()
            Type.Native.pubkey -> ReadWriteSpec.PublicKey
            is Type.Defined -> ReadWriteSpec.Borsh(toClassName(), fixedSize())
            is Type.Vec -> ReadWriteSpec.Vec(elementType.readWriteSpec())
        }
    }

    private fun Type.Defined.fixedSize(): Int? {
        var fixedSize = 0
        for (field in fields) {
            val fieldSize = when (field) {
                is Type.Defined.Field.Named -> field.type.readWriteSpec().fixedSize
                is Type.Defined.Field.Unnamed -> field.type.readWriteSpec().fixedSize
                is Type.Defined.Field.FixedSizeArray -> field.elementType.readWriteSpec().fixedSize?.let { it * field.size }
            }
            fixedSize += fieldSize ?: return null
        }
        return fixedSize
    }

    private fun Type.Defined.Field.readWriteSpec(): ReadWriteSpec {
        return when (this) {
            is Type.Defined.Field.Named -> type.readWriteSpec()
            is Type.Defined.Field.Unnamed -> type.readWriteSpec()
            is Type.Defined.Field.FixedSizeArray -> throw UnsupportedOperationException(toString())
        }
    }

    private fun Type.bytesCodeBlock(variable: String): CodeBlock {
        return when (this) {
            Type.Native.u8, Type.Native.i8 -> CodeBlock.of("byteArrayOf(%L)", variable)
            Type.Native.u128 -> CodeBlock.of("%N.write()", variable)
            Type.Native.bytes -> CodeBlock.of("%N.clone()", variable)
            Type.Native.pubkey -> CodeBlock.of("%N.copyByteArray()", variable)
            is Type.Native -> CodeBlock.of("%M(%N)", BorshUtils::class.member("toBytes"), variable)
            is Type.Defined -> CodeBlock.of("%N.write()", variable)
            is Type.Vec -> TODO()
        }
    }

    private fun param(name: String, type: TypeName, docLines: List<String> = emptyList()): ParameterSpec {
        return ParameterSpec
            .builder(name.toLowerCamel(), type)
            .also { builder -> docLines.forEach { builder.addKdoc(it) } }
            .build()
    }

    companion object {
        const val TYPES_SUBPACKAGE = "types"
        const val ACCOUNTS_SUBPACKAGE = "accounts"
        const val POSITION_VAR = "pos"

        val publicKeyfromBase58Encoded = PublicKey::class.member("fromBase58Encoded")
    }
}
