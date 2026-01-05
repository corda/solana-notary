rootProject.name = "solana-notary"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "admin-cli",
    "common",
    "java-client",
    "kotlin-client",
    "solana-program",
    "notary-test"
)

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://download.corda.net/maven/corda-dependencies") }
    }
}
