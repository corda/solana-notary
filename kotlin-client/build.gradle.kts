plugins {
    id("corda-kotlin")
    `java-library`
    id("r3-artifactory")
}

java {
    withSourcesJar()
    withJavadocJar()
}

private val generatedKotlinDir = layout.buildDirectory.dir("generated/src/main/kotlin")

sourceSets {
    create("codeGenerator")
    main {
        java.srcDir(generatedKotlinDir)
    }
}

val codeGeneratorImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    codeGeneratorImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    codeGeneratorImplementation(libs.kotlinpoet)
    codeGeneratorImplementation(libs.jackson.kotlin)
    codeGeneratorImplementation(libs.guava)

    api(libs.corda.solana.core)
}

tasks.register<JavaExec>("generateKotlinClient") {
    val generateIdlTask = project(":solana-program").tasks.named("generateIdl")
    dependsOn(generateIdlTask)
    outputs.dir(generatedKotlinDir)
    classpath = sourceSets["codeGenerator"].runtimeClasspath
    mainClass = "net.corda.solana.notary.client.generator.EntryPointKt"
    doFirst {
        args(
            generateIdlTask.get().outputs.files.singleFile,
            generatedKotlinDir.get(),
            "net.corda.solana.notary.client"
        )
    }
}

tasks.compileKotlin {
    dependsOn("generateKotlinClient")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("generateKotlinClient")
}
