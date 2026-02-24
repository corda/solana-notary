import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention

plugins {
    `maven-publish`
    id("com.jfrog.artifactory")
}

configure<ArtifactoryPluginConvention> {
    publish {
        contextUrl = "https://software.r3.com/artifactory"
        repository {
            repoKey = if (version.toString().endsWith("-SNAPSHOT")) "corda-dependencies-dev" else "corda-dependencies"
            username = System.getenv("CORDA_ARTIFACTORY_USERNAME")
            password = System.getenv("CORDA_ARTIFACTORY_PASSWORD")
        }
        defaults {
            publications("ALL_PUBLICATIONS")
        }
    }
}

extensions.configure<PublishingExtension>("publishing") {
    publications {
        create<MavenPublication>("mainPublication") {
            from(components["java"])
            artifactId = "solana-notary-${project.name}"
        }
    }
}
