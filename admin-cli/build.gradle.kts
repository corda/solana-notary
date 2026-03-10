import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
    implementation(libs.picocli)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(project(":testing"))
    testImplementation(libs.corda.solana.testing)
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("") // remove "-all"
    archiveVersion.set("")
}

tasks.build {
    dependsOn(shadowJarTask)
}

tasks.test {
    dependsOn(shadowJarTask)
    systemProperty("gradle.test.version", version)
    doFirst {
        systemProperty("gradle.test.shadowjar", shadowJarTask.get().archiveFile.get())
    }
}
