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
    create("codegen").java.srcDir("src/codegen/kotlin")
    main.get().java.srcDir(generatedKotlinDir)
}

dependencies {
    "codegenImplementation"(project(":common"))
    "codegenImplementation"("org.jetbrains.kotlin:kotlin-stdlib")
    "codegenImplementation"(libs.kotlinpoet)
    "codegenImplementation"(libs.jackson.kotlin)
    "codegenImplementation"(libs.guava)
    "codegenImplementation"(libs.solana4j.core)

    implementation(project(":common"))
    implementation(libs.solana4j.core)
}

tasks.register<JavaExec>("generateKotlinClient") {
    val generateIdlTask = project(":solana-program").tasks.named("generateIdl")
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
