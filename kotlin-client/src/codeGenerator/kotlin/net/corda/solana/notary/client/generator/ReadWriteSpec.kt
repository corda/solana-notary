package net.corda.solana.notary.client.generator

import com.r3.corda.lib.solana.core.internal.BorshUtils
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterSpec
import software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH
import software.sava.core.encoding.ByteUtil

sealed interface ReadWriteSpec {
    val fixedSize: Int?
    fun sizeCodeBlock(variable: String): CodeBlock
    fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock
    fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock

    abstract class FixedSize(override val fixedSize: Int) : ReadWriteSpec {
        final override fun sizeCodeBlock(variable: String): CodeBlock = CodeBlock.of("%L", fixedSize)
    }

    class Primitive(fixedSize: Int, readMethodName: String, writeMethodName: String) : FixedSize(fixedSize) {
        val readMethod = ByteUtil::class.member(readMethodName)
        val writeMethod = ByteUtil::class.member(writeMethodName)

        override fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock {
            return CodeBlock.of("%M(%N, $offsetVar)", readMethod, dataParam)
        }
        override fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock {
            return WriteCodeBlock(
                CodeBlock.of("%M($dataVar, $offsetVar, %L)", writeMethod, elementVar),
                CodeBlock.of("%L", fixedSize)
            )
        }
    }

    object Byte : FixedSize(1) {
        override fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock {
            return CodeBlock.of("%N[$offsetVar]", dataParam)
        }

        override fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock {
            return WriteCodeBlock(CodeBlock.of("$dataVar[$offsetVar] = $elementVar"), CodeBlock.of("%L", 1))
        }
    }

    object PublicKey : FixedSize(PUBLIC_KEY_LENGTH) {
        override fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock {
            return CodeBlock.of(
                "%M(%N, $offsetVar)",
                software.sava.core.accounts.PublicKey::class.member("readPubKey"),
                dataParam
            )
        }

        override fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock {
            return WriteCodeBlock(CodeBlock.of("$elementVar.write($dataVar, $offsetVar)"))
        }
    }

    class Borsh(val className: ClassName, override val fixedSize: Int? = 0) : ReadWriteSpec {
        override fun sizeCodeBlock(variable: String): CodeBlock {
            return if (fixedSize != null) CodeBlock.of("%L", fixedSize) else CodeBlock.of("%L.l()", variable)
        }

        override fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock {
            return CodeBlock.of("%M(%N, $offsetVar)", className.member("read"), dataParam)
        }

        override fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock {
            return WriteCodeBlock(CodeBlock.of("$elementVar.write($dataVar, $offsetVar)"))
        }
    }

    class Vec(val elementSpec: ReadWriteSpec) : ReadWriteSpec {
        override val fixedSize: Int?
            get() = null

        override fun sizeCodeBlock(variable: String): CodeBlock {
            return if (elementSpec.fixedSize != null) {
                CodeBlock.of("4 + %L.size * %L", variable, elementSpec.fixedSize)
            } else {
                CodeBlock.of("4 + %L.sumOf { %L }", variable, elementSpec.sizeCodeBlock("it"))
            }
        }

        override fun readCodeBlock(dataParam: ParameterSpec, offsetVar: String): CodeBlock {
            return CodeBlock.of(
                "%M({ %L }, { %L }, %N, $offsetVar)",
                BorshUtils::class.member("readVector"),
                elementSpec.readCodeBlock(dataParam, "it"),
                elementSpec.sizeCodeBlock("it"),
                dataParam
            )
        }

        override fun writeCodeBlock(dataVar: String, elementVar: String, offsetVar: String): WriteCodeBlock {
            val (elementWriteBlock, elementSizeBlock) = elementSpec.writeCodeBlock(dataVar, "elem", "i")
            val vectorWriteBlock = if (elementSizeBlock == null) {
                CodeBlock.of(
                    "%M(%N, { elem, i -> %L }, $dataVar, $offsetVar)",
                    BorshUtils::class.member("writeVector"),
                    elementVar,
                    elementWriteBlock
                )
            } else {
                CodeBlock.of(
                    "%M(%N, { elem, i -> %L; %L }, $dataVar, $offsetVar)",
                    BorshUtils::class.member("writeVector"),
                    elementVar,
                    elementWriteBlock,
                    elementSizeBlock
                )
            }
            return WriteCodeBlock(vectorWriteBlock)
        }
    }

    data class WriteCodeBlock(val write: CodeBlock, val size: CodeBlock? = null)
}
