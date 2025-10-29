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
    implementation(project(":common"))
    implementation(project(":kotlin-client"))
    implementation(libs.solana4j.core)
    implementation(libs.solana4j.rpc)

    runtimeOnly(project(":solana-program"))
}
