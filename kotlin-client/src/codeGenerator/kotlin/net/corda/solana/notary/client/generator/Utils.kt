package net.corda.solana.notary.client.generator

import com.google.common.base.CaseFormat
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName

inline fun <reified T> className(): ClassName = T::class.asClassName()

fun String.toUpperCamel(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this)
fun String.toLowerCamel(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this)
fun String.toUpperUnderscore(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, this)

fun ByteArray.toCodeBlock() = CodeBlock.of("byteArrayOf(%L)", joinToString(", "))
