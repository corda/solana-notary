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
    api(libs.sava.programs)

    implementation(libs.sava.core)
    implementation(libs.sava.rpc)
    implementation(libs.bucket4j)
    implementation(libs.slf4j.api)
}
