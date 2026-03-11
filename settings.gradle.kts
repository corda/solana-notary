rootProject.name = "solana-notary"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "admin-cli",
    "kotlin-client",
    "program",
    "testing"
)

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { r3Artifactory("corda-dependencies") }
        maven { url = uri("https://download.corda.net/maven/corda-dependencies") }
    }
}

fun MavenArtifactRepository.r3Artifactory(repo: String) {
    url = uri("https://software.r3.com/artifactory/$repo")
    credentials {
        username = System.getenv("CORDA_ARTIFACTORY_USERNAME")
        password = System.getenv("CORDA_ARTIFACTORY_PASSWORD")
    }
}
