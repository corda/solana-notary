plugins {
    id("default-kotlin")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "net.corda.solana.notary.admincli.SolanaAggregatorCliKt"
}

dependencies {
    implementation(project(":kotlin-client"))
    implementation(project(":common"))
    implementation(libs.corda.tools.cliutils)
    implementation(libs.picocli)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.kotlin)
    implementation(libs.bouncycastle)
    implementation(libs.solana4j.core)
    implementation(libs.solana4j.rpc)

    runtimeOnly(libs.logback)
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}

tasks.test {
    val shadowJarTask = tasks.named<Jar>("shadowJar")
    dependsOn(shadowJarTask)
    systemProperty("gradle.test.version", version)
    doFirst {
        systemProperty("gradle.test.shadowjar", shadowJarTask.get().archiveFile.get())
    }
}
