import java.io.OutputStream

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
        "git",
        "clone",
        "-c",
        "advice.detachedHead=false",
        "--depth=1",
        "--branch=24.1.0",
        "https://github.com/sava-software/anchor-src-gen.git",
        dir.asFile
    )
    errorOutput = OutputStream.nullOutputStream()
}

tasks.register<Exec>("runAnchorSrcGen") {
    outputs.dir(generatedJavaDir)
    val generateIdlTask = project(":solana-program").tasks.named("generateIdl")
    dependsOn(downloadAnchorSrcGenTask, generateIdlTask)
    val java24Dir = javaToolchains
        .launcherFor { languageVersion = JavaLanguageVersion.of(24) }
        .map { it.metadata.installationPath }
    val programConfigFile = temporaryDir.resolve("notary-program.json")
    doFirst {
        workingDir(downloadAnchorSrcGenTask.get().outputs.files.singleFile)
        environment("JAVA_HOME", java24Dir.get())
        programConfigFile.writeText(
            """
            [
              {
                "name": "Corda Notary",
                "package": "notary.client",
                "program": "notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY",
                "idlFile": "${generateIdlTask.get().outputs.files.singleFile}"
              }
            ]
    """.trimIndent()
        )
    }
    commandLine(
        "sh",
        "genSrc.sh",
        "--basePackageName=net.corda.solana",
        "--programs=$programConfigFile",
        "--sourceDirectory=${generatedJavaDir.get()}"
    )
}

tasks.compileJava {
    dependsOn("runAnchorSrcGen")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("runAnchorSrcGen")
}
