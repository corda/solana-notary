import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.deleteExisting
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

plugins {
    id("default-kotlin")
    `kotlin-kapt`
    application
    alias(libs.plugins.graalvm)
    alias(libs.plugins.shadow)
}

application {
    mainClass = "net.corda.solana.notary.admincli.SolanaNotaryAdmin"
}

dependencies {
    kapt(libs.picocli.codegen)

    implementation(project(":kotlin-client"))
    implementation(libs.picocli)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(project(":testing"))
    testImplementation(libs.corda.solana.testing)
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

graalvmNative {
    testSupport = false
    binaries {
        named("main") {
            javaLauncher = javaToolchains
                .launcherFor {
                    languageVersion = java.toolchain.languageVersion
                    vendor = JvmVendorSpec.GRAAL_VM
                }
                .map(::fixNativeImageSymLink)
            imageName = "solana-notary-admin"
            quickBuild = project.hasProperty("quickBuild")
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion
    }
}

tasks.shadowJar {
    archiveFileName = "solana-notary-admin.jar"
}

// TODO CI tests the shadowJar as the build environment is missing required packages for native-image
tasks.test {
    val isNativeImage = project.hasProperty("nativeImage")
    dependsOn(if (isNativeImage) tasks.nativeCompile else tasks.shadowJar)
    systemProperty("gradle.test.version", version)
    doFirst {
        val binary = if (isNativeImage) tasks.nativeCompile.get().outputFile else tasks.shadowJar.get().archiveFile
        systemProperty("gradle.test.bin", binary.get())
    }
}

// https://github.com/gradle/gradle/issues/28583
private fun fixNativeImageSymLink(javaLauncher: JavaLauncher): JavaLauncher {
    val binPath = javaLauncher.executablePath.asFile.toPath().parent
    val svmBinPath = binPath.resolve("../lib/svm/bin")
    fixSymlink(binPath.resolve("native-image"), svmBinPath.resolve("native-image"))
    return javaLauncher
}

private fun fixSymlink(target: Path, source: Path) {
    if (!source.isRegularFile(NOFOLLOW_LINKS)) {
        logger.info("fixSymlink: expected is not regular, skip (expected: {})", source)
        return
    }
    if (!target.isRegularFile(NOFOLLOW_LINKS) || target.fileSize() > 0) {
        logger.info("fixSymlink: target is not regular or the file size > 0, skip (target: {})", target)
        return
    }
    logger.info("fixSymlink: {} -> {}", target, source)
    target.deleteExisting()
    target.createLinkPointingTo(source)
}
