plugins {
    alias(libs.plugins.axion.release)
    alias(libs.plugins.versions)
    java
}

scmVersion {
    tag {
        prefix = "v"
    }
}

// It is discouraged to configure modules via the root build file, but this how the axion-release plugin advises module
// versions be set.
allprojects {
    project.version = rootProject.scmVersion.version
}

val cargoClean = tasks.register<Exec>("cargoClean") {
    commandLine("cargo", "clean")
}

tasks.clean {
    dependsOn(cargoClean)
}
