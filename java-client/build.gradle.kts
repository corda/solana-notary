import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("corda-java")
    `java-library`
    id("r3-artifactory")
}

java {
    withSourcesJar()
}

private val generatedJavaDir = layout.buildDirectory.dir("generated/src/main/java")

sourceSets {
    main {
        java.srcDir(generatedJavaDir)
    }
}

dependencies {
    implementation(libs.sava.core)
    implementation(libs.sava.rpc)
}

val downloadAnchorSrcGenTask = tasks.register<Exec>("downloadAnchorSrcGen") {
    val dir = layout.buildDirectory.dir("anchor-src-gen").get()
    outputs.dir(dir)
    doFirst {
        delete(dir)
    }
    commandLine(
        "bash",
        "-c",
        """
        git clone https://github.com/sava-software/anchor-src-gen.git ${dir.asFile} && \
        git -C ${dir.asFile} -c advice.detachedHead=false checkout 979d71a8c6ad4c08a6ba5fdca78742a7364a6174
        """.trimIndent()
    )
}

tasks.register<Exec>("runAnchorSrcGen") {
    outputs.dir(generatedJavaDir)
    val generateIdlTask = project(":solana-program").tasks.named("generateIdl")
    dependsOn(downloadAnchorSrcGenTask, generateIdlTask)
    // anchor-src-gen doesn't work on Java 17 and so we use Gradle's toolchains feature to auto-download a Java 25 JVM
    // and run it against that. If the generated code is not Java 17 compatible then that will be caught during
    // compilation.
    val java25Dir = javaToolchains
        .launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        .map { it.metadata.installationPath }
    val programConfigFile = temporaryDir.resolve("notary-program.json")
    doFirst {
        workingDir(downloadAnchorSrcGenTask.get().outputs.files.singleFile)
        environment("JAVA_HOME", java25Dir.get())
        val idlFile = generateIdlTask.get().outputs.files.singleFile
        val idl = JsonSlurper().parse(idlFile) as Map<*, *>
        programConfigFile.writeText(JsonOutput.toJson(
            listOf(
                mapOf(
                    "name" to "Corda Notary",
                    "package" to "notary.client",
                    "program" to idl["address"]!!,
                    "idlFile" to idlFile.absolutePath,
                )
            )
        ))
    }
    commandLine(
        "sh",
        "genSrc.sh",
        "--basePackageName=net.corda.solana",
        "--programs=$programConfigFile",
        "--sourceDirectory=${generatedJavaDir.get()}",
        "--tabLength=4"
    )
    doLast {
        check(delete(generatedJavaDir.get().file("net/corda/solana/notary/client/anchor/idl.json")))
        // We have CordaNotaryAccounts which is better
        check(delete(generatedJavaDir.get().file("net/corda/solana/notary/client/anchor/CordaNotaryPDAs.java")))
    }
}

tasks.compileJava {
    dependsOn("runAnchorSrcGen")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("runAnchorSrcGen")
}
