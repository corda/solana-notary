plugins {
    java
    id("com.adarshr.test-logger")
}

java {
    toolchain {
        // TODO This should be 25
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    val libs = versionCatalogs.named("libs")

    testImplementation(libs.findLibrary("junit.jupiter").get())
    testImplementation(libs.findLibrary("assertj.core").get())

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
