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
    implementation(libs.solana4j.core)
    implementation(libs.solana4j.rpc)
    implementation(libs.bouncycastle)
    implementation(libs.jackson.kotlin)
}
