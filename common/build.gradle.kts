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
    api(libs.solana4j.core)
    api(libs.solana4j.rpc)

    implementation(libs.bouncycastle)
    implementation(libs.jackson.kotlin)
}

tasks.jar {
    archiveBaseName = "corda-solana-notary-common"
}
