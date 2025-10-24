import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("default-kotlin")
}

kotlin {
    val kotlinVersion = versionCatalogs.named("libs").findVersion("kotlin.corda").get().toString()
    coreLibrariesVersion = kotlinVersion
    compilerOptions {
        val kotlinMinorVersion = KotlinVersion.fromVersion(kotlinVersion.split(".").take(2).joinToString("."))
        languageVersion = kotlinMinorVersion
        apiVersion = kotlinMinorVersion
        // Make sure Java 17 bytecode is produced, even if the java.toolchain uses a newer JDK
        jvmTarget = JvmTarget.JVM_17
        // Make sure only JDK 17 APIs are used.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
