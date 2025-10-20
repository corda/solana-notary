plugins {
    id("default-kotlin")
    application
    alias(libs.plugins.shadow)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "net.corda.solana.notary.admincli.SolanaAggregatorCliKt"
}

dependencies {
    implementation(project(":kotlin-client"))
    implementation(libs.corda.tools.cliutils)
    implementation(libs.picocli)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.kotlin)
    implementation(libs.bouncycastle)

    runtimeOnly(libs.logback)
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}
