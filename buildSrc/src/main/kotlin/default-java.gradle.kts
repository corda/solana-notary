import java.nio.file.Files

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

    testImplementation(libs.findLibrary("junit.core").get())
    testImplementation(libs.findLibrary("assertj.core").get())

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.findLibrary("slf4j.simple").get())
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    doFirst {
        val tempDir = layout.buildDirectory.dir("junit-temp").get().asFile.toPath()
        Files.createDirectories(tempDir)
        systemProperty("java.io.tmpdir", tempDir)
        systemProperty("junit.jupiter.execution.timeout.default", "2m")
    }
}
