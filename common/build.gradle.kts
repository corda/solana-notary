plugins {
    alias(libs.plugins.kotlin.jvm)
//    id 'corda.common-publishing'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(libs.solana4j.core)
    api(libs.solana4j.rpc)

    implementation(libs.bouncycastle)
    implementation(libs.jackson.kotlin)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName = "corda-solana-notary-common"
}

//publishing {
//    publications {
//        maven(MavenPublication) {
//            artifactId jar.baseName
//            from components.java
//        }
//    }
//}
