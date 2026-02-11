plugins {
    id("corda-java")
    `java-library`
    id("r3-artifactory")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":kotlin-client"))
    implementation(libs.corda.solana.core)
    implementation(libs.corda.solana.testing)

    runtimeOnly(project(":solana-program"))
}
