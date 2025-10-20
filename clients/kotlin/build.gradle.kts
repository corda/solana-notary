plugins {
    id("default-kotlin")
    id("r3-artifactory")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

private val generatedKotlinDir = layout.buildDirectory.dir("generated/src/main/kotlin")

sourceSets {
    create("codegen").java.srcDir("src/codegen/kotlin")
    main.get().java.srcDir(generatedKotlinDir)
}

dependencies {
    "codegenImplementation"(project(":common"))
    "codegenImplementation"("org.jetbrains.kotlin:kotlin-stdlib")
    "codegenImplementation"(libs.kotlinpoet)
    "codegenImplementation"(libs.jackson.kotlin)
    "codegenImplementation"(libs.guava)

    api(project(":common"))
}

tasks.register<JavaExec>("generateKotlinClient") {
    val generateIdlTask = project(":notary-program").tasks.named("generateIdl")
    dependsOn("compileCodegenJava", generateIdlTask)
    outputs.dir(generatedKotlinDir)
    classpath(layout.buildDirectory.dir("classes/kotlin/codegen"), configurations["codegenRuntimeClasspath"])
    mainClass = "net.corda.solana.notary.kotlincodegen.KotlinClientCodeGeneratorKt"
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

tasks.jar {
    archiveBaseName = "corda-solana-notary-client"
}
