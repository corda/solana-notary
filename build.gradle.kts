plugins {
    id("pl.allegro.tech.build.axion-release") version "1.21.0"
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
