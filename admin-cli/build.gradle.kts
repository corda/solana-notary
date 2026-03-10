import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("default-kotlin")
    `kotlin-kapt`
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass = "net.corda.solana.notary.admincli.SolanaNotaryAdmin"
}

dependencies {
    kapt(libs.picocli.codegen)

    implementation(project(":kotlin-client"))
    implementation(libs.picocli)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.slf4j.simple)
    runtimeOnly(libs.slf4j.jdk)

    testImplementation(project(":testing"))
    testImplementation(libs.corda.solana.testing)
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveFileName = "solana-notary-admin.jar"
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
