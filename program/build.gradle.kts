plugins {
    id("default-java")
    id("r3-artifactory")
}

val anchorClean = tasks.register<Exec>("anchorClean") {
    commandLine("anchor", "clean")
}

tasks.clean {
    dependsOn(anchorClean)
}

val anchorBuild = tasks.register<Exec>("anchorBuild") {
    inputs.files("../Cargo.toml", "../Cargo.lock", "programs/corda-notary/Cargo.toml")
    inputs.dir("programs/corda-notary/src")
    outputs.file("../target/deploy/corda_notary.so")
    commandLine("anchor", "build", "--no-idl")
    environment("GRADLE_VERSION", version.toString())
}

val anchorIdl = tasks.register<Exec>("anchorIdl") {
    dependsOn(anchorBuild)
    val idlFile = layout.projectDirectory.file("target/idl/corda_notary.json")
    outputs.file(idlFile)
    commandLine("anchor", "idl", "build", "-o", idlFile)
    environment("GRADLE_VERSION", version.toString())
}

tasks.processResources {
    from(anchorBuild) {
        into("net/corda/solana/notary/program")
    }
    from(anchorIdl) {
        into("net/corda/solana/notary/program")
    }
}

val compileCargoTest = tasks.register<Exec>("compileCargoTest") {
    dependsOn(anchorBuild)
    commandLine("cargo", "test", "--no-run")
}

tasks.testClasses {
    dependsOn(compileCargoTest)
}

val cargoTest = tasks.register<Exec>("cargoTest") {
    dependsOn(compileCargoTest)
    commandLine("cargo", "test")
}

tasks.test {
    dependsOn(cargoTest)
}

val rustfmtCheck = tasks.register<Exec>("rustfmtCheck") {
    commandLine("cargo", "fmt", "--all", "--check")
}

tasks.check {
    dependsOn(rustfmtCheck)
}

publishing {
    publications {
        getByName<MavenPublication>("mainPublication") {
            pom {
                licenses {
                    license {
                        name = "Business Source License 1.1"
                        url = "https://github.com/corda/solana-notary/blob/main/program/LICENSE"
                    }
                }
            }
        }
    }
}
