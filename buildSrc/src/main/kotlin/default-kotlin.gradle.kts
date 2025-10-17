plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.adarshr.test-logger")
    id("dev.detekt")
}

dependencies {
    val libs = versionCatalogs.named("libs")

    testImplementation(libs.findLibrary("junit.jupiter").get())
    testImplementation(libs.findLibrary("assertj.core").get())

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:${libs.findVersion("detekt").get()}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}
