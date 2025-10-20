rootProject.name = "solana-notary"

include(
    "admin-cli",
    "common",
    "kotlin-client",
    "notary-program"
)

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://download.corda.net/maven/corda-dependencies") }
    }
}
