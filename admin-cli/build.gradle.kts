plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.detekt)
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
    implementation(project(":clients:kotlin"))
    implementation(libs.corda.tools.cliutils)
    implementation(libs.picocli)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.kotlin)
    implementation(libs.bouncycastle)

    runtimeOnly(libs.logback)

    detektPlugins(libs.detekt.ktlint.wrapper)
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}
