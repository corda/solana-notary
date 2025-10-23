import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9

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

dependencies {
    implementation(libs.solana4j.core)
    implementation(libs.solana4j.rpc)
    implementation(libs.bouncycastle)
    implementation(libs.jackson.kotlin)
}

tasks.compileKotlin {
    compilerOptions {
        apiVersion = KOTLIN_1_9  // For compatibility with Corda
    }
}
