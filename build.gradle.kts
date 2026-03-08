plugins {
    id("pl.allegro.tech.build.axion-release") version "1.21.0"
    id("com.github.ben-manes.versions") version "0.53.0"
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
