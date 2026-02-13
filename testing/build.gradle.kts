plugins {
    id("corda-kotlin")
    `java-library`
    id("r3-artifactory")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":kotlin-client"))
    implementation(libs.corda.solana.testing)
    implementation(libs.junit.api)
    implementation(libs.slf4j.api)

    runtimeOnly(project(":solana-program"))
}
