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
    implementation(project(":kotlin-client"))
    implementation(libs.corda.solana.testing)
    implementation(libs.junit.api)
    implementation(libs.slf4j.api)

    runtimeOnly(project(":program"))
}

publishing {
    publications {
        getByName<MavenPublication>("mainPublication") {
            pom {
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
}
