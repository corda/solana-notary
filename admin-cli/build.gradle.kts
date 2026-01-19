plugins {
    id("default-kotlin")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "net.corda.solana.notary.admincli.SolanaNotaryAdminCliKt"
}

dependencies {
    implementation(project(":kotlin-client"))
    implementation(project(":common"))
    implementation(libs.picocli)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.kotlin)
    implementation(libs.sava.core)
    implementation(libs.sava.rpc)

    runtimeOnly(libs.logback)

    testImplementation(project(":notary-test"))
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
