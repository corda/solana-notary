rootProject.name = "solana-notary"

include(
    "admin-cli",
    "common",
    "kotlin-client",
    "solana-program",
    "testing"
)

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { r3Artifactory("corda-lib") }
        maven { r3Artifactory("corda-dependencies") }
        maven { url = uri("https://download.corda.net/maven/corda-dependencies") }
        // TODO Confirm if this public repo actually exists and if not move corda-solana lib to somewhere else like
        //  corda-dependencies
        maven { url = uri("https://download.corda.net/maven/corda-lib") }
    }
}

fun MavenArtifactRepository.r3Artifactory(repo: String) {
    url = uri("https://software.r3.com/artifactory/$repo")
    credentials {
        username = System.getenv("CORDA_ARTIFACTORY_USERNAME")
        password = System.getenv("CORDA_ARTIFACTORY_PASSWORD")
    }
}
