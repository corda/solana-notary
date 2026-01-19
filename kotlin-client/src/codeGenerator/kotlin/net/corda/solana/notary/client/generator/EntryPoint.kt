package net.corda.solana.notary.client.generator

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import net.corda.solana.notary.client.generator.SavaGenerator.Companion.ACCOUNTS_SUBPACKAGE
import net.corda.solana.notary.client.generator.SavaGenerator.Companion.TYPES_SUBPACKAGE
import java.nio.file.Path
import javax.annotation.processing.Generated
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    val idlFile = Path(args[0])
    val outputDir = Path(args[1]).createDirectories()
    val basePackage = args[2]

    val anchorIdl = jacksonObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .readValue<AnchorIdl>(idlFile.toFile())
    val model = AnchorIdlParser(anchorIdl).parse()
    val savaGenerator = SavaGenerator(model, basePackage)

    savaGenerator.createProgramObject().writeTo(basePackage, outputDir)

    for (instructionObject in savaGenerator.createInstructionObjects()) {
        instructionObject.writeTo("$basePackage.instructions", outputDir)
    }

    for (typeClass in savaGenerator.createTypeClasses()) {
        typeClass.writeTo("$basePackage.${TYPES_SUBPACKAGE}", outputDir)
    }

    for (accountClass in savaGenerator.createAccountClasses()) {
        accountClass.writeTo("$basePackage.${ACCOUNTS_SUBPACKAGE}", outputDir)
    }
}

private fun TypeSpec.writeTo(packageName: String, outputDir: Path) {
    val file = FileSpec
        .builder(packageName, name!!)
        .indent("    ")
        .addFileComment("THIS IS GENERATED CODE, DO NOT MODIFY!")
        .addAnnotation(Generated::class)
        .addAnnotation(AnnotationSpec
            .builder(Suppress::class)
            .addMember("%S", "unused")
            .addMember("%S", "RedundantVisibilityModifier")
            .build()
        )
        .addType(this)
        .build()
    file.writeTo(outputDir)
}
