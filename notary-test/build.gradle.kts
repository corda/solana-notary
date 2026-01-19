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
    implementation(libs.sava.core)
    implementation(libs.sava.rpc)
    implementation(libs.slf4j.api)

    runtimeOnly(project(":solana-program"))
}
